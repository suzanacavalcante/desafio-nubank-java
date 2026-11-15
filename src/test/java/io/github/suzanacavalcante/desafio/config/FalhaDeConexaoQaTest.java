package io.github.suzanacavalcante.desafio.config;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaSystemException;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA da #34: complementa {@link FalhaDeConexaoTest}. Os testes do PR constroem as exceções JDBC de conexão
 * <b>sem SQLState</b>, então a regra "SQLException sem SQLState conta" também as reconhece, e as três cláusulas
 * {@code instanceof} podiam ser removidas sem nenhum teste falhar (achado por mutação). Aqui cada tipo traz um
 * SQLState que as outras regras <b>não</b> reconhecem, de modo que só a cláusula do próprio tipo o salva.
 */
class FalhaDeConexaoQaTest {

    /** SQLState genérico (nem {@code 08}, nem {@code 57P}, nem nulo), como outros drivers/pools usam. */
    private static final String ESTADO_GENERICO = "HY000";

    @Test
    void sqlRecoverableExceptionContaMesmoComSqlStateQueNaoEDeConexao() {
        assertTrue(FalhaDeConexao.causadaPor(
                new RuntimeException(new SQLRecoverableException("recuperável", ESTADO_GENERICO))));
    }

    @Test
    void sqlNonTransientConnectionExceptionContaMesmoComSqlStateQueNaoEDeConexao() {
        assertTrue(FalhaDeConexao.causadaPor(
                new RuntimeException(new SQLNonTransientConnectionException("conexão perdida", ESTADO_GENERICO))));
    }

    @Test
    void sqlTransientConnectionExceptionDoPoolContaMesmoComSqlStateQueNaoEDeConexao() {
        // o Hikari reporta "request timed out" (pool sem conexão) com esta classe
        assertTrue(FalhaDeConexao.causadaPor(new JpaSystemException(new RuntimeException(
                new SQLTransientConnectionException("Connection is not available, request timed out after 3000ms.",
                        ESTADO_GENERICO)))));
    }

    @Test
    void sqlExceptionComSqlStateGenericoQueNaoEDeConexaoNaoConta() {
        // contraprova: o mesmo SQLState em uma SQLException "comum" (não de conexão) segue sendo erro da consulta
        assertFalse(FalhaDeConexao.causadaPor(new RuntimeException(new SQLException("erro", ESTADO_GENERICO))));
    }
}
