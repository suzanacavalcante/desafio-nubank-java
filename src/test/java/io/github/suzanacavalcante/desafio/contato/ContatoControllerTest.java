package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de {@code POST /contatos}, sem banco (o service é um mock). */
@WebMvcTest(ContatoController.class)
class ContatoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ContatoService service;

    @Test
    void contatoParaClienteExistenteRetorna201ComOContatoCriado() throws Exception {
        when(service.criar(any())).thenReturn(new ContatoResponse(11L, "48123", "a@x.com", 7L));

        mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":7,\"telefone\":\"48123\",\"email\":\"a@x.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.telefone").value("48123"))
                .andExpect(jsonPath("$.email").value("a@x.com"))
                .andExpect(jsonPath("$.clienteId").value(7));

        ArgumentCaptor<NovoContatoRequest> captor = ArgumentCaptor.forClass(NovoContatoRequest.class);
        verify(service).criar(captor.capture());
        assertEquals(new NovoContatoRequest(7L, "48123", "a@x.com"), captor.getValue());
    }

    @Test
    void jsonMalformadoRetorna400ENaoChegaAoService() throws Exception {
        mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON).content("{clienteId:"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"telefone\":\"1\",\"email\":\"a@x.com\"}",
            "{\"clienteId\":null,\"telefone\":\"1\",\"email\":\"a@x.com\"}",
            "{\"clienteId\":7,\"email\":\"a@x.com\"}",
            "{\"clienteId\":7,\"telefone\":\"\",\"email\":\"a@x.com\"}",
            "{\"clienteId\":7,\"telefone\":\"   \",\"email\":\"a@x.com\"}",
            "{\"clienteId\":7,\"telefone\":\"1\"}",
            "{\"clienteId\":7,\"telefone\":\"1\",\"email\":\"\"}",
            "{\"clienteId\":7,\"telefone\":\"1\",\"email\":\"nao-e-email\"}",
            "{\"clienteId\":7,\"telefone\":\"1\",\"email\":\"a@x\u0020.com\"}"})
    void corpoInvalidoRetorna400ENaoChegaAoService(String json) throws Exception {
        mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    /** E-mail de formato válido (parte local até 64 caracteres, rótulos até 63) com exatamente {@code tamanho}. */
    private static String emailComTamanho(int tamanho) {
        String prefixo = "a".repeat(60) + "@" + ("b".repeat(60) + ".").repeat(3);
        return prefixo + "c".repeat(tamanho - prefixo.length());
    }

    @Test
    void telefoneEEmailAcimaDoLimiteRetornam400() throws Exception {
        String telefoneLongo = "{\"clienteId\":7,\"telefone\":\"" + "1".repeat(Contato.TELEFONE_MAX + 1)
                + "\",\"email\":\"a@x.com\"}";
        String emailLongo = "{\"clienteId\":7,\"telefone\":\"1\",\"email\":\"" + emailComTamanho(Contato.EMAIL_MAX + 1)
                + "\"}";

        for (String json : new String[] {telefoneLongo, emailLongo}) {
            mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void valoresNoLimiteExatoSaoAceitos() throws Exception {
        when(service.criar(any())).thenReturn(new ContatoResponse(1L, "t", "e", 7L));
        String json = "{\"clienteId\":7,\"telefone\":\"" + "1".repeat(Contato.TELEFONE_MAX)
                + "\",\"email\":\"" + emailComTamanho(Contato.EMAIL_MAX) + "\"}";

        mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    /** U+0000 chega ao PostgreSQL como erro 500; a validação precisa barrá-lo antes (BUG-4 do QA, PR #32). */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"clienteId\":7,\"telefone\":\"1\\u00002\",\"email\":\"a@x.com\"}",
            "{\"clienteId\":7,\"telefone\":\"\\u0000\",\"email\":\"a@x.com\"}"})
    void telefoneComCaractereNuloRetorna400(String json) throws Exception {
        mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void telefoneComSurrogateSoltoRetorna400EComParValidoRetorna201() throws Exception {
        mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":7,\"telefone\":\"1\\uD800\",\"email\":\"a@x.com\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);

        when(service.criar(any())).thenReturn(new ContatoResponse(1L, "t", "e", 7L));
        mockMvc.perform(post("/contatos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":7,\"telefone\":\"\\uD83D\\uDE00\",\"email\":\"a@x.com\"}"))
                .andExpect(status().isCreated());
    }
}
