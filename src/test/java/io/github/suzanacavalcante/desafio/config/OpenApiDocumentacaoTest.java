package io.github.suzanacavalcante.desafio.config;

import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documentação OpenAPI (issue #15): a Swagger UI e o documento {@code /v3/api-docs} listam os 4 endpoints com
 * seus códigos de resposta e exemplos, e os <b>exemplos de erro conferem com o que a API realmente devolve</b>
 * (para a documentação não se afastar do comportamento).
 */
@UsaPostgresDeTeste
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentacaoTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    MockMvc mockMvc;

    private JsonNode doc;

    @BeforeEach
    void carregarDocumento() throws Exception {
        String corpo = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        doc = JSON.readTree(corpo);
    }

    private JsonNode operacao(String metodo, String caminho) {
        JsonNode op = doc.path("paths").path(caminho).path(metodo);
        assertFalse(op.isMissingNode(), metodo + " " + caminho + " não está na documentação");
        return op;
    }

    private static List<String> codigos(JsonNode op) {
        List<String> codigos = new ArrayList<>();
        op.path("responses").propertyNames().forEach(codigos::add);
        return new TreeSet<>(codigos).stream().toList();
    }

    @Test
    void informacoesGeraisTemTituloEVersao() {
        assertEquals("API de Clientes e Contatos", doc.path("info").path("title").asString());
        assertEquals("1.0.0", doc.path("info").path("version").asString());
    }

    @Test
    void listaExatamenteOsQuatroEndpoints() {
        int operacoes = 0;
        for (String caminho : doc.path("paths").propertyNames()) {
            operacoes += doc.path("paths").path(caminho).size();
        }
        assertEquals(4, operacoes);
        operacao("post", "/clientes");
        operacao("get", "/clientes");
        operacao("post", "/contatos");
        operacao("get", "/clientes/{id}/contatos");
    }

    @Test
    void cadaEndpointDocumentaSeusCodigosDeResposta() {
        assertEquals(List.of("201", "400"), codigos(operacao("post", "/clientes")));
        assertEquals(List.of("200"), codigos(operacao("get", "/clientes")));
        assertEquals(List.of("201", "400", "404"), codigos(operacao("post", "/contatos")));
        assertEquals(List.of("200", "400", "404"), codigos(operacao("get", "/clientes/{id}/contatos")));
    }

    @Test
    void cadaEndpointTemResumoETag() {
        for (String[] ep : new String[][] {{"post", "/clientes"}, {"get", "/clientes"}, {"post", "/contatos"},
                {"get", "/clientes/{id}/contatos"}}) {
            JsonNode op = operacao(ep[0], ep[1]);
            assertFalse(op.path("summary").asString().isBlank(), ep[0] + " " + ep[1] + " sem resumo");
            assertEquals(1, op.path("tags").size(), ep[0] + " " + ep[1] + " sem tag");
        }
    }

    @Test
    void respostasDeErroUsamProblemJsonComExemploESchemaProblema() {
        for (String[] ep : new String[][] {{"post", "/clientes", "400"}, {"post", "/contatos", "400"},
                {"post", "/contatos", "404"}, {"get", "/clientes/{id}/contatos", "400"},
                {"get", "/clientes/{id}/contatos", "404"}}) {
            JsonNode conteudo = operacao(ep[0], ep[1]).path("responses").path(ep[2]).path("content").path(PROBLEM_JSON);
            String onde = ep[0] + " " + ep[1] + " " + ep[2];
            assertFalse(conteudo.isMissingNode(), onde + " sem application/problem+json");
            assertTrue(conteudo.path("schema").path("$ref").asString().endsWith("/Problema"), onde + " sem schema Problema");
            assertFalse(conteudo.path("examples").isMissingNode(), onde + " sem exemplo");
        }
    }

    @Test
    void dtosTemExemplosELimitesDaValidacao() {
        JsonNode nome = doc.path("components").path("schemas").path("ClienteRequest").path("properties").path("nome");
        assertEquals("Maria Silva", nome.path("example").asString());
        assertEquals(150, nome.path("maxLength").asInt(), "o limite vem das anotações de validação");
        JsonNode contatoRequest = doc.path("components").path("schemas").path("ContatoRequest");
        assertEquals("11999990000", contatoRequest.path("properties").path("telefone").path("example").asString());
        assertEquals("maria@exemplo.com", contatoRequest.path("properties").path("email").path("example").asString());
        JsonNode novoContato = doc.path("components").path("schemas").path("NovoContatoRequest");
        assertEquals(1, novoContato.path("properties").path("clienteId").path("example").asInt());
        assertEquals("10", doc.path("components").path("schemas").path("ContatoResponse")
                .path("properties").path("id").path("example").asString());
    }

    /** O exemplo do 400 de validação tem os mesmos campos que a resposta real. */
    @Test
    void exemploDeValidacaoConfereComARespostaReal() throws Exception {
        String real = mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"contatos\":[{\"telefone\":\"1\",\"email\":\"lixo\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith(PROBLEM_JSON)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode exemplo = JSON.readTree(ExemplosDeErro.VALIDACAO_400);
        JsonNode resposta = JSON.readTree(real);

        assertEquals(chaves(exemplo), chaves(resposta));
        assertEquals(chaves(exemplo.path("erros")), chaves(resposta.path("erros")));
        assertEquals(exemplo.path("title").asString(), resposta.path("title").asString());
        assertEquals(exemplo.path("status").asInt(), resposta.path("status").asInt());
        assertEquals(exemplo.path("erros").path("nome").asString(), resposta.path("erros").path("nome").asString());
    }

    @Test
    void exemploDeClienteNaoEncontradoConfereComARespostaReal() throws Exception {
        String real = mockMvc.perform(get("/clientes/99/contatos"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode exemplo = JSON.readTree(ExemplosDeErro.CLIENTE_NAO_ENCONTRADO_404);
        JsonNode resposta = JSON.readTree(real);

        assertEquals(chaves(exemplo), chaves(resposta));
        assertEquals(exemplo.path("title").asString(), resposta.path("title").asString());
        assertEquals(exemplo.path("detail").asString(), resposta.path("detail").asString());
        assertEquals(exemplo.path("instance").asString(), resposta.path("instance").asString());
    }

    @Test
    void exemploDeIdInvalidoConfereComARespostaReal() throws Exception {
        String real = mockMvc.perform(get("/clientes/abc/contatos"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode exemplo = JSON.readTree(ExemplosDeErro.ID_INVALIDO_400);
        JsonNode resposta = JSON.readTree(real);

        assertEquals(chaves(exemplo), chaves(resposta));
        assertEquals(exemplo.path("detail").asString(), resposta.path("detail").asString());
    }

    @Test
    void exemploDeCorpoInvalidoConfereComARespostaReal() throws Exception {
        String real = mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"abc\",\"telefone\":\"1\",\"email\":\"a@x.com\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode exemplo = JSON.readTree(ExemplosDeErro.CORPO_INVALIDO_400);
        JsonNode resposta = JSON.readTree(real);

        assertEquals(chaves(exemplo), chaves(resposta));
        assertEquals(exemplo.path("detail").asString(), resposta.path("detail").asString());
    }

    @Test
    void swaggerUiEstaEmSwaggerUiHtml() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/swagger-ui/index.html")));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private static List<String> chaves(JsonNode no) {
        List<String> chaves = new ArrayList<>();
        no.propertyNames().forEach(chaves::add);
        return new TreeSet<>(chaves).stream().toList();
    }
}
