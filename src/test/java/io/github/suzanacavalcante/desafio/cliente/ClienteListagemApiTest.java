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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    private JsonNode pagina(String... paramsEValores) throws Exception {
        var requisicao = get("/clientes");
        for (int i = 0; i < paramsEValores.length; i += 2) {
            requisicao.param(paramsEValores[i], paramsEValores[i + 1]);
        }
        String corpo = mockMvc.perform(requisicao)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(corpo);
    }

    /** Todos os clientes, sem paginar explicitamente (a página padrão cabe nos poucos clientes de cada teste). */
    private List<ClienteResponse> listar() throws Exception {
        return Arrays.asList(JSON.treeToValue(pagina().get("content"), ClienteResponse[].class));
    }

    private List<String> nomes(JsonNode pagina) {
        List<String> nomes = new ArrayList<>();
        pagina.get("content").forEach(c -> nomes.add(c.get("nome").asString()));
        return nomes;
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

    @Test
    void paginaOsClientesPorIdComOEnvelopeCompleto() throws Exception {
        for (String nome : List.of("A", "B", "C", "D", "E")) {
            criarCliente(nome, 1);
        }

        JsonNode primeira = pagina("page", "0", "size", "2");
        JsonNode segunda = pagina("page", "1", "size", "2");
        JsonNode terceira = pagina("page", "2", "size", "2");

        assertEquals(List.of("A", "B"), nomes(primeira));
        assertEquals(List.of("C", "D"), nomes(segunda));
        assertEquals(List.of("E"), nomes(terceira));
        for (JsonNode p : List.of(primeira, segunda, terceira)) {
            assertEquals(5, p.get("totalElements").asLong());
            assertEquals(3, p.get("totalPages").asInt());
            assertEquals(2, p.get("size").asInt());
        }
        assertEquals(1, segunda.get("page").asInt());
        assertEquals(2, terceira.get("page").asInt());
    }

    @Test
    void cadaClienteDaPaginaVemComTodosOsSeusContatosEmOrdem() throws Exception {
        criarCliente("A", 3);
        criarCliente("B", 0);
        criarCliente("C", 2);

        JsonNode pagina = pagina("size", "2");

        assertEquals(List.of("A", "B"), nomes(pagina));
        assertEquals(3, pagina.get("content").get(0).get("contatos").size());
        assertEquals(0, pagina.get("content").get(1).get("contatos").size());
        assertEquals(2, pagina("page", "1", "size", "2").get("content").get(0).get("contatos").size());
    }

    @Test
    void semParametrosUsaPagina0ETamanho20() throws Exception {
        for (int i = 0; i < 25; i++) {
            criarCliente("N" + i, 0);
        }

        JsonNode pagina = pagina();

        assertEquals(0, pagina.get("page").asInt());
        assertEquals(20, pagina.get("size").asInt());
        assertEquals(20, pagina.get("content").size());
        assertEquals(25, pagina.get("totalElements").asLong());
        assertEquals(2, pagina.get("totalPages").asInt());
    }

    @Test
    void paginaAlemDaUltimaDevolve200ComContentVazioEOTotalCerto() throws Exception {
        criarCliente("A", 1);

        JsonNode pagina = pagina("page", "7", "size", "10");

        assertEquals(0, pagina.get("content").size());
        assertEquals(7, pagina.get("page").asInt());
        assertEquals(1, pagina.get("totalElements").asLong());
        assertEquals(1, pagina.get("totalPages").asInt());
    }

    @Test
    void semClientesDevolveEnvelopeVazio() throws Exception {
        JsonNode pagina = pagina();

        assertEquals(0, pagina.get("content").size());
        assertEquals(0, pagina.get("totalElements").asLong());
        assertEquals(0, pagina.get("totalPages").asInt());
    }

    /** Limite máximo: 100 clientes numa página passam; 101 é recusado. */
    @Test
    void sizeNoMaximoPassaEAcimaDoMaximoEhRecusado() throws Exception {
        criarCliente("A", 0);

        assertEquals(100, pagina("size", "100").get("size").asInt());
        mockMvc.perform(get("/clientes").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void ultimaPaginaPermitidaNaoEstouraEDevolveVazio() throws Exception {
        criarCliente("A", 0);

        // page * size = 1 bilhão: cabe em int, então o OFFSET é aceito (acima disso daria 500)
        JsonNode pagina = pagina("page", "10000000", "size", "100");

        assertEquals(0, pagina.get("content").size());
        assertEquals(1, pagina.get("totalElements").asLong());
    }

    @Test
    void parametrosInvalidosDevolvem400NoFormatoDeErro() throws Exception {
        mockMvc.perform(get("/clientes").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/clientes").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/clientes").param("page", "x")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/clientes").param("size", "99999999999")).andExpect(status().isBadRequest());
        // sem o limite de page, isto estouraria o int do OFFSET e daria 500
        mockMvc.perform(get("/clientes").param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "100"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Critério de aceite da #29: o número de consultas <b>não cresce</b> com o tamanho da página nem com o número
     * de contatos (sem N+1). Página cheia: 1 de ids + 1 de contagem + 1 dos contatos = 3; página inicial não cheia:
     * a contagem é dispensada (2).
     */
    @Test
    void numeroDeConsultasNaoCresceComOTamanhoDaPagina() throws Exception {
        for (int i = 0; i < 12; i++) {
            criarCliente("C" + i, 3);
        }
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        for (String size : List.of("1", "2", "5", "10")) {
            estatisticas.clear();
            JsonNode pagina = pagina("size", size);
            assertEquals(Integer.parseInt(size), pagina.get("content").size());
            assertEquals(3, estatisticas.getPrepareStatementCount(),
                    "size=" + size + "; consultas: " + Arrays.toString(estatisticas.getQueries()));
        }
        estatisticas.clear();
        pagina("size", "100");
        assertEquals(2, estatisticas.getPrepareStatementCount(),
                "página inicial não cheia dispensa a contagem; consultas: " + Arrays.toString(estatisticas.getQueries()));
    }

    @Test
    void numeroDeConsultasEOMesmoEmQualquerPagina() throws Exception {
        for (int i = 0; i < 9; i++) {
            criarCliente("P" + i, 2);
        }
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        for (String page : List.of("0", "1", "2")) {
            estatisticas.clear();
            pagina("page", page, "size", "3");
            assertEquals(3, estatisticas.getPrepareStatementCount(), "page=" + page);
        }
    }

    /** Página vazia (além da última): 2 consultas (ids + contagem), a dos contatos nem acontece. */
    @Test
    void paginaVaziaNaoConsultaOsContatos() throws Exception {
        criarCliente("A", 2);
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        pagina("page", "3", "size", "5");

        assertEquals(2, estatisticas.getPrepareStatementCount(), Arrays.toString(estatisticas.getQueries()));
    }
}
