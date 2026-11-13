package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import io.github.suzanacavalcante.desafio.cliente.Cliente;
import io.github.suzanacavalcante.desafio.cliente.ClienteRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /contatos} de ponta a ponta (controller + service + JPA + PostgreSQL real).
 * <p>
 * Segue o padrão de {@code ClienteApiTest}: sem {@code @Transactional} no teste, com marca única
 * por execução e limpeza no {@code @AfterEach}.
 */
@UsaPostgresDeTeste
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ContatoApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClienteRepository clienteRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private final String marca = "qa-contato-" + UUID.randomUUID();

    @AfterEach
    void limpar() {
        jdbc.update("delete from contatos where cliente_id in (select id from clientes where nome like ?)", marca + "%");
        jdbc.update("delete from clientes where nome like ?", marca + "%");
    }

    private long novoCliente() {
        Cliente cliente = new Cliente();
        cliente.setNome(marca);
        return clienteRepository.save(cliente).getId();
    }

    private int contatosDoCliente(long clienteId) {
        return jdbc.queryForObject("select count(*) from contatos where cliente_id = ?", Integer.class, clienteId);
    }

    private ResultActions postar(String json) throws Exception {
        return mockMvc.perform(post("/contatos")
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void clienteExistenteRetorna201ECriaOContatoAssociado() throws Exception {
        long clienteId = novoCliente();

        postar("{\"clienteId\":" + clienteId + ",\"telefone\":\"48123\",\"email\":\"a@x.com\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.telefone").value("48123"))
                .andExpect(jsonPath("$.email").value("a@x.com"))
                .andExpect(jsonPath("$.clienteId").value(clienteId));

        assertEquals(1, contatosDoCliente(clienteId));
        assertEquals("48123", jdbc.queryForObject(
                "select telefone from contatos where cliente_id = ?", String.class, clienteId));
    }

    @Test
    void doisContatosParaOMesmoClienteSaoAmbosPersistidos() throws Exception {
        long clienteId = novoCliente();

        postar("{\"clienteId\":" + clienteId + ",\"telefone\":\"1\",\"email\":\"a@x.com\"}")
                .andExpect(status().isCreated());
        postar("{\"clienteId\":" + clienteId + ",\"telefone\":\"2\",\"email\":\"b@x.com\"}")
                .andExpect(status().isCreated());

        assertEquals(2, contatosDoCliente(clienteId));
    }

    /** Nenhuma sequência chega a este valor, então o cliente nunca existe. */
    private static final long ID_INEXISTENTE = Long.MAX_VALUE;

    /** Cliente inexistente: 404 no formato padrão de erro (ProblemDetail), e nada é gravado. */
    @Test
    void clienteInexistenteRetorna404() throws Exception {
        postar("{\"clienteId\":" + ID_INEXISTENTE + ",\"telefone\":\"48123\",\"email\":\"a@x.com\"}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente " + ID_INEXISTENTE + " não encontrado"));

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from contatos where telefone = '48123' and email = 'a@x.com' and cliente_id = ?",
                Integer.class, ID_INEXISTENTE));
    }

    /**
     * Review do PR #27: {@code POST /contatos} <b>não</b> inicializa a coleção LAZY {@code Cliente.contatos}.
     * O cliente já tem 5 contatos; se o service voltasse a usar {@code Cliente.adicionarContato}, a coleção
     * seria carregada só para acrescentar um (o {@code ContatoServiceTest} apenas guarda a interação).
     */
    @Test
    void postContatosNaoInicializaAColecaoLazyDoCliente() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setNome(marca);
        for (int i = 0; i < 5; i++) {
            Contato contato = new Contato();
            contato.setTelefone("t" + i);
            contato.setEmail(i + "@x.com");
            cliente.adicionarContato(contato);
        }
        long clienteId = clienteRepository.save(cliente).getId();
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        postar("{\"clienteId\":" + clienteId + ",\"telefone\":\"48123\",\"email\":\"a@x.com\"}")
                .andExpect(status().isCreated());

        assertEquals(0, estatisticas.getCollectionLoadCount(), "a coleção LAZY não deve ser carregada");
        assertEquals(0, estatisticas.getCollectionFetchCount(), "a coleção LAZY não deve ser buscada");
        assertEquals(6, contatosDoCliente(clienteId));
    }
}
