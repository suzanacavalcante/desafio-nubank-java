package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Rollback de {@code ClienteService.criar} (review do PR #25), com o banco de verdade e <b>sem</b>
 * {@code @Transactional} no teste: se o teste abrisse a transação, ele mesmo faria o rollback e esconderia
 * o comportamento do service.
 * <p>
 * Chama o service direto porque a validação (#10) já barra, na API, um contato com telefone nulo; aqui se
 * prova a segunda linha de defesa, o banco (coluna {@code NOT NULL}), e que a falha desfaz tudo.
 */
@UsaPostgresDeTeste
@SpringBootTest
class ClienteRollbackIntegrationTest {

    @Autowired
    ClienteService service;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void esvaziarTabelas() {
        jdbc.execute("truncate table contatos, clientes restart identity cascade");
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    @Test
    void contatoComTelefoneNuloDesfazOClienteEOsContatosJaEnviados() {
        var request = new ClienteRequest("Ana", List.of(
                new ContatoRequest("48123", "valido@x.com"),
                new ContatoRequest(null, "invalido@x.com")));

        assertThrows(DataIntegrityViolationException.class, () -> service.criar(request));

        assertEquals(0, contar("clientes"), "o cliente não pode ficar gravado");
        assertEquals(0, contar("contatos"), "nem o contato válido que veio antes do inválido");
    }

    @Test
    void depoisDeUmaFalhaOServiceContinuaGravandoNormalmente() {
        assertThrows(DataIntegrityViolationException.class, () -> service.criar(
                new ClienteRequest("Ana", List.of(new ContatoRequest(null, "x@x.com")))));

        service.criar(new ClienteRequest("Bia", List.of(new ContatoRequest("1", "b@x.com"))));

        assertEquals(1, contar("clientes"));
        assertEquals(1, contar("contatos"));
    }
}
