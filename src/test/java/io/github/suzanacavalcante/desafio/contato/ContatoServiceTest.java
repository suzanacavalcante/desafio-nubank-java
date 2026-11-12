package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.cliente.Cliente;
import io.github.suzanacavalcante.desafio.cliente.ClienteRepository;
import io.github.suzanacavalcante.desafio.cliente.ClienteNaoEncontradoException;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    void naoUsaAdicionarContatoParaNaoInicializarAColecao() {
        Cliente cliente = clienteComId(7L);
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(cliente));
        when(contatoRepository.save(any(Contato.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(new NovoContatoRequest(7L, "48123", "a@x.com"));

        // Guarda contra a regressão para Cliente.adicionarContato (que inicializaria a coleção LAZY):
        // o contato não entra na lista em memória do cliente. Este Cliente não é um proxy Hibernate,
        // então isto NÃO prova a ausência de carregamento; essa prova é a contagem de statements da #14.
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

    @Test
    void propagaAFalhaAoSalvarSemEngolirOErro() {
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(clienteComId(7L)));
        when(contatoRepository.save(any(Contato.class))).thenThrow(new DataIntegrityViolationException("falha"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.criar(new NovoContatoRequest(7L, "48123", "a@x.com")));
    }

    /** O cliente é buscado só por {@code findById}: nenhum método que carregue os contatos dele (LAZY). */
    @Test
    void buscaOClienteApenasPorFindById() {
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(clienteComId(7L)));
        when(contatoRepository.save(any(Contato.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(new NovoContatoRequest(7L, "48123", "a@x.com"));

        verify(clienteRepository).findById(7L);
        verifyNoMoreInteractions(clienteRepository);
    }
}
