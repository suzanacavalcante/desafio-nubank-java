package io.github.suzanacavalcante.desafio.contato.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contato devolvido pela API.
 *
 * @param id        identificador do contato
 * @param telefone  telefone do contato
 * @param email     e-mail do contato
 * @param clienteId identificador do cliente dono do contato
 */
public record ContatoResponse(
        @Schema(description = "Identificador do contato", example = "10") Long id,
        @Schema(description = "Telefone do contato", example = "11999990000") String telefone,
        @Schema(description = "E-mail do contato", example = "maria@exemplo.com") String email,
        @Schema(description = "Cliente dono do contato", example = "1") Long clienteId) {
}
