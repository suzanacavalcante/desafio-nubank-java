package io.github.suzanacavalcante.desafio.config;

import io.github.suzanacavalcante.desafio.cliente.ClienteController;
import io.github.suzanacavalcante.desafio.cliente.ClienteNaoEncontradoException;
import io.github.suzanacavalcante.desafio.cliente.ClienteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Formato único de erro (issue #11): toda resposta de erro é um {@code ProblemDetail}
 * ({@code application/problem+json}), sem vazar detalhes internos. Sem banco: o service é um mock.
 */
@WebMvcTest(ClienteController.class)
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ClienteService service;

    @Autowired
    Environment environment;

    private ResultActions postarCliente(String json) throws Exception {
        return mockMvc.perform(post("/clientes")
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static ResultMatcher problema(int status) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(status).match(result);
        };
    }

    @Test
    void clienteNaoEncontradoRetorna404ComDetalheEInstancia() throws Exception {
        when(service.contatosDoCliente(5L)).thenThrow(new ClienteNaoEncontradoException(5L));

        mockMvc.perform(get("/clientes/5/contatos"))
                .andExpect(problema(404))
                .andExpect(jsonPath("$.title").value("Cliente não encontrado"))
                .andExpect(jsonPath("$.detail").value("Cliente 5 não encontrado"))
                .andExpect(jsonPath("$.instance").value("/clientes/5/contatos"));
    }

    @Test
    void validacaoRetorna400ComMapaCampoParaMensagem() throws Exception {
        postarCliente("{\"nome\":\"\",\"contatos\":[{\"telefone\":\"1\",\"email\":\"lixo\"}]}")
                .andExpect(problema(400))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros.nome").value("não deve estar em branco"))
                .andExpect(jsonPath("$.erros['contatos[0].email']").isNotEmpty());
    }

    @Test
    void validacaoNaoEcoaOValorRejeitado() throws Exception {
        String valor = "x".repeat(200);

        postarCliente("{\"nome\":\"" + valor + "\"}")
                .andExpect(problema(400))
                .andExpect(jsonPath("$.erros.nome").isNotEmpty())
                .andExpect(content().string(not(containsString("xxxxxxxxxx"))));
    }

    @Test
    void mensagensDeValidacaoSaemEmPortuguesComIdiomaFixo() throws Exception {
        mockMvc.perform(post("/clientes")
                        .header("Accept-Language", "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \"}"))
                .andExpect(jsonPath("$.erros.nome").value("não deve estar em branco"));
    }

    @Test
    void jsonMalformadoRetorna400SemDetalhesDoParser() throws Exception {
        postarCliente("{\"nome\": ")
                .andExpect(problema(400))
                .andExpect(jsonPath("$.title").value("Corpo inválido"))
                .andExpect(content().string(not(containsString("Unexpected"))))
                .andExpect(content().string(not(containsString("JsonParse"))));
    }

    @Test
    void tipoErradoNoCorpoRetorna400DizendoSoOCampo() throws Exception {
        postarCliente("{\"nome\":{\"a\":1}}")
                .andExpect(problema(400))
                .andExpect(jsonPath("$.detail").value("Tipo incorreto no campo 'nome'."));
    }

    @Test
    void tipoErradoEmContatoAninhadoDizOCaminhoSemOValor() throws Exception {
        postarCliente("{\"nome\":\"Ana\",\"contatos\":[{\"telefone\":{\"x\":\"segredo-do-cliente\"},\"email\":\"a@x.com\"}]}")
                .andExpect(problema(400))
                .andExpect(jsonPath("$.detail").value("Tipo incorreto no campo 'contatos[0].telefone'."))
                .andExpect(content().string(not(containsString("segredo-do-cliente"))));
    }

    @Test
    void idDeTipoErradoRetorna400SemEcoarOValorNoDetalhe() throws Exception {
        mockMvc.perform(get("/clientes/abc/contatos"))
                .andExpect(problema(400))
                .andExpect(jsonPath("$.detail").value("Valor inválido para o parâmetro 'id'."))
                .andExpect(jsonPath("$.detail", not(containsString("abc"))))
                .andExpect(jsonPath("$.title", not(containsString("abc"))));
    }

    @Test
    void erroInesperadoRetorna500GenericoSemVazarNada() throws Exception {
        when(service.criar(any())).thenThrow(new IllegalStateException("segredo-interno-do-banco"));

        postarCliente("{\"nome\":\"Ana\"}")
                .andExpect(problema(500))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value("Erro interno. Tente novamente mais tarde."))
                .andExpect(content().string(not(containsString("segredo-interno"))))
                .andExpect(content().string(not(containsString("IllegalState"))))
                .andExpect(content().string(not(containsString("trace"))));
    }

    @Test
    void metodoNaoSuportadoTemOMesmoFormato() throws Exception {
        mockMvc.perform(delete("/clientes")).andExpect(problema(405));
    }

    @Test
    void rotaInexistenteTemOMesmoFormato() throws Exception {
        mockMvc.perform(get("/nao-existe")).andExpect(problema(404));
    }

    /**
     * Páginas de erro fora do MVC (ErrorController do container) não passam por este handler; a
     * configuração garante que nelas também não haja stack trace, mensagem nem exceção.
     */
    @Test
    void paginaDeErroDoContainerNaoExpoeDetalhes() {
        assertEquals("never", environment.getProperty("server.error.include-stacktrace"));
        assertEquals("never", environment.getProperty("server.error.include-message"));
        assertEquals("never", environment.getProperty("server.error.include-binding-errors"));
        assertEquals("false", environment.getProperty("server.error.include-exception"));
    }

    /**
     * Um campo com vários motivos (151 espaços viola {@code @NotBlank} e {@code @Size}) junta as mensagens
     * em ordem fixa. O Hibernate Validator não garante a ordem das violações, então repete a chamada.
     */
    @Test
    void variosMotivosNoMesmoCampoSaemJuntosEmOrdemFixa() throws Exception {
        for (int i = 0; i < 30; i++) {
            postarCliente("{\"nome\":\"" + " ".repeat(151) + "\"}")
                    .andExpect(problema(400))
                    .andExpect(jsonPath("$.erros.nome")
                            .value("não deve estar em branco; tamanho deve ser entre 0 e 150"));
        }
    }
}
