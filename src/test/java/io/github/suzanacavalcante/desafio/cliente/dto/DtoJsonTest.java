package io.github.suzanacavalcante.desafio.cliente.dto;

import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Garante o contrato JSON da API com o {@code JsonMapper} <b>configurado pelo Spring Boot</b> (Jackson 3),
 * o mesmo que os controllers usam, em vez de um mapper criado à mão. {@code @JsonTest} sobe só a
 * configuração de JSON, sem web nem banco.
 */
@JsonTest
class DtoJsonTest {

    @Autowired
    JsonMapper json;

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

    /**
     * O mapper vem do Boot (respeita {@code spring.jackson.*}); campo desconhecido no corpo é ignorado, não vira
     * erro. No Jackson 3 isso já é o padrão; o que o teste prova é a configuração do Boot, não o padrão do Jackson.
     */
    @Test
    void campoDesconhecidoNoCorpoEIgnorado() {
        ClienteRequest request = json.readValue("{\"nome\":\"Ana\",\"extra\":123}", ClienteRequest.class);

        assertEquals("Ana", request.nome());
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
