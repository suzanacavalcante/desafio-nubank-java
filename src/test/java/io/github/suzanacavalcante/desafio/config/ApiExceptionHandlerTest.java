package io.github.suzanacavalcante.desafio.config;

import io.github.suzanacavalcante.desafio.cliente.ClienteController;
import io.github.suzanacavalcante.desafio.cliente.ClienteNaoEncontradoException;
import io.github.suzanacavalcante.desafio.cliente.ClienteService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.util.stream.Stream;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    private static final String MENSAGEM_DO_DRIVER =
            "Connection to db-interno.exemplo:5432 refused. jdbc:postgresql://db-interno.exemplo:5432/cadastro";

    @ParameterizedTest
    @MethodSource("excecoesDeBancoIndisponivel")
    void bancoIndisponivelRetorna503ComRetryAfterSemVazarNada(RuntimeException excecao) throws Exception {
        when(service.criar(any())).thenThrow(excecao);

        postarCliente("{\"nome\":\"Ana\"}")
                .andExpect(problema(503))
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.title").value("Serviço indisponível"))
                .andExpect(jsonPath("$.detail")
                        .value("Serviço temporariamente indisponível. Tente novamente em instantes."))
                .andExpect(jsonPath("$.instance").value("/clientes"))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("db-interno"))))
                .andExpect(content().string(not(containsString("5432"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("trace"))));
    }

    static Stream<RuntimeException> excecoesDeBancoIndisponivel() {
        return Stream.of(
                new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
                        new IllegalStateException(MENSAGEM_DO_DRIVER)),
                new DataAccessResourceFailureException(MENSAGEM_DO_DRIVER, new IllegalStateException("causa")));
    }

    /** O cliente não vê o detalhe, mas quem opera precisa dele: o log guarda a exceção com o stack trace. */
    @Test
    void bancoIndisponivelContinuaNoLogComStackTrace() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            when(service.criar(any())).thenThrow(new CannotCreateTransactionException("falha",
                    new IllegalStateException(MENSAGEM_DO_DRIVER)));

            postarCliente("{\"nome\":\"Ana\"}").andExpect(status().isServiceUnavailable());

            assertEquals(1, logs.list.size());
            ILoggingEvent evento = logs.list.get(0);
            assertEquals(Level.ERROR, evento.getLevel());
            assertNotNull(evento.getThrowableProxy(), "o stack trace deve ir para o log");
            assertEquals(CannotCreateTransactionException.class.getName(), evento.getThrowableProxy().getClassName());
            assertEquals(IllegalStateException.class.getName(), evento.getThrowableProxy().getCause().getClassName());
        } finally {
            logger.detachAppender(logs);
        }
    }

    /** A 1ª requisição depois da queda pega conexão morta do pool: consulta e rollback falham (JpaSystemException). */
    @Test
    void conexaoMortaDoPoolNaPrimeiraRequisicaoTambemVira503() throws Exception {
        when(service.criar(any())).thenThrow(new JpaSystemException(new RuntimeException(
                "Unable to rollback against JDBC Connection", new SQLException("Connection is closed"))));

        postarCliente("{\"nome\":\"Ana\"}")
                .andExpect(problema(503))
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().string(not(containsString("rollback"))))
                .andExpect(content().string(not(containsString("closed"))));
    }

    /** Erro de dados (SQLState 23505) não é indisponibilidade: 500 genérico, sem Retry-After. */
    @Test
    void erroDeDadosDoBancoNaoVira503() throws Exception {
        when(service.criar(any())).thenThrow(new JpaSystemException(new RuntimeException(
                new SQLException("duplicate key", "23505"))));

        postarCliente("{\"nome\":\"Ana\"}")
                .andExpect(problema(500))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    /** Só a indisponibilidade vira 503: erro de dados continua sendo 500 genérico (não é "tente depois"). */
    @Test
    void outroErroDeAcessoADadosContinuaSendo500() throws Exception {
        when(service.criar(any())).thenThrow(new DataIntegrityViolationException("duplicado"));

        postarCliente("{\"nome\":\"Ana\"}")
                .andExpect(problema(500))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    /** Sem este valor curto o Hikari espera 30 s por requisição com o banco fora do ar (padrão do Hikari). */
    @Test
    void timeoutDeConexaoPadraoEDe3Segundos() {
        assertEquals("3000", environment.getProperty("spring.datasource.hikari.connection-timeout"));
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
