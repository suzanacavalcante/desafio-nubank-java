package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.contato.Contato;
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

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /clientes/{id}/contatos} de ponta a ponta (controller + service + JPA + PostgreSQL real).
 * <p>
 * Segue o padrão de {@code ClienteApiTest}: sem {@code @Transactional} no teste, marca única por
 * execução e limpeza no {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class ClienteContatosApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClienteRepository clienteRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private final String marca = "qa-contatos-" + UUID.randomUUID();

    @AfterEach
    void limpar() {
        jdbc.update("delete from contatos where cliente_id in (select id from clientes where nome like ?)", marca + "%");
        jdbc.update("delete from clientes where nome like ?", marca + "%");
    }

    /** Grava um cliente com {@code telefones.length} contatos (telefones curtos: a coluna tem 30). */
    private long novoCliente(String sufixo, String... telefones) {
        Cliente cliente = new Cliente();
        cliente.setNome(marca + sufixo);
        for (String telefone : telefones) {
            Contato contato = new Contato();
            contato.setTelefone(telefone);
            contato.setEmail(telefone + "@x.com");
            cliente.adicionarContato(contato);
        }
        return clienteRepository.save(cliente).getId();
    }

    @Test
    void clienteComContatosRetorna200ComOsSeusContatosOrdenadosPorId() throws Exception {
        long id = novoCliente("-A", "111", "222", "333");
        // Reescreve a linha do primeiro contato: o UPDATE grava uma nova versão no fim da tabela, então
        // sem ORDER BY ele sairia por último e o teste detectaria a troca por findByClienteId.
        jdbc.update("update contatos set email = email where cliente_id = ? and telefone = '111'", id);

        mockMvc.perform(get("/clientes/{id}/contatos", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].telefone").value("111"))
                .andExpect(jsonPath("$[1].telefone").value("222"))
                .andExpect(jsonPath("$[2].telefone").value("333"))
                .andExpect(jsonPath("$[0].email").value("111@x.com"))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].clienteId").value(id))
                .andExpect(jsonPath("$[2].clienteId").value(id));
    }

    @Test
    void naoMisturaContatosDeOutrosClientes() throws Exception {
        long ana = novoCliente("-A", "111", "222");
        novoCliente("-B", "999");

        mockMvc.perform(get("/clientes/{id}/contatos", ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.telefone == '999')]").isEmpty());
    }

    @Test
    void clienteExistenteSemContatosRetorna200ComArrayVazio() throws Exception {
        long id = novoCliente("-A");

        mockMvc.perform(get("/clientes/{id}/contatos", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    /** Sem N+1: uma consulta de existência + uma de contatos, qualquer que seja o número de contatos. */
    @Test
    void executaDuasConsultasSqlIndependenteDoNumeroDeContatos() throws Exception {
        long id = novoCliente("-A", "1", "2", "3", "4", "5");
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        mockMvc.perform(get("/clientes/{id}/contatos", id)).andExpect(status().isOk());

        assertEquals(2, estatisticas.getPrepareStatementCount(),
                "esperava existsById + SELECT de contatos; consultas: " + Arrays.toString(estatisticas.getQueries()));
    }

    /** Nenhuma sequência chega a este valor, então o cliente nunca existe. */
    private static final long ID_INEXISTENTE = Long.MAX_VALUE;

    /** Cliente inexistente: 404 no formato padrão de erro, e não 200 com lista vazia. */
    @Test
    void clienteInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/clientes/{id}/contatos", ID_INEXISTENTE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente " + ID_INEXISTENTE + " não encontrado"));
    }
}
