package io.github.suzanacavalcante.desafio.config;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Forma do corpo de erro, <b>só para a documentação OpenAPI</b>: a resposta real é um
 * {@link org.springframework.http.ProblemDetail}, montado pelo {@link ApiExceptionHandler}. Este tipo existe
 * porque o schema gerado a partir de {@code ProblemDetail} mostra {@code erros} como uma propriedade genérica
 * ({@code properties}), e não como o campo que a API realmente devolve.
 *
 * @param title    resumo curto do erro
 * @param status   código HTTP
 * @param detail   explicação do erro
 * @param instance caminho da requisição
 * @param erros    só no 400 de validação: campo → mensagem
 */
@Schema(name = "Problema", description = "Erro no formato ProblemDetail (RFC 7807), application/problem+json. O campo `type` (about:blank) é omitido.")
public record Problema(
        @Schema(example = "Dados inválidos") String title,
        @Schema(example = "400") Integer status,
        @Schema(example = "Há campos inválidos na requisição.") String detail,
        @Schema(example = "/clientes") String instance,
        @Schema(description = "Presente só no 400 de validação: campo → mensagem",
                example = "{\"nome\": \"não deve estar em branco\"}") Map<String, String> erros) {
}
