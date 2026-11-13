package io.github.suzanacavalcante.desafio.contato.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.github.suzanacavalcante.desafio.contato.Contato;
import io.github.suzanacavalcante.desafio.validacao.SemCaracteresInvalidos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contato enviado dentro do corpo de {@code POST /clientes}. Limites iguais aos das colunas.
 *
 * @param telefone obrigatório, não pode ser vazio nem só espaços
 * @param email    obrigatório, em formato de e-mail válido
 */
public record ContatoRequest(
        @Schema(description = "Telefone do contato", example = "11999990000") @NotBlank @SemCaracteresInvalidos @Size(max = Contato.TELEFONE_MAX) String telefone,
        @Schema(description = "E-mail do contato", example = "maria@exemplo.com") @NotBlank @Email @Size(max = Contato.EMAIL_MAX) String email) {
}
