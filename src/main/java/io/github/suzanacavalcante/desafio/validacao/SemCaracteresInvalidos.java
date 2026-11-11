package io.github.suzanacavalcante.desafio.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Rejeita texto que o PostgreSQL não consegue gravar fielmente:
 * <ul>
 *   <li>o caractere NUL (U+0000): o banco o recusa ({@code invalid byte sequence for encoding "UTF8": 0x00})
 *       e a requisição viraria erro 500;</li>
 *   <li>surrogate UTF-16 desemparelhado (U+D800 a U+DFFF sem o par): o driver o troca por {@code ?} ao
 *       codificar, e o dado gravado deixaria de ser o que a resposta devolveu. Pares válidos (emojis) são
 *       aceitos.</li>
 * </ul>
 * {@code @Email} já rejeita ambos; {@code @NotBlank} e {@code @Size} não. Valor {@code null} é válido aqui
 * (a obrigatoriedade é de {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "[^\\u0000\\uD800-\\uDFFF]*")
@ReportAsSingleViolation
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER})
@Retention(RUNTIME)
public @interface SemCaracteresInvalidos {

    String message() default "contém caractere inválido (NUL ou surrogate UTF-16 desemparelhado)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
