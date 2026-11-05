package io.github.suzanacavalcante.desafio.cliente.dto;

import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;

import java.util.List;

/**
 * Corpo de {@code POST /clientes}.
 *
 * @param contatos opcional: pode ser {@code null} ou vazio (o cliente é criado sem contatos)
 */
public record ClienteRequest(String nome, List<ContatoRequest> contatos) {
}
