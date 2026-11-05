package io.github.suzanacavalcante.desafio.contato.dto;

public record ContatoResponse(Long id, String telefone, String email, Long clienteId) {
}
