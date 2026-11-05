package io.github.suzanacavalcante.desafio.contato.dto;

/**
 * Contato devolvido pela API.
 *
 * @param id        identificador do contato
 * @param telefone  telefone do contato
 * @param email     e-mail do contato
 * @param clienteId identificador do cliente dono do contato
 */
public record ContatoResponse(Long id, String telefone, String email, Long clienteId) {
}
