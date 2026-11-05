package io.github.suzanacavalcante.desafio.contato.dto;

/**
 * Contato enviado dentro do corpo de {@code POST /clientes}.
 *
 * @param telefone telefone do contato
 * @param email    e-mail do contato
 */
public record ContatoRequest(String telefone, String email) {
}
