package io.github.suzanacavalcante.desafio.contato.dto;

/** Corpo de {@code POST /contatos}: cria um contato para um cliente que já existe. */
public record NovoContatoRequest(Long clienteId, String telefone, String email) {
}
