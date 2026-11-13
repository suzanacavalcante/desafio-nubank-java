package io.github.suzanacavalcante.desafio.suporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL 16 descartável para os testes de integração: um <b>único</b> container por execução do Maven,
 * compartilhado por todos os contextos do Spring (subir um por contexto custaria segundos a cada um).
 * <p>
 * {@code @ServiceConnection} liga a conexão do Spring ao container (URL, usuário e senha), então os testes
 * não dependem de banco local, do Docker Compose nem do {@code .env}. O container é encerrado pelo Ryuk do
 * Testcontainers quando a JVM termina; por isso o bean não tem método de destruição (um contexto fechado não
 * pode derrubar o banco dos outros).
 * <p>
 * Use pela anotação {@link UsaPostgresDeTeste}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDeTeste {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }
}
