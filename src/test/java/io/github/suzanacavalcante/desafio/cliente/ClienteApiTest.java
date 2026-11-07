package io.github.suzanacavalcante.desafio.cliente;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
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
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
class ClienteApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private final String marca = "qa-" + UUID.randomUUID();

    @AfterEach
    void limpar() {
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
        // 1º contato válido, 2º sem e-mail: o banco rejeita o 2º, e o 1º e o cliente devem sofrer rollback.
        // Hoje (sem handler) a exceção sobe pelo MockMvc; depois da #11 vira uma resposta 4xx. O teste vale nos dois casos,
        // mas exige que a requisição tenha FALHADO, para provar que o rollback foi exercitado.
        Integer status = null;
        Throwable falha = null;
        try {
            status = postar("{\"nome\":\"" + marca + "\",\"contatos\":["
                    + "{\"telefone\":\"1\",\"email\":\"ok@x.com\"},{\"telefone\":\"2\"}]}")
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
