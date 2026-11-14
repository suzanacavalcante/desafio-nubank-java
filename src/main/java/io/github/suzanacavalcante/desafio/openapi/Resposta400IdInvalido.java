package io.github.suzanacavalcante.desafio.openapi;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-anotação: documenta a resposta {@code 400} (`id` que não é um número) no formato {@link Problema}, com exemplo.
 * Substitui o bloco repetido de {@code @ApiResponse} + {@code @Content} + {@code @ExampleObject} nos controllers.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "400", description = "`id` que não é um número",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problema.class),
                examples = @ExampleObject(name = "id inválido", value = ExemplosDeErro.ID_INVALIDO_400)))
public @interface Resposta400IdInvalido {
}
