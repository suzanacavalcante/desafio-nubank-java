package io.github.suzanacavalcante.desafio.config;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaSystemException;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quando uma exceção conta como "banco indisponível" (503) e quando é erro de consulta/dados (500). */
class FalhaDeConexaoTest {

    @Test
    void conexaoFechadaNoRollbackDaPrimeiraRequisicaoDepoisDaQuedaContaComoIndisponibilidade() {
        // o que o pool devolve com a conexão já morta: SQLException sem SQLState
        Throwable erro = new JpaSystemException(new RuntimeException("Unable to rollback against JDBC Connection",
                new SQLException("Connection is closed")));

        assertTrue(FalhaDeConexao.causadaPor(erro));
    }

    @Test
    void excecoesJdbcDeConexaoContam() {
        assertTrue(FalhaDeConexao.causadaPor(new RuntimeException(new SQLTransientConnectionException("pool"))));
        assertTrue(FalhaDeConexao.causadaPor(new RuntimeException(new SQLNonTransientConnectionException("x"))));
        assertTrue(FalhaDeConexao.causadaPor(new RuntimeException(new SQLRecoverableException("x"))));
    }

    @Test
    void sqlStateDeFalhaDeConexaoOuServidorDesligandoConta() {
        assertTrue(FalhaDeConexao.causadaPor(new SQLException("I/O error", "08006")));
        assertTrue(FalhaDeConexao.causadaPor(new SQLException("sem conexão", "08001")));
        assertTrue(FalhaDeConexao.causadaPor(new SQLException("shutdown", "57P01")));
    }

    @Test
    void erroDeDadosOuDeConsultaNaoContaComoIndisponibilidade() {
        assertFalse(FalhaDeConexao.causadaPor(new RuntimeException(new SQLException("duplicado", "23505"))));
        assertFalse(FalhaDeConexao.causadaPor(new RuntimeException(new SQLException("not null", "23502"))));
        assertFalse(FalhaDeConexao.causadaPor(new RuntimeException(new SQLException("muito longo", "22001"))));
        assertFalse(FalhaDeConexao.causadaPor(new RuntimeException(new SQLException("sintaxe", "42601"))));
        assertFalse(FalhaDeConexao.causadaPor(new IllegalStateException("outro erro")));
        assertFalse(FalhaDeConexao.causadaPor(new RuntimeException()));
    }

    @Test
    void ciclosDeCausasNaoTravam() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        assertFalse(FalhaDeConexao.causadaPor(a));
    }
}
