package io.github.suzanacavalcante.desafio.config;

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
 * Rejeita texto com o caractere NUL (U+0000).
 * <p>
 * O PostgreSQL não aceita esse caractere em colunas de texto ({@code invalid byte sequence for encoding
 * "UTF8": 0x00}), então sem esta regra uma entrada do cliente viraria erro 500 ao chegar ao banco.
 * {@code @Email} já o rejeita; {@code @NotBlank} e {@code @Size} não. Valor {@code null} é válido aqui
 * (a obrigatoriedade é de {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "[^\u0000]*")
@ReportAsSingleViolation
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER})
@Retention(RUNTIME)
public @interface SemCaractereNulo {

    String message() default "não pode conter o caractere NUL (U+0000)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
