package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de {@code POST /clientes}, sem banco (o service é um mock). */
@WebMvcTest(ClienteController.class)
class ClienteControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ClienteService service;

    @Test
    void clienteSoComNomeRetorna201ComContatosVazio() throws Exception {
        when(service.criar(any())).thenReturn(new ClienteResponse(1L, "Ana", List.of()));

        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Ana\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Ana"))
                .andExpect(jsonPath("$.contatos").isArray())
                .andExpect(jsonPath("$.contatos").isEmpty());

        ArgumentCaptor<ClienteRequest> captor = ArgumentCaptor.forClass(ClienteRequest.class);
        verify(service).criar(captor.capture());
        assertEquals("Ana", captor.getValue().nome());
        assertNull(captor.getValue().contatos());
    }

    @Test
    void clienteComDoisContatosRetorna201ComOsContatos() throws Exception {
        when(service.criar(any())).thenReturn(new ClienteResponse(1L, "Ana", List.of(
                new ContatoResponse(10L, "48123", "a@x.com", 1L),
                new ContatoResponse(11L, "48987", "b@x.com", 1L))));

        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome":"Ana","contatos":[
                          {"telefone":"48123","email":"a@x.com"},
                          {"telefone":"48987","email":"b@x.com"}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contatos.length()").value(2))
                .andExpect(jsonPath("$.contatos[0].telefone").value("48123"))
                .andExpect(jsonPath("$.contatos[1].clienteId").value(1));

        ArgumentCaptor<ClienteRequest> captor = ArgumentCaptor.forClass(ClienteRequest.class);
        verify(service).criar(captor.capture());
        assertEquals(List.of(new ContatoRequest("48123", "a@x.com"), new ContatoRequest("48987", "b@x.com")),
                captor.getValue().contatos());
    }

    @Test
    void clienteComContatosVaziosAceitaListaVazia() throws Exception {
        when(service.criar(any())).thenReturn(new ClienteResponse(1L, "Ana", List.of()));

        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"contatos\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void listarRetorna200ComClientesEContatos() throws Exception {
        when(service.listar()).thenReturn(List.of(
                new ClienteResponse(1L, "Ana", List.of(new ContatoResponse(10L, "48123", "a@x.com", 1L))),
                new ClienteResponse(2L, "Bia", List.of())));

        mockMvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Ana"))
                .andExpect(jsonPath("$[0].contatos[0].telefone").value("48123"))
                .andExpect(jsonPath("$[0].contatos[0].clienteId").value(1))
                .andExpect(jsonPath("$[1].contatos").isArray())
                .andExpect(jsonPath("$[1].contatos").isEmpty());
    }

    @Test
    void listarSemClientesRetorna200ComListaVazia() throws Exception {
        when(service.listar()).thenReturn(List.of());

        mockMvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void jsonMalformadoRetorna400ENaoChegaAoService() throws Exception {
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("{nome:"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void contatosDoClienteRetorna200ComALista() throws Exception {
        when(service.contatosDoCliente(1L)).thenReturn(List.of(
                new ContatoResponse(10L, "48123", "a@x.com", 1L),
                new ContatoResponse(11L, "48987", "b@x.com", 1L)));

        mockMvc.perform(get("/clientes/1/contatos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].clienteId").value(1))
                .andExpect(jsonPath("$[1].telefone").value("48987"));
    }

    @Test
    void contatosDoClienteSemContatosRetorna200ComArrayVazio() throws Exception {
        when(service.contatosDoCliente(1L)).thenReturn(List.of());

        mockMvc.perform(get("/clientes/1/contatos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void idNaoNumericoRetorna400SemChamarOService() throws Exception {
        mockMvc.perform(get("/clientes/abc/contatos"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
