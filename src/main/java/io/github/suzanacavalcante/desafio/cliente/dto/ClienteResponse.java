package io.github.suzanacavalcante.desafio.cliente.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;

import java.util.List;

/** Cliente com seus contatos; a lista nunca é {@code null}. */
public record ClienteResponse(
        @Schema(description = "Identificador do cliente", example = "1") Long id,
        @Schema(description = "Nome do cliente", example = "Maria Silva") String nome,
        @Schema(description = "Contatos do cliente, ordenados por id (lista vazia se não tiver)") List<ContatoResponse> contatos) {
}
