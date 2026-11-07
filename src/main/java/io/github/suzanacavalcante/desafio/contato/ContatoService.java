package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.cliente.Cliente;
import io.github.suzanacavalcante.desafio.cliente.ClienteRepository;
import io.github.suzanacavalcante.desafio.config.ClienteNaoEncontradoException;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContatoService {

    private final ClienteRepository clienteRepository;
    private final ContatoRepository contatoRepository;

    /**
     * Cria um contato para um cliente que já existe.
     * <p>
     * O cliente é carregado sem a sua coleção de contatos (que é LAZY): por isso o contato é associado
     * com {@code setCliente} e salvo pelo seu próprio repository, em vez de {@code Cliente.adicionarContato},
     * que carregaria todos os contatos do cliente só para acrescentar um.
     * <p>
     * A conversão para {@link ContatoResponse} acontece dentro da transação.
     *
     * @throws ClienteNaoEncontradoException se não existir cliente com o {@code clienteId} informado
     */
    @Transactional
    public ContatoResponse criar(NovoContatoRequest request) {
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new ClienteNaoEncontradoException(request.clienteId()));

        Contato contato = ContatoMapper.toEntity(request);
        contato.setCliente(cliente);

        return ContatoMapper.toResponse(contatoRepository.save(contato));
    }
}
