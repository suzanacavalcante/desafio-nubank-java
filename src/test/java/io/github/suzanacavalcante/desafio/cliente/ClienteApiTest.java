package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /clientes} de ponta a ponta (controller + service + JPA + PostgreSQL real).
 * <p>
 * Não usa {@code @Transactional} no teste de propósito: a atomicidade do service só é observável
 * quando ele abre e dá rollback na própria transação. Para não sujar o banco, cada cliente criado
 * leva um sufixo único e é removido no {@code @AfterEach}.
 */
@UsaPostgresDeTeste
@SpringBootTest
@AutoConfigureMockMvc
class ClienteApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private final String marca = "qa-" + UUID.randomUUID();

    private String indiceTemporario;

    @AfterEach
    void limpar() {
        if (indiceTemporario != null) {
            jdbc.execute("drop index if exists " + indiceTemporario);
        }
        jdbc.update("delete from contatos where cliente_id in (select id from clientes where nome like ?)", marca + "%");
        jdbc.update("delete from clientes where nome like ?", marca + "%");
    }

    private int contarClientes() {
        return jdbc.queryForObject("select count(*) from clientes where nome like ?", Integer.class, marca + "%");
    }

    private int contarContatos() {
        return jdbc.queryForObject(
                "select count(*) from contatos where cliente_id in (select id from clientes where nome like ?)",
                Integer.class, marca + "%");
    }

    private org.springframework.test.web.servlet.ResultActions postar(String json) throws Exception {
        return mockMvc.perform(post("/clientes")
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void criaClienteSemContatosEPersisteNoBanco() throws Exception {
        postar("{\"nome\":\"" + marca + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value(marca))
                .andExpect(jsonPath("$.contatos").isEmpty());

        assertEquals(1, contarClientes());
        assertEquals(0, contarContatos());
    }

    @Test
    void criaClienteComContatosEDevolveClienteIdIgualAoIdDoCliente() throws Exception {
        postar("{\"nome\":\"" + marca + "\",\"contatos\":["
                + "{\"telefone\":\"48111\",\"email\":\"a@x.com\"},"
                + "{\"telefone\":\"48222\",\"email\":\"b@x.com\"}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contatos.length()").value(2))
                .andExpect(jsonPath("$.contatos[0].id").isNumber())
                .andExpect(jsonPath("$.contatos[0].clienteId").value(
                        jdbc.queryForObject("select id from clientes where nome = ?", Long.class, marca)));

        assertEquals(2, contarContatos());
    }

    @Test
    void preservaUnicodeEEmojiNoNomeENoEmail() throws Exception {
        String nome = marca + " José Ñandú 日本 😀";

        postar("{\"nome\":\"" + nome + "\",\"contatos\":[{\"telefone\":\"1\",\"email\":\"é@ü.com\"}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value(nome))
                .andExpect(jsonPath("$.contatos[0].email").value("é@ü.com"));

        assertEquals(nome, jdbc.queryForObject("select nome from clientes where nome like ?", String.class, marca + "%"));
    }

    @Test
    void aceitaNomeComExatamenteOLimiteDeCaracteres() throws Exception {
        String nome = marca + "x".repeat(Cliente.NOME_MAX - marca.length());

        postar("{\"nome\":\"" + nome + "\"}").andExpect(status().isCreated());

        assertEquals(1, contarClientes());
    }

    @Test
    void duasRequisicoesIguaisCriamDoisClientesSemIdempotenciaNemUnicidade() throws Exception {
        String corpo = "{\"nome\":\"" + marca + "\"}";

        postar(corpo).andExpect(status().isCreated());
        postar(corpo).andExpect(status().isCreated());

        assertEquals(2, contarClientes());
    }

    @Test
    void atomicidadeContatoInvalidoNaListaNaoDeixaClienteNemContatosPersistidos() throws Exception {
        // A entrada é VÁLIDA para o Bean Validation (#10), então a falha só ocorre no banco, depois do 1º contato
        // já ter sido enviado: um índice único parcial, criado só neste teste e só para este e-mail, faz o 2º contato
        // (mesmo e-mail) violar a restrição. Assim o teste continua provando rollback depois da #10.
        // Hoje (sem handler) a exceção sobe pelo MockMvc; depois da #11 vira uma resposta 4xx. O teste exige que a
        // requisição tenha FALHADO, para provar que o rollback foi exercitado.
        String email = marca + "@x.com";
        indiceTemporario = "idx_qa_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create unique index " + indiceTemporario + " on contatos (email) where email = '" + email + "'");
        Integer status = null;
        Throwable falha = null;
        try {
            status = postar("{\"nome\":\"" + marca + "\",\"contatos\":["
                    + "{\"telefone\":\"1\",\"email\":\"" + email + "\"},"
                    + "{\"telefone\":\"2\",\"email\":\"" + email + "\"}]}")
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            falha = e;
        }

        if (falha != null) {
            boolean violacaoDoBanco = false;
            for (Throwable t = falha; t != null; t = t.getCause()) {
                violacaoDoBanco |= t instanceof org.springframework.dao.DataIntegrityViolationException;
            }
            assertTrue(violacaoDoBanco, "a falha deve vir do banco, não de outro erro: " + falha);
        } else {
            assertTrue(status >= 400, "o contato inválido não pode resultar em sucesso, status " + status);
        }

        assertEquals(0, contarClientes(), "cliente não pode ficar gravado");
        assertEquals(0, contarContatos(), "contato válido não pode ficar gravado");
    }
}
