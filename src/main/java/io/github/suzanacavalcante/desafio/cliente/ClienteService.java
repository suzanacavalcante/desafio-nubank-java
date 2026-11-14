package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.cliente.dto.PaginaDeClientes;
import io.github.suzanacavalcante.desafio.contato.ContatoMapper;
import io.github.suzanacavalcante.desafio.contato.ContatoRepository;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
     * Lista uma página de clientes com seus contatos, em <b>duas consultas</b> (mais a contagem total quando
     * a página inicial está cheia), em número constante: sem N+1 e sem crescer com o tamanho da página:
     * <ol>
     *   <li>{@link ClienteRepository#findIds}: os ids da página, por ordem de id;</li>
     *   <li>{@link ClienteRepository#findComContatosByIdIn}: esses clientes com os contatos
     *       ({@code JOIN FETCH}), preservando a ordem. Se a página está vazia, esta consulta nem acontece.</li>
     * </ol>
     * Clientes sem contatos vêm com lista vazia; clientes e contatos saem ordenados por id.
     * <p>
     * A conversão para {@link ClienteResponse} acontece <b>dentro</b> da transação; a coleção já vem
     * carregada, então o mapeamento não dispara consultas extras. {@code readOnly = true} dá só o modo
     * somente leitura (sem dirty checking nem flush); quem protege contra
     * {@code LazyInitializationException} é o {@code JOIN FETCH}.
     * <p>
     * Os limites de {@code page} e {@code size} são validados no controller; aqui valem as regras do
     * {@link PageRequest} ({@code size >= 1}, {@code page >= 0}).
     */
    @Transactional(readOnly = true)
    public PaginaDeClientes listar(int page, int size) {
        Page<Long> ids = clienteRepository.findIds(PageRequest.of(page, size));
        List<ClienteResponse> clientes = ids.isEmpty()
                ? List.of()
                : clienteRepository.findComContatosByIdIn(ids.getContent()).stream()
                        .map(ClienteMapper::toResponse)
                        .toList();
        return new PaginaDeClientes(clientes, ids.getNumber(), ids.getSize(), ids.getTotalElements(), ids.getTotalPages());
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
