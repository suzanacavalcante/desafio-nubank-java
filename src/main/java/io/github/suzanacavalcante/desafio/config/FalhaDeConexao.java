package io.github.suzanacavalcante.desafio.config;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

/**
 * Reconhece, pela cadeia de causas, quando uma exceção veio de o banco estar <b>indisponível</b> (conexão
 * recusada, derrubada, pool sem conexão), e não de um erro da consulta ou dos dados.
 * <p>
 * Existe porque a primeira requisição depois da queda costuma pegar uma conexão <b>já morta</b> do pool: a
 * consulta falha e o rollback também ("Connection is closed"), e o Spring o devolve como
 * {@code JpaSystemException}, que não é {@code CannotCreateTransactionException} nem
 * {@code DataAccessResourceFailureException}, embora a causa seja a mesma queda.
 * <p>
 * Conta como indisponibilidade: exceções JDBC de conexão ({@code SQLTransientConnectionException},
 * {@code SQLNonTransientConnectionException}, {@code SQLRecoverableException}); {@code SQLState} da classe
 * {@code 08} (falha de conexão) ou {@code 57P} (servidor desligando/reiniciando); e {@code SQLException} sem
 * {@code SQLState}, que é como o pool/driver reporta uma conexão fechada. Erros de dados (restrições, sintaxe)
 * sempre trazem {@code SQLState} das classes {@code 22}, {@code 23}, {@code 42} etc. e <b>não</b> entram.
 */
final class FalhaDeConexao {

    private FalhaDeConexao() {
    }

    static boolean causadaPor(Throwable erro) {
        // limite de profundidade: protege contra ciclos de causas
        Throwable atual = erro;
        for (int i = 0; atual != null && i < 20; i++, atual = atual.getCause()) {
            if (atual instanceof SQLTransientConnectionException
                    || atual instanceof SQLNonTransientConnectionException
                    || atual instanceof SQLRecoverableException) {
                return true;
            }
            if (atual instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if (estado == null || estado.startsWith("08") || estado.startsWith("57P")) {
                    return true;
                }
            }
        }
        return false;
    }
}
