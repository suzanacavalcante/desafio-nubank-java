package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.contato.ContatoMapper;
import io.github.suzanacavalcante.desafio.contato.ContatoRepository;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ContatoRepository contatoRepository;

    /**
     * Cria o cliente e, se vierem, seus contatos, tudo na mesma transação: se qualquer contato
     * falhar ao ser gravado, nada é persistido.
     * <p>
     * A conversão para {@link ClienteResponse} acontece <b>dentro</b> da transação: com
     * {@code open-in-view: false}, mapear depois que o método retornou lançaria
     * {@code LazyInitializationException}.
     */
    @Transactional
    public ClienteResponse criar(ClienteRequest request) {
        Cliente cliente = clienteRepository.save(ClienteMapper.toEntity(request));
        return ClienteMapper.toResponse(cliente);
    }

    /**
     * Lista todos os clientes com seus contatos, em uma única consulta principal
     * ({@link ClienteRepository#findAllComContatos()}, com {@code JOIN FETCH}). Clientes sem contatos
     * vêm com lista vazia; clientes e contatos saem ordenados por id.
     * <p>
     * A conversão para {@link ClienteResponse} acontece <b>dentro</b> da transação; a coleção já vem
     * carregada, então o mapeamento não dispara consultas extras.
     * <p>
     * O que {@code @Transactional(readOnly = true)} dá aqui é só o modo somente leitura (sem dirty
     * checking nem flush). Não é ela que protege contra {@code LazyInitializationException}: o
     * {@code JOIN FETCH} já traz a coleção. Se a consulta for trocada por uma sem {@code JOIN FETCH},
     * a transação passa a ser necessária de verdade.
     * <p>
     * Sem paginação: a resposta traz a base inteira e cresce linearmente (medição de QA, nov/2026:
     * ~8 MB para 20 mil clientes com 5 contatos cada). Paginar é pré-requisito para uso com volume real
     * (issue de paginação do backlog); o motivo de não paginar hoje está no Javadoc de
     * {@link ClienteRepository#findAllComContatos()}.
     */
    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        return clienteRepository.findAllComContatos().stream()
                .map(ClienteMapper::toResponse)
                .toList();
    }

    /**
     * Lista os contatos de um cliente, ordenados por id.
     * <p>
     * Checa a existência com {@link ClienteRepository#existsById} antes de buscar:
     * {@link ContatoRepository#findByClienteIdOrderById} devolve lista vazia tanto para cliente sem
     * contatos quanto para cliente inexistente, e a API precisa distinguir os dois casos (200 com
     * {@code []} versus 404).
     * <p>
     * O mapeamento para {@link ContatoResponse} acontece dentro da transação; o cliente de cada contato
     * é só uma referência (o mapper lê apenas o id), então não há consultas extras por contato.
     *
     * @throws ClienteNaoEncontradoException se não existir cliente com o {@code id} informado
     */
    @Transactional(readOnly = true)
    public List<ContatoResponse> contatosDoCliente(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new ClienteNaoEncontradoException(id);
        }
        return contatoRepository.findByClienteIdOrderById(id).stream()
                .map(ContatoMapper::toResponse)
                .toList();
    }
}
