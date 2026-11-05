package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.contato.Contato;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteMapperTest {

    @Test
    void toEntityCriaClienteComContatosSincronizados() {
        var request = new ClienteRequest("Ana", List.of(
                new ContatoRequest("48123", "a@x.com"),
                new ContatoRequest("48987", "b@x.com")));

        Cliente cliente = ClienteMapper.toEntity(request);

        assertEquals("Ana", cliente.getNome());
        assertNull(cliente.getId());
        assertEquals(2, cliente.getContatos().size());
        cliente.getContatos().forEach(c -> assertSame(cliente, c.getCliente()));
        assertEquals("48123", cliente.getContatos().get(0).getTelefone());
        assertEquals("b@x.com", cliente.getContatos().get(1).getEmail());
    }

    @Test
    void toEntitySemContatosOuComListaVaziaCriaClienteSemContatos() {
        assertTrue(ClienteMapper.toEntity(new ClienteRequest("Ana", null)).getContatos().isEmpty());
        assertTrue(ClienteMapper.toEntity(new ClienteRequest("Ana", List.of())).getContatos().isEmpty());
    }

    @Test
    void toResponseCopiaDadosEContatosComClienteId() {
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", 7L);
        cliente.setNome("Ana");
        Contato contato = new Contato();
        ReflectionTestUtils.setField(contato, "id", 11L);
        contato.setTelefone("48123");
        contato.setEmail("a@x.com");
        cliente.adicionarContato(contato);

        ClienteResponse response = ClienteMapper.toResponse(cliente);

        assertEquals(7L, response.id());
        assertEquals("Ana", response.nome());
        assertEquals(List.of(new ContatoResponse(11L, "48123", "a@x.com", 7L)), response.contatos());
    }

    @Test
    void toResponseDeClienteSemContatosDevolveListaVaziaNaoNula() {
        Cliente cliente = new Cliente();
        cliente.setNome("Ana");

        assertTrue(ClienteMapper.toResponse(cliente).contatos().isEmpty());
    }

    @Test
    void listaDeContatosDaResponseNaoPodeSerAlterada() {
        ClienteResponse response = ClienteMapper.toResponse(new Cliente());

        assertThrows(UnsupportedOperationException.class,
                () -> response.contatos().add(new ContatoResponse(1L, "1", "a@x.com", 1L)));
    }
}
