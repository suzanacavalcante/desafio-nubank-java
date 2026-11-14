package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA da paginação de {@code GET /clientes} (#29), com PostgreSQL real. Cobre o que os testes do PR deixaram de
 * fora (achado por mutação): a ordem é por <b>id</b>, e não por nome. Os testes do PR criam clientes com nomes em
 * ordem crescente (A, B, C...), então ordenar {@code findIds} por {@code nome} passava despercebido.
 */
@UsaPostgresDeTeste
@SpringBootTest
@AutoConfigureMockMvc
class ClientePaginacaoComportamentoTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void esvaziar() {
        jdbc.execute("truncate table contatos, clientes restart identity cascade");
    }

    private JsonNode pagina(int page, int size) throws Exception {
        String corpo = mockMvc.perform(get("/clientes").param("page", "" + page).param("size", "" + size))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(corpo);
    }

    private List<Long> idsDe(JsonNode pagina) {
        List<Long> ids = new ArrayList<>();
        pagina.get("content").forEach(c -> ids.add(c.get("id").asLong()));
        return ids;
    }

    private void inserir(String nome) {
        jdbc.update("insert into clientes (nome) values (?)", nome);
    }

    @Test
    void ordemEPorIdEIndependeDoNome() throws Exception {
        // ids 1..5 com nomes fora de ordem alfabética (e em parte decrescentes)
        for (String nome : List.of("Zeca", "Marta", "Ana", "Yuri", "Bia")) {
            inserir(nome);
        }

        JsonNode primeira = pagina(0, 2);
        JsonNode segunda = pagina(1, 2);
        JsonNode terceira = pagina(2, 2);

        assertEquals(List.of(1L, 2L), idsDe(primeira));
        assertEquals(List.of(3L, 4L), idsDe(segunda));
        assertEquals(List.of(5L), idsDe(terceira));
        assertEquals("Zeca", primeira.get("content").get(0).get("nome").asString());
    }

    @Test
    void percorrerTodasAsPaginasEntregaCadaClienteUmaVezEmOrdemDeId() throws Exception {
        List<String> nomes = new ArrayList<>();
        for (int i = 0; i < 57; i++) {
            nomes.add("n" + i);
        }
        Collections.shuffle(nomes, new Random(42)); // nomes sem relação com a ordem dos ids
        nomes.forEach(this::inserir);

        for (int size : new int[] {1, 7, 20, 56, 57, 58, 100}) {
            List<Long> vistos = new ArrayList<>();
            int numero = 0;
            JsonNode p;
            do {
                p = pagina(numero++, size);
                vistos.addAll(idsDe(p));
                assertEquals(57, p.get("totalElements").asLong(), "size=" + size);
            } while (!p.get("content").isEmpty());

            Set<Long> unicos = new HashSet<>(vistos);
            assertEquals(57, vistos.size(), "size=" + size + ": nenhum cliente repetido ou perdido");
            assertEquals(57, unicos.size(), "size=" + size);
            assertEquals(vistos.stream().sorted().toList(), vistos, "size=" + size + ": ordem crescente de id");
            assertEquals((57 + size - 1) / size, p.get("totalPages").asInt(), "size=" + size);
        }
    }

    @Test
    void idsComLacunasNaoQuebramAPaginacao() throws Exception {
        for (int i = 1; i <= 10; i++) {
            inserir("c" + i);
        }
        jdbc.update("delete from clientes where id in (3, 4, 5, 9)");

        assertEquals(List.of(1L, 2L, 6L), idsDe(pagina(0, 3)));
        assertEquals(List.of(7L, 8L, 10L), idsDe(pagina(1, 3)));
        assertEquals(6, pagina(0, 3).get("totalElements").asLong());
        assertEquals(2, pagina(0, 3).get("totalPages").asInt());
    }

    @Test
    void clientesComOMaximoDeContatosVemCompletosSemCortarPeloTamanhoDaPagina() throws Exception {
        // 3 clientes x 50 contatos (limite da API): a página de 2 clientes tem 100 linhas de contato, e o
        // total de linhas do join não pode ser confundido com o tamanho da página.
        for (int c = 1; c <= 3; c++) {
            inserir("com-contatos-" + c);
        }
        jdbc.update("insert into contatos (telefone, email, cliente_id) "
                + "select 't' || k, k || '@x.com', c.id from clientes c, generate_series(1, 50) k");

        JsonNode primeira = pagina(0, 2);
        JsonNode segunda = pagina(1, 2);

        assertEquals(2, primeira.get("content").size());
        assertEquals(1, segunda.get("content").size());
        for (JsonNode cliente : primeira.get("content")) {
            assertEquals(50, cliente.get("contatos").size());
        }
        assertEquals(50, segunda.get("content").get(0).get("contatos").size());
        assertEquals(3, primeira.get("totalElements").asLong());
        List<Long> ids = new ArrayList<>();
        primeira.get("content").get(0).get("contatos").forEach(k -> ids.add(k.get("id").asLong()));
        assertEquals(ids.stream().sorted().toList(), ids);
    }

    @Test
    void clienteInseridoNoFimDepoisDaPrimeiraPaginaNaoRepeteNemPulaOsJaVistos() throws Exception {
        for (int i = 1; i <= 4; i++) {
            inserir("c" + i);
        }
        List<Long> vistos = new ArrayList<>(idsDe(pagina(0, 2)));

        inserir("novo"); // id 5, escrita entre uma página e outra

        vistos.addAll(idsDe(pagina(1, 2)));
        vistos.addAll(idsDe(pagina(2, 2)));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), vistos);
    }
}
