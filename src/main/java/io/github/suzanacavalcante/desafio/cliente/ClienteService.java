package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

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
}
