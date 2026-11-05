package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.cliente.Cliente;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContatoMapperTest {

    @Test
    void toEntityDeContatoRequestCopiaTelefoneEEmailSemAssociarCliente() {
        Contato contato = ContatoMapper.toEntity(new ContatoRequest("48123", "a@x.com"));

        assertEquals("48123", contato.getTelefone());
        assertEquals("a@x.com", contato.getEmail());
        assertNull(contato.getId());
        assertNull(contato.getCliente());
    }

    @Test
    void toEntityDeNovoContatoRequestNaoAssociaOCliente() {
        Contato contato = ContatoMapper.toEntity(new NovoContatoRequest(5L, "48987", "b@x.com"));

        assertEquals("48987", contato.getTelefone());
        assertEquals("b@x.com", contato.getEmail());
        assertNull(contato.getCliente());
    }

    @Test
    void toResponseIncluiIdEClienteId() {
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", 7L);
        Contato contato = ContatoMapper.toEntity(new ContatoRequest("48123", "a@x.com"));
        ReflectionTestUtils.setField(contato, "id", 11L);
        cliente.adicionarContato(contato);

        assertEquals(new ContatoResponse(11L, "48123", "a@x.com", 7L), ContatoMapper.toResponse(contato));
    }

    @Test
    void toResponseDeContatoSemClienteTemClienteIdNulo() {
        Contato contato = ContatoMapper.toEntity(new ContatoRequest("48123", "a@x.com"));

        assertNull(ContatoMapper.toResponse(contato).clienteId());
    }
}
