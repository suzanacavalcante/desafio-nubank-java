package io.github.suzanacavalcante.desafio.contato.dto;

/**
 * Corpo de {@code POST /contatos}: cria um contato para um cliente que já existe.
 * <p>
 * Convenção de nomes: {@code Novo...Request} cria um recurso e {@code ...UpdateRequest} (ex.:
 * {@code ClienteUpdateRequest}) será o nome de uma eventual atualização, se ela for implementada.
 *
 * @param clienteId cliente que receberá o contato
 * @param telefone  telefone do contato
 * @param email     e-mail do contato
 */
public record NovoContatoRequest(Long clienteId, String telefone, String email) {
}
