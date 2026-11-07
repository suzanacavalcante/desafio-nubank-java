package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import org.junit.jupiter.api.Test;
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
}
