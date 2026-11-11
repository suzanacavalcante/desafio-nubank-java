package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validação de entrada de {@code POST /clientes} de ponta a ponta (issue #10): entrada inválida responde
 * <b>400</b> e <b>nada</b> chega ao banco. Nasceu como os testes de regressão de QA (relatório de
 * 2026-11-07, com {@code @Tag("bug-conhecido")}); a tag saiu quando a validação foi implementada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
class ClienteApiValidacaoTest {

    private static final String MARCA = "qa-validacao-";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * Rede de segurança: se a validação falhar, o dado inválido seria gravado. Apaga só o que os testes
     * podem ter criado (nomes com a marca e os dois valores exatos sem marca possível: vazio e só espaços).
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
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from clientes where nome like ? or nome in ('', '   ')", Integer.class, MARCA + "%"),
                "entrada inválida não pode gravar cliente");
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

    private String contatos(int quantidade) {
        return IntStream.rangeClosed(1, quantidade)
                .mapToObj(i -> "{\"telefone\":\"" + i + "\",\"email\":\"c" + i + "@x.com\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void deveRejeitarMaisContatosQueOLimite() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "7\",\"contatos\":" + contatos(ClienteRequest.CONTATOS_MAX + 1) + "}");
    }

    @Test
    void aceitaListaDeContatosNoLimiteENomeNoLimite() throws Exception {
        String nome = (MARCA + "8-").concat("a".repeat(Cliente.NOME_MAX - (MARCA + "8-").length()));
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"contatos\":" + contatos(ClienteRequest.CONTATOS_MAX) + "}"))
                .andExpect(status().isCreated());
    }

    /** U+0000 (escrito no JSON como barra invertida + u0000): o PostgreSQL o recusa, e viraria 500. */
    @Test
    void deveRejeitarCaractereNuloNoNome() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "9\\u0000b\"}");
    }

    @Test
    void deveRejeitarCaractereNuloNoTelefoneDeContatoAninhado() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "10\",\"contatos\":[{\"telefone\":\"1\\u00002\",\"email\":\"a@x.com\"}]}");
    }

    /** Surrogate UTF-16 solto (BUG-5 do QA): o driver o gravaria como "?", diferente do que a resposta devolve. */
    @Test
    void deveRejeitarSurrogateSoltoNoNome() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "11\\uD800b\"}");
    }

    @Test
    void deveRejeitarSurrogateSoltoNoTelefoneDeContatoAninhado() throws Exception {
        esperar400("{\"nome\":\"" + MARCA + "12\",\"contatos\":[{\"telefone\":\"1\\uDC00\",\"email\":\"a@x.com\"}]}");
    }

    /** O par de surrogates válido (emoji) continua aceito e é gravado fielmente. */
    @Test
    void aceitaEmojiNoNomeEGravaFielmente() throws Exception {
        String nome = MARCA + "13-\\uD83D\\uDE00";
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated());

        assertEquals(MARCA + "13-" + new String(Character.toChars(0x1F600)),
                jdbc.queryForObject("select nome from clientes where nome like ?", String.class, MARCA + "13-%"));
    }

    /** Formato do erro no contexto completo: ProblemDetail com o campo e a mensagem em português. */
    @Test
    void respostaDeValidacaoTemOCampoEAMensagemEmPortugues() throws Exception {
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "en-US")
                        .content("{\"nome\":\"" + MARCA + "14\",\"contatos\":[{\"telefone\":\"1\",\"email\":\"lixo\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.erros['contatos[0].email']").value("deve ser um endereço de e-mail bem formado"));
    }
}
