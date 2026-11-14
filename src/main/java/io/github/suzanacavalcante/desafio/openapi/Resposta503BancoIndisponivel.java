package io.github.suzanacavalcante.desafio.openapi;

import io.swagger.v3.oas.annotations.headers.Header;
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
 * Meta-anotação: documenta a resposta {@code 503} (banco de dados indisponível) com o cabeçalho
 * {@code Retry-After}, no formato {@link Problema}, com exemplo.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "503", description = "Banco de dados indisponível; tente de novo após o `Retry-After`",
        headers = @Header(name = "Retry-After", description = "Segundos até tentar novamente",
                schema = @Schema(type = "integer", example = "5")),
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problema.class),
                examples = @ExampleObject(name = "banco indisponível", value = ExemplosDeErro.SERVICO_INDISPONIVEL_503)))
public @interface Resposta503BancoIndisponivel {
}
