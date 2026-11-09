package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.contato.Contato;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.ServletException;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private long idDeClienteInexistente() {
        return jdbc.queryForObject("select coalesce(max(id), 0) + 100000 from clientes", Long.class);
    }

    /**
     * Comportamento <b>atual</b>: sem o tratamento global de erros (issue #11) a exceção de domínio
     * atravessa o MockMvc. O ponto importante é que <b>não</b> vira 200 com lista vazia.
     * <p>
     * Na #11, trocar este {@code assertThrows} pelo teste de contrato abaixo (remover a tag e este método).
     */
    @Test
    void clienteInexistenteLancaExcecaoDeDominioEmVezDeListaVazia() {
        ServletException ex = assertThrows(ServletException.class,
                () -> mockMvc.perform(get("/clientes/{id}/contatos", idDeClienteInexistente())));

        assertInstanceOf(ClienteNaoEncontradoException.class, ex.getCause());
    }

    /**
     * <b>Contrato desejado</b> (critério de aceite da #11): cliente inexistente responde 404.
     * Falha de propósito até a #11; ao implementá-la, remover a tag {@code bug-conhecido} e o teste
     * {@code clienteInexistenteLancaExcecaoDeDominioEmVezDeListaVazia}. Rodar com
     * {@code -Dgroups=bug-conhecido -DexcludedGroups=nenhum}.
     */
    @Test
    @Tag("bug-conhecido")
    void clienteInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/clientes/{id}/contatos", idDeClienteInexistente()))
                .andExpect(status().isNotFound());
    }
}
