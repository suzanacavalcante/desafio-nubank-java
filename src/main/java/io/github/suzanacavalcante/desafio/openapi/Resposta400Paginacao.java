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
 * Meta-anotação: documenta a resposta {@code 400} dos parâmetros de paginação ({@code page}/{@code size} fora
 * dos limites ou que não são números) no formato {@link Problema}, com exemplo.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "400", description = "`page` ou `size` fora dos limites (`erros` diz qual) ou que não são números",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = Problema.class),
                examples = @ExampleObject(name = "size acima do máximo", value = ExemplosDeErro.PAGINACAO_400)))
public @interface Resposta400Paginacao {
}
