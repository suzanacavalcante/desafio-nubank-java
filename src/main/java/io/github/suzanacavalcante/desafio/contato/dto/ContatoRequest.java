package io.github.suzanacavalcante.desafio.contato.dto;

import io.github.suzanacavalcante.desafio.contato.Contato;
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
        @NotBlank @Size(max = Contato.TELEFONE_MAX) String telefone,
        @NotBlank @Email @Size(max = Contato.EMAIL_MAX) String email) {
}
