package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.cliente.Cliente;
import io.github.suzanacavalcante.desafio.cliente.ClienteRepository;
import io.github.suzanacavalcante.desafio.config.ClienteNaoEncontradoException;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContatoServiceTest {

    @Mock
    ClienteRepository clienteRepository;

    @Mock
    ContatoRepository contatoRepository;

    ContatoService service;

    @BeforeEach
    void setUp() {
        service = new ContatoService(clienteRepository, contatoRepository);
    }

    private static Cliente clienteComId(long id) {
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", id);
        cliente.setNome("Ana");
        return cliente;
    }

    @Test
    void clienteExistenteGravaOContatoAssociadoEDevolveClienteId() {
        Cliente cliente = clienteComId(7L);
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(cliente));
        when(contatoRepository.save(any(Contato.class))).thenAnswer(inv -> {
            Contato c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 11L);
            return c;
        });

        ContatoResponse response = service.criar(new NovoContatoRequest(7L, "48123", "a@x.com"));

        assertEquals(new ContatoResponse(11L, "48123", "a@x.com", 7L), response);
        ArgumentCaptor<Contato> captor = ArgumentCaptor.forClass(Contato.class);
        verify(contatoRepository).save(captor.capture());
        assertSame(cliente, captor.getValue().getCliente());
    }

    @Test
    void naoCarregaOsContatosDoClienteParaAcrescentarUm() {
        Cliente cliente = clienteComId(7L);
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(cliente));
        when(contatoRepository.save(any(Contato.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(new NovoContatoRequest(7L, "48123", "a@x.com"));

        // a coleção do agregado não é tocada: ela é LAZY e só seria carregada por adicionarContato
        assertEquals(0, cliente.getContatos().size());
    }

    @Test
    void clienteInexistenteLancaExcecaoDeDominioENaoGrava() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        ClienteNaoEncontradoException ex = assertThrows(ClienteNaoEncontradoException.class,
                () -> service.criar(new NovoContatoRequest(99L, "48123", "a@x.com")));

        assertEquals(99L, ex.getClienteId());
        assertEquals("Cliente 99 não encontrado", ex.getMessage());
        verify(contatoRepository, never()).save(any());
    }
}
