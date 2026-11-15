package io.github.suzanacavalcante.desafio.config;

import com.zaxxer.hikari.HikariDataSource;
import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QA da #34: a variável {@code DB_CONNECTION_TIMEOUT_MS} (documentada no README) precisa chegar ao pool real do
 * Hikari. O PR só testa o valor padrão (lido do {@code Environment}) e o proxy com {@code connection-timeout}
 * sobrescrito direto pela propriedade do Spring; trocar o placeholder por um número fixo no
 * {@code application.yml} passava despercebido (achado por mutação).
 */
@UsaPostgresDeTeste
@SpringBootTest(properties = {"DB_CONNECTION_TIMEOUT_MS=1234", "DB_SOCKET_TIMEOUT_S=7"})
class TimeoutDeConexaoPorVariavelQaTest {

    @Autowired
    DataSource dataSource;

    @Test
    void variavelDeAmbienteDefineOConnectionTimeoutDoPoolReal() throws SQLException {
        assertEquals(1234L, dataSource.unwrap(HikariDataSource.class).getConnectionTimeout());
    }

    /** O limite de espera numa conexão já aberta (banco que trava) também vem da configuração e é repassado ao driver. */
    @Test
    void variavelDefineOSocketTimeoutEOTcpKeepAliveDoDriver() throws SQLException {
        var propriedades = dataSource.unwrap(HikariDataSource.class).getDataSourceProperties();

        assertEquals("7", String.valueOf(propriedades.get("socketTimeout")));
        assertEquals("true", String.valueOf(propriedades.get("tcpKeepAlive")));
    }
}
