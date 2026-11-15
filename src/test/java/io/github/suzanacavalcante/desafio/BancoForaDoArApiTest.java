package io.github.suzanacavalcante.desafio;

import io.github.suzanacavalcante.desafio.suporte.PostgresDeTeste;
import io.github.suzanacavalcante.desafio.suporte.ProxyTcp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critérios de aceite da #34, com a aplicação inteira e um PostgreSQL real atrás de um {@link ProxyTcp} que
 * "derruba" e "religa" o banco: com o banco fora, a resposta é <b>503 com Retry-After em poucos segundos</b> (não
 * o 500 depois de ~30 s), sem termo sensível; rotas que não usam o banco e a validação seguem rápidas; e a
 * aplicação <b>volta sozinha</b> quando o banco volta.
 * <p>
 * Contexto próprio (a URL passa pelo proxy e o {@code connection-timeout} é de 1 s para o teste ser rápido), então
 * não afeta os outros testes. É um único teste porque as etapas dependem do estado do banco.
 */
@SpringBootTest(properties = {"spring.datasource.hikari.connection-timeout=1000",
        "spring.datasource.hikari.data-source-properties.socketTimeout=1"})
@AutoConfigureMockMvc
class BancoForaDoArApiTest {

    /** "Poucos segundos": o esperado é ~1 s (timeouts de 1 s no teste); o padrão do Hikari seria 30 s por requisição. */
    private static final Duration LIMITE = Duration.ofSeconds(3);

    /** Tempo máximo para a aplicação voltar sozinha depois que o banco volta. */
    private static final Duration RECUPERACAO = Duration.ofSeconds(20);

    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static ProxyTcp proxy;

    @DynamicPropertySource
    static void bancoPeloProxy(DynamicPropertyRegistry registry) throws IOException {
        PostgreSQLContainer postgres = PostgresDeTeste.iniciar();
        proxy = new ProxyTcp(postgres.getHost(), postgres.getFirstMappedPort());
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:" + proxy.porta() + "/" + postgres.getDatabaseName());
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterAll
    static void derrubaProxy() throws IOException {
        proxy.close();
    }

    @Autowired
    MockMvc mockMvc;

    private ResultActions rapido(RequestBuilder requisicao) throws Exception {
        long inicio = System.nanoTime();
        ResultActions resultado = mockMvc.perform(requisicao);
        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);
        assertTrue(gasto.compareTo(LIMITE) < 0, "demorou " + gasto + " (limite " + LIMITE + ")");
        return resultado;
    }

    private void espera503(ResultActions r) throws Exception {
        r.andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Serviço indisponível"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("localhost"))))
                .andExpect(content().string(not(containsString(String.valueOf(proxy.porta())))))
                .andExpect(content().string(not(containsString("postgres"))))
                .andExpect(content().string(not(containsString("Hikari"))))
                .andExpect(content().string(not(containsString("select"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("trace"))));
    }

    @Test
    void bancoForaDoArDa503RapidoComRetryAfterEAAplicacaoVoltaSozinha() throws Exception {
        // banco no ar: tudo normal
        rapido(get("/clientes")).andExpect(status().isOk());

        proxy.cortar();

        // consultas e escritas: 503 em poucos segundos, nunca 500
        espera503(rapido(get("/clientes")));
        espera503(rapido(get("/clientes/1/contatos")));
        espera503(rapido(post("/clientes").contentType(JSON_UTF8)
                .content("{\"nome\":\"Ana\"}".getBytes(StandardCharsets.UTF_8))));

        // o que não depende do banco continua respondendo rápido
        rapido(get("/v3/api-docs")).andExpect(status().isOk());
        rapido(post("/clientes").contentType(JSON_UTF8).content("{\"nome\":\"\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
        rapido(get("/clientes?size=0")).andExpect(status().isBadRequest());
        rapido(get("/clientes/abc/contatos")).andExpect(status().isBadRequest());

        proxy.religar();

        // banco de volta: a aplicação se recupera sozinha, sem reiniciar
        esperaRecuperar();
        rapido(post("/clientes").contentType(JSON_UTF8)
                .content("{\"nome\":\"Volta\"}".getBytes(StandardCharsets.UTF_8))).andExpect(status().isCreated());
    }

    /**
     * Banco que <b>trava sem recusar</b> (conexões abertas, nenhuma resposta): sem {@code socketTimeout} a
     * requisição ficaria pendurada por minutos; com ele (1 s no teste, 10 s por padrão) vira 503 com Retry-After.
     */
    @Test
    void bancoQueTravaSemRecusarTambemDa503EmPoucosSegundos() throws Exception {
        rapido(get("/clientes")).andExpect(status().isOk());

        proxy.congelar();
        try {
            espera503(rapido(get("/clientes")));
            espera503(rapido(post("/clientes").contentType(JSON_UTF8)
                    .content("{\"nome\":\"Ana\"}".getBytes(StandardCharsets.UTF_8))));
            rapido(get("/clientes?size=0")).andExpect(status().isBadRequest());
        } finally {
            proxy.descongelar();
        }

        esperaRecuperar();
    }

    /**
     * Espera a aplicação voltar sozinha. Não é instantâneo: o Hikari espera um pouco entre as tentativas de recriar
     * conexões (backoff), então enquanto isso ainda sai 503 (nunca 500).
     */
    private void esperaRecuperar() throws Exception {
        long inicio = System.nanoTime();
        int status;
        do {
            status = rapido(get("/clientes")).andReturn().getResponse().getStatus();
            assertTrue(status == 200 || status == 503, "status inesperado na recuperação: " + status);
            if (status == 503) {
                Thread.sleep(250);
            }
        } while (status != 200 && Duration.ofNanos(System.nanoTime() - inicio).compareTo(RECUPERACAO) < 0);
        assertEquals(200, status, "a aplicação deveria voltar sozinha em até " + RECUPERACAO);
        rapido(get("/clientes")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
    }
}
