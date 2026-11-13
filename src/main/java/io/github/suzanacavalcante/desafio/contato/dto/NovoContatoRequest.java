package io.github.suzanacavalcante.desafio.contato.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.github.suzanacavalcante.desafio.contato.Contato;
import io.github.suzanacavalcante.desafio.validacao.SemCaracteresInvalidos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
public record NovoContatoRequest(
        @Schema(description = "Cliente que receberá o contato (precisa existir)", example = "1") @NotNull Long clienteId,
        @Schema(description = "Telefone do contato", example = "11888880000") @NotBlank @SemCaracteresInvalidos @Size(max = Contato.TELEFONE_MAX) String telefone,
        @Schema(description = "E-mail do contato", example = "outro@exemplo.com") @NotBlank @Email @Size(max = Contato.EMAIL_MAX) String email) {
}
