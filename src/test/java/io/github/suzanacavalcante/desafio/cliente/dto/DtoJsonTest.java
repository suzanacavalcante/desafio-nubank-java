package io.github.suzanacavalcante.desafio.cliente.dto;

import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Garante o contrato JSON da API (Jackson 3, o padrão do Spring Boot 4) sem subir o Spring. */
class DtoJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void clienteRequestSemContatosDesserializaComListaNula() {
        ClienteRequest request = json.readValue("{\"nome\":\"Ana\"}", ClienteRequest.class);

        assertEquals("Ana", request.nome());
        assertNull(request.contatos());
    }

    @Test
    void clienteRequestComContatosDesserializa() {
        ClienteRequest request = json.readValue(
                "{\"nome\":\"Ana\",\"contatos\":[{\"telefone\":\"48123\",\"email\":\"a@x.com\"}]}",
                ClienteRequest.class);

        assertEquals(1, request.contatos().size());
        assertEquals("48123", request.contatos().get(0).telefone());
    }

    @Test
    void novoContatoRequestUsaCampoClienteId() {
        NovoContatoRequest request = json.readValue(
                "{\"clienteId\":3,\"telefone\":\"1\",\"email\":\"a@x.com\"}", NovoContatoRequest.class);

        assertEquals(3L, request.clienteId());
    }

    @Test
    void clienteResponseSerializaComOsNomesDoContrato() {
        var response = new ClienteResponse(1L, "Ana",
                List.of(new ContatoResponse(2L, "48123", "a@x.com", 1L)));

        assertEquals(
                "{\"id\":1,\"nome\":\"Ana\",\"contatos\":[{\"id\":2,\"telefone\":\"48123\","
                        + "\"email\":\"a@x.com\",\"clienteId\":1}]}",
                json.writeValueAsString(response));
    }
}
