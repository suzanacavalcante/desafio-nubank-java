package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.contato.Contato;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteTest {

    private static Contato contato(String telefone, String email) {
        Contato c = new Contato();
        c.setTelefone(telefone);
        c.setEmail(email);
        return c;
    }

    @Test
    void novoClienteNaoTemContatos() {
        assertTrue(new Cliente().getContatos().isEmpty());
    }

    @Test
    void adicionarContatoSincronizaOsDoisLados() {
        Cliente cliente = new Cliente();
        Contato contato = contato("48123", "a@x.com");

        cliente.adicionarContato(contato);

        assertEquals(1, cliente.getContatos().size());
        assertSame(cliente, contato.getCliente());
    }

    @Test
    void adicionarOMesmoContatoDuasVezesNaoDuplica() {
        Cliente cliente = new Cliente();
        Contato contato = contato("48123", "a@x.com");

        cliente.adicionarContato(contato);
        cliente.adicionarContato(contato);

        assertEquals(1, cliente.getContatos().size());
        assertSame(cliente, contato.getCliente());
    }

    @Test
    void adicionarContatoDeOutroClienteLancaExcecaoENaoAlteraNada() {
        Cliente a = new Cliente();
        Cliente b = new Cliente();
        Contato contato = contato("48123", "a@x.com");
        a.adicionarContato(contato);

        assertThrows(IllegalStateException.class, () -> b.adicionarContato(contato));

        assertSame(a, contato.getCliente());
        assertEquals(1, a.getContatos().size());
        assertTrue(b.getContatos().isEmpty());
    }

    @Test
    void adicionarContatoNuloLancaExcecaoSemAlterarALista() {
        Cliente cliente = new Cliente();
        assertThrows(NullPointerException.class, () -> cliente.adicionarContato(null));
        assertTrue(cliente.getContatos().isEmpty());
    }

    @Test
    void removerContatoDesfazOsDoisLados() {
        Cliente cliente = new Cliente();
        Contato contato = contato("48123", "a@x.com");
        cliente.adicionarContato(contato);

        cliente.removerContato(contato);

        assertTrue(cliente.getContatos().isEmpty());
        assertNull(contato.getCliente());
    }

    @Test
    void removerContatoQueNaoPertenceAoClienteNaoAlteraNada() {
        Cliente dono = new Cliente();
        Contato contato = contato("1", "a@x.com");
        dono.adicionarContato(contato);

        new Cliente().removerContato(contato);

        assertSame(dono, contato.getCliente());
        assertEquals(1, dono.getContatos().size());
    }

    @Test
    void listaDeContatosExpostaNaoPodeSerAlteradaDiretamente() {
        Cliente cliente = new Cliente();
        assertThrows(UnsupportedOperationException.class,
                () -> cliente.getContatos().add(contato("1", "a@x.com")));
    }
}
