package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.contato.Contato;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
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

    /** Validação de {@code POST /clientes} sem banco (o service nunca é chamado). */
    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"nome\":null}",
            "{\"nome\":\"\"}",
            "{\"nome\":\"   \"}",
            "{\"nome\":\"Ana\\u0000\"}",
            "{\"nome\":\"Ana\\uD800\"}",
            "{\"nome\":\"Ana\",\"contatos\":[null]}",
            "{\"nome\":\"Ana\",\"contatos\":[{\"telefone\":\"1\"}]}",
            "{\"nome\":\"Ana\",\"contatos\":[{\"telefone\":\"1\\u0000\",\"email\":\"a@x.com\"}]}",
            "{\"nome\":\"Ana\",\"contatos\":[{\"telefone\":\"1\",\"email\":\"lixo\"}]}"})
    void corpoInvalidoRetorna400ENaoChegaAoService(String json) throws Exception {
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void maisContatosQueOLimiteRetorna400ENaoChegaAoService() throws Exception {
        String contatos = String.join(",", Collections.nCopies(ClienteRequest.CONTATOS_MAX + 1,
                "{\"telefone\":\"1\",\"email\":\"a@x.com\"}"));

        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"contatos\":[" + contatos + "]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // ---- 404 e formato do erro ----

    @Test
    void contatosDeClienteInexistenteRetorna404ComProblemDetail() throws Exception {
        when(service.contatosDoCliente(99L)).thenThrow(new ClienteNaoEncontradoException(99L));

        mockMvc.perform(get("/clientes/99/contatos"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente 99 não encontrado"));
    }

    @Test
    void jsonMalformadoTemOFormatoDeErroPadrao() throws Exception {
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---- uma regra de validação por teste (issue #10): nome ----

    @Test
    void nomeComExatamenteOLimiteEAceito() throws Exception {
        when(service.criar(any())).thenReturn(new ClienteResponse(1L, "n", List.of()));

        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "a".repeat(Cliente.NOME_MAX) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void nomeAcimaDoLimiteRetorna400ComOCampoNoErro() throws Exception {
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "a".repeat(Cliente.NOME_MAX + 1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").isNotEmpty());

        verifyNoInteractions(service);
    }

    // ---- contatos aninhados: cada regra de ContatoRequest, com o caminho do campo no erro ----

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"email\":\"a@x.com\"} | contatos[0].telefone",
            "{\"telefone\":\"\",\"email\":\"a@x.com\"} | contatos[0].telefone",
            "{\"telefone\":\"   \",\"email\":\"a@x.com\"} | contatos[0].telefone",
            "{\"telefone\":\"1\"} | contatos[0].email",
            "{\"telefone\":\"1\",\"email\":\"\"} | contatos[0].email",
            "{\"telefone\":\"1\",\"email\":\"nao-e-email\"} | contatos[0].email"})
    void contatoInvalidoRetorna400ApontandoOCampoAninhado(String contato, String campo) throws Exception {
        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"contatos\":[" + contato.trim() + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['" + campo.trim() + "']").isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void telefoneEEmailAcimaDoLimiteDoContatoRetornam400() throws Exception {
        String telefoneLongo = "{\"telefone\":\"" + "1".repeat(Contato.TELEFONE_MAX + 1) + "\",\"email\":\"a@x.com\"}";
        String emailLongo = "{\"telefone\":\"1\",\"email\":\"" + emailComTamanho(Contato.EMAIL_MAX + 1) + "\"}";

        for (String contato : new String[] {telefoneLongo, emailLongo}) {
            mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Ana\",\"contatos\":[" + contato + "]}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void listaComExatamenteOLimiteDeContatosEAceita() throws Exception {
        when(service.criar(any())).thenReturn(new ClienteResponse(1L, "Ana", List.of()));
        String contatos = String.join(",", Collections.nCopies(ClienteRequest.CONTATOS_MAX,
                "{\"telefone\":\"1\",\"email\":\"a@x.com\"}"));

        mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"contatos\":[" + contatos + "]}"))
                .andExpect(status().isCreated());
    }

    /** E-mail de formato válido (parte local até 64 caracteres, rótulos até 63) com exatamente {@code tamanho}. */
    private static String emailComTamanho(int tamanho) {
        String prefixo = "a".repeat(60) + "@" + ("b".repeat(60) + ".").repeat(3);
        return prefixo + "c".repeat(tamanho - prefixo.length());
    }
}
