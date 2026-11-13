package io.github.suzanacavalcante.desafio.suporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
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
 * O container sobe <b>sob demanda</b> (na primeira classe que precisa dele), e não num bloco {@code static}:
 * se o Docker não existir, <b>toda</b> classe de integração falha com a mesma mensagem explicativa, em vez
 * de só a primeira e as outras com {@code NoClassDefFoundError}.
 * <p>
 * Use pela anotação {@link UsaPostgresDeTeste}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDeTeste {

    private static PostgreSQLContainer postgres;

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return iniciar();
    }

    private static synchronized PostgreSQLContainer iniciar() {
        if (postgres == null) {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new IllegalStateException("""
                        Docker não encontrado: os testes de integração usam o Testcontainers (postgres:16).
                        Instale/inicie o Docker e rode o ./mvnw verify onde o Docker estiver acessível
                        (no Windows com o Docker só no WSL, rode dentro do WSL). Veja o README, seção Testes.""");
            }
            PostgreSQLContainer container = new PostgreSQLContainer("postgres:16");
            container.start();
            postgres = container;
        }
        return postgres;
    }
}
