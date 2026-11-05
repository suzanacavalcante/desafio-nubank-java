package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;

/**
 * Única implementação das conversões entre {@link Contato} e seus DTOs.
 * Os métodos {@code toEntity} <b>não</b> associam o cliente: quem chama decide como
 * (por {@code Cliente.adicionarContato} ou buscando o cliente existente).
 */
public final class ContatoMapper {

    private ContatoMapper() {
    }

    public static Contato toEntity(ContatoRequest request) {
        return novo(request.telefone(), request.email());
    }

    public static Contato toEntity(NovoContatoRequest request) {
        return novo(request.telefone(), request.email());
    }

    public static ContatoResponse toResponse(Contato contato) {
        Long clienteId = contato.getCliente() == null ? null : contato.getCliente().getId();
        return new ContatoResponse(contato.getId(), contato.getTelefone(), contato.getEmail(), clienteId);
    }

    private static Contato novo(String telefone, String email) {
        Contato contato = new Contato();
        contato.setTelefone(telefone);
        contato.setEmail(email);
        return contato;
    }
}
