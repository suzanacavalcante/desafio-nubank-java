package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /clientes} de ponta a ponta (controller + service + JPA + PostgreSQL real).
 * <p>
 * Sem {@code @Transactional} no teste. O banco é o PostgreSQL descartável do Testcontainers e cada teste
 * começa com as tabelas vazias ({@code truncate}), então as asserções olham a <b>listagem inteira</b>,
 * sem filtrar por marca: o teste não depende de dados que outro teste tenha deixado.
 */
@UsaPostgresDeTeste
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class ClienteListagemApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @BeforeEach
    @AfterEach
    void esvaziarTabelas() {
        jdbc.execute("truncate table contatos, clientes restart identity cascade");
    }

    private void criarCliente(String nome, int contatos) throws Exception {
        StringBuilder lista = new StringBuilder();
        for (int i = 0; i < contatos; i++) {
            lista.append(i > 0 ? "," : "")
                    .append("{\"telefone\":\"t").append(i)
                    .append("\",\"email\":\"").append(i).append("@x.com\"}");
        }
        String json = "{\"nome\":\"" + nome + "\",\"contatos\":[" + lista + "]}";
        mockMvc.perform(post("/clientes")
                        .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                        .content(json.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated());
    }

    private List<ClienteResponse> listar() throws Exception {
        String corpo = mockMvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Arrays.asList(JSON.readValue(corpo, ClienteResponse[].class));
    }

    @Test
    void cadaClienteVemComSeusContatosEClienteSemContatosVemComListaVazia() throws Exception {
        criarCliente("A", 3);
        criarCliente("B", 0);
        criarCliente("C", 1);

        List<ClienteResponse> clientes = listar();

        assertEquals(List.of("A", "B", "C"),
                clientes.stream().map(ClienteResponse::nome).toList());
        assertEquals(3, clientes.get(0).contatos().size());
        assertTrue(clientes.get(1).contatos().isEmpty());
        assertEquals(1, clientes.get(2).contatos().size());
        for (ClienteResponse c : clientes) {
            for (ContatoResponse contato : c.contatos()) {
                assertEquals(c.id(), contato.clienteId());
            }
        }
    }

    @Test
    void clientesEContatosSaemOrdenadosPorId() throws Exception {
        criarCliente("A", 4);
        criarCliente("B", 4);

        List<ClienteResponse> clientes = listar();

        List<Long> idsDosClientes = clientes.stream().map(ClienteResponse::id).toList();
        assertEquals(idsDosClientes.stream().sorted().toList(), idsDosClientes);
        for (ClienteResponse c : clientes) {
            List<Long> ids = c.contatos().stream().map(ContatoResponse::id).toList();
            assertEquals(ids.stream().sorted().toList(), ids);
        }
    }

    /** Critério de aceite: "uma única consulta SQL principal", sem N+1 mesmo com vários contatos. */
    @Test
    void listarExecutaUmaUnicaConsultaSqlMesmoComVariosClientesEContatos() throws Exception {
        criarCliente("A", 3);
        criarCliente("B", 3);
        criarCliente("C", 3);
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        listar();

        assertEquals(1, estatisticas.getPrepareStatementCount(),
                "esperava 1 SELECT; consultas: " + Arrays.toString(estatisticas.getQueries()));
    }
}
