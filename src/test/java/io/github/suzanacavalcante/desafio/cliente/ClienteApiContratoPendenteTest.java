package io.github.suzanacavalcante.desafio.cliente;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de regressão dos bugs de QA (relatório de 2026-11-07): o enunciado diz que
 * {@code POST /clientes} responde <b>400</b> para entrada inválida, mas a {@code main} (a01138e)
 * responde 500 ou aceita o dado. Falham de propósito até as issues #10 (Bean Validation) e
 * #11 (tratamento global de erros) serem implementadas.
 * <p>
 * Marcados com {@code bug-conhecido}, ficam fora do {@code ./mvnw verify} padrão para não quebrar o
 * CI. Para rodá-los: {@code ./mvnw test -Dgroups=bug-conhecido -DexcludedGroups=nenhum}.
 * Quando o bug for corrigido, o teste correspondente deve passar e a tag deve ser removida.
 */
@Tag("bug-conhecido")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
class ClienteApiContratoPendenteTest {

    private static final String MARCA = "qa-pendente-";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * Enquanto o bug existir, o dado inválido é gravado. Apaga só o que os testes podem ter criado:
     * os nomes com a marca e os dois valores exatos sem marca possível (vazio e só espaços).
     * Não usa faixa de id, para não apagar dados de outro escritor no mesmo banco.
     */
    @AfterEach
    void limpar() {
        String filtro = "select id from clientes where nome like ? or nome in ('', '   ')";
        jdbc.update("delete from contatos where cliente_id in (" + filtro + ")", MARCA + "%");
        jdbc.update("delete from clientes where nome like ? or nome in ('', '   ')", MARCA + "%");
    }

    private void esperar400(String json) throws Exception {
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarClienteSemNome() throws Exception {
        esperar400("{}");
    }

    @Test
    void deveRejeitarNomeNulo() throws Exception {
        esperar400("{\"nome\":null}");
    }

    @Test
    void deveRejeitarNomeVazio() throws Exception {
        esperar400("{\"nome\":\"\"}");
    }

    @Test
    void deveRejeitarNomeSoComEspacos() throws Exception {
        esperar400("{\"nome\":\"   \"}");
    }

    @Test
    void deveRejeitarNomeAcimaDoLimite() throws Exception {
        esperar400("{\"nome\":\"" + "a".repeat(Cliente.NOME_MAX + 1) + "\"}");
    }

    @Test
    void deveRejeitarContatoNuloNaLista() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "1\",\"contatos\":[null]}");
    }

    @Test
    void deveRejeitarContatoSemEmail() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "2\",\"contatos\":[{\"telefone\":\"1\"}]}");
    }

    @Test
    void deveRejeitarContatoSemTelefone() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "3\",\"contatos\":[{\"email\":\"a@x.com\"}]}");
    }

    @Test
    void deveRejeitarEmailAcimaDoLimite() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "4\",\"contatos\":[{\"telefone\":\"1\",\"email\":\""
                + "e".repeat(255) + "\"}]}");
    }

    @Test
    void deveRejeitarTelefoneAcimaDoLimite() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "5\",\"contatos\":[{\"telefone\":\""
                + "1".repeat(31) + "\",\"email\":\"a@x.com\"}]}");
    }

    @Test
    void deveRejeitarEmailSemFormatoValido() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "6\",\"contatos\":[{\"telefone\":\"1\",\"email\":\"nao-e-email\"}]}");
    }
}
