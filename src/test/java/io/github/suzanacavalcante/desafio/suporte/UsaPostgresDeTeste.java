package io.github.suzanacavalcante.desafio.suporte;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Faz o teste usar o PostgreSQL descartável ({@link PostgresDeTeste}) em vez do banco do desenvolvedor.
 * <ul>
 *   <li>o schema do container nasce das migrations do Flyway (as mesmas da produção) e o Hibernate apenas
 *       o valida ({@code ddl-auto=validate}, herdado do {@code application.yml}): se uma entidade e a
 *       migration divergirem, o contexto nem sobe;</li>
 *   <li>define usuário e senha de mentira: o {@code application.yml} exige {@code DB_USER} e
 *       {@code DB_PASSWORD} sem valor padrão, e o container já traz as credenciais reais pela conexão
 *       de serviço, que prevalece sobre estas.</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(PostgresDeTeste.class)
@TestPropertySource(properties = {
        "spring.datasource.username=teste",
        "spring.datasource.password=teste"})
public @interface UsaPostgresDeTeste {
}
