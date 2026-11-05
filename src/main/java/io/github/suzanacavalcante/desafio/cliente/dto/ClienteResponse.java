package io.github.suzanacavalcante.desafio.cliente.dto;

import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;

import java.util.List;

/** Cliente com seus contatos; a lista nunca é {@code null}. */
public record ClienteResponse(Long id, String nome, List<ContatoResponse> contatos) {
}
