package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.contato.ContatoMapper;

/**
 * Única implementação das conversões entre {@link Cliente} e seus DTOs. A conversão de cada contato
 * é delegada a {@link ContatoMapper}, sem duplicá-la.
 */
public final class ClienteMapper {

    private ClienteMapper() {
    }

    /** Cria o cliente já com seus contatos (se vierem), sincronizando os dois lados do relacionamento. */
    public static Cliente toEntity(ClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNome(request.nome());
        if (request.contatos() != null) {
            request.contatos().forEach(c -> cliente.adicionarContato(ContatoMapper.toEntity(c)));
        }
        return cliente;
    }

    public static ClienteResponse toResponse(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNome(),
                cliente.getContatos().stream().map(ContatoMapper::toResponse).toList());
    }
}
