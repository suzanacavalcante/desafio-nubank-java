package io.github.suzanacavalcante.desafio.contato.dto;

/** Contato enviado dentro do corpo de {@code POST /clientes}. */
public record ContatoRequest(String telefone, String email) {
}
