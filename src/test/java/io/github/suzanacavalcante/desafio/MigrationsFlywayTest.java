package io.github.suzanacavalcante.desafio;

import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O schema do banco nasce das migrations do Flyway (#16) e o Hibernate só o valida. Este teste fixa o que
 * as migrations prometem e o Hibernate <b>não</b> confere: a FK com {@code ON DELETE CASCADE} e o índice em
 * {@code contatos.cliente_id} (o {@code validate} não olha índices, chaves primárias, nulidade da FK nem a regra de exclusão).
 * <p>
 * Que as entidades e as migrations batem entre si é provado pelo próprio contexto: com
 * {@code ddl-auto=validate} ele não sobe se faltar tabela/coluna ou se o tipo ou tamanho divergir.
 */
@UsaPostgresDeTeste
@SpringBootTest
class MigrationsFlywayTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment env;

    @BeforeEach
    @AfterEach
    void esvaziarTabelas() {
        jdbc.execute("truncate table contatos, clientes restart identity cascade");
    }

    @Test
    void hibernateApenasValidaOSchema() {
        assertEquals("validate", env.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    void v1FoiAplicadaComSucesso() {
        List<Map<String, Object>> historico = jdbc.queryForList(
                "select version, description, success from flyway_schema_history where version is not null");
        assertEquals(1, historico.size());
        assertEquals("1", historico.get(0).get("version"));
        assertEquals("cria tabelas", historico.get(0).get("description"));
        assertEquals(true, historico.get(0).get("success"));
    }

    @Test
    void chaveEstrangeiraApagaOsContatosEmCascata() {
        String regra = jdbc.queryForObject("""
                select delete_rule from information_schema.referential_constraints
                where constraint_name = 'fk_contatos_cliente'""", String.class);
        assertEquals("CASCADE", regra);
    }

    @Test
    void apagarOClienteNoBancoApagaSeusContatos() {
        jdbc.update("insert into clientes (nome) values ('Ana')");
        Long id = jdbc.queryForObject("select id from clientes", Long.class);
        jdbc.update("insert into contatos (telefone, email, cliente_id) values ('1', 'a@x.com', ?)", id);

        jdbc.update("delete from clientes where id = ?", id);

        assertEquals(0, jdbc.queryForObject("select count(*) from contatos", Integer.class));
    }

    @Test
    void contatoSemClienteExistenteEhRejeitadoPeloBanco() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("insert into contatos (telefone, email, cliente_id) values ('1', 'a@x.com', 999)"));
    }

    @Test
    void contatoComClienteIdNuloEhRejeitadoPeloBanco() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("insert into contatos (telefone, email, cliente_id) values ('1', 'a@x.com', null)"));
    }

    @Test
    void idsSaoChavesPrimariasNasDuasTabelas() {
        List<String> tabelas = jdbc.queryForList("""
                select tc.table_name from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name and kcu.table_schema = tc.table_schema
                where tc.constraint_type = 'PRIMARY KEY' and kcu.column_name = 'id'
                  and tc.table_name in ('clientes', 'contatos')
                order by tc.table_name""", String.class);
        assertEquals(List.of("clientes", "contatos"), tabelas);
    }

    @Test
    void existeIndiceNaColunaDaChaveEstrangeira() {
        String definicao = jdbc.queryForObject("""
                select indexdef from pg_indexes
                where tablename = 'contatos' and indexname = 'idx_contatos_cliente_id'""", String.class);
        assertTrue(definicao.contains("(cliente_id)"), definicao);
    }

    @Test
    void tamanhosDasColunasAcompanhamOsLimitesDaValidacao() {
        assertEquals(150, tamanho("clientes", "nome"));
        assertEquals(30, tamanho("contatos", "telefone"));
        assertEquals(254, tamanho("contatos", "email"));
    }

    private int tamanho(String tabela, String coluna) {
        return jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_name = ? and column_name = ?""", Integer.class, tabela, coluna);
    }
}
