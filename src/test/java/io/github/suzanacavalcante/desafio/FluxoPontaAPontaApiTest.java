package io.github.suzanacavalcante.desafio;

import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo da API, na ordem em que um cliente real a usaria, com tudo de verdade (controllers, services,
 * JPA e PostgreSQL do Testcontainers): criar cliente → criar contato → listar → listar contatos do cliente.
 * Cada teste começa com as tabelas vazias.
 */
@UsaPostgresDeTeste
@SpringBootTest
@AutoConfigureMockMvc
class FluxoPontaAPontaApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void esvaziarTabelas() {
        jdbc.execute("truncate table contatos, clientes restart identity cascade");
    }

    private JsonNode enviar(RequestBuilder requisicao, int statusEsperado)
            throws Exception {
        MvcResult resultado = mockMvc.perform(requisicao).andExpect(status().is(statusEsperado)).andReturn();
        return JSON.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static RequestBuilder postar(String url, String json) {
        return post(url).contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void criarClienteCriarContatoListarEListarOsContatosDoCliente() throws Exception {
        // 1) cria o cliente, já com um contato
        JsonNode criado = enviar(postar("/clientes",
                "{\"nome\":\"Maria\",\"contatos\":[{\"telefone\":\"11999990000\",\"email\":\"maria@exemplo.com\"}]}"), 201);
        long clienteId = criado.get("id").asLong();
        assertEquals("Maria", criado.get("nome").asString());
        assertEquals(1, criado.get("contatos").size());

        // 2) acrescenta outro contato ao cliente existente
        JsonNode contato = enviar(postar("/contatos",
                "{\"clienteId\":" + clienteId + ",\"telefone\":\"11888880000\",\"email\":\"outro@exemplo.com\"}"), 201);
        assertEquals(clienteId, contato.get("clienteId").asLong());

        // um segundo cliente, sem contatos
        enviar(postar("/clientes", "{\"nome\":\"Joao\"}"), 201);

        // 3) lista tudo: os dois clientes, na ordem, cada um com seus contatos
        JsonNode pagina = enviar(get("/clientes"), 200);
        assertEquals(2, pagina.get("totalElements").asLong());
        assertEquals(1, pagina.get("totalPages").asInt());
        assertEquals(0, pagina.get("page").asInt());
        assertEquals(20, pagina.get("size").asInt());
        JsonNode lista = pagina.get("content");
        assertEquals(2, lista.size());
        assertEquals("Maria", lista.get(0).get("nome").asString());
        assertEquals(2, lista.get(0).get("contatos").size());
        assertEquals("11999990000", lista.get(0).get("contatos").get(0).get("telefone").asString());
        assertEquals("11888880000", lista.get(0).get("contatos").get(1).get("telefone").asString());
        assertEquals("Joao", lista.get(1).get("nome").asString());
        assertEquals(0, lista.get(1).get("contatos").size());

        // 4) lista os contatos do cliente: os mesmos dois, na ordem de criação
        JsonNode contatos = enviar(get("/clientes/" + clienteId + "/contatos"), 200);
        assertEquals(2, contatos.size());
        assertEquals("maria@exemplo.com", contatos.get(0).get("email").asString());
        assertEquals("outro@exemplo.com", contatos.get(1).get("email").asString());
        contatos.forEach(c -> assertEquals(clienteId, c.get("clienteId").asLong()));
    }

    @Test
    void contatosDeClienteSemContatosSaoListaVaziaEDeClienteInexistenteSao404() throws Exception {
        long clienteId = enviar(postar("/clientes", "{\"nome\":\"Sem contatos\"}"), 201).get("id").asLong();

        assertEquals(0, enviar(get("/clientes/" + clienteId + "/contatos"), 200).size());
        enviar(get("/clientes/" + (clienteId + 1000) + "/contatos"), 404);
    }
}
