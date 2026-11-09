package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.contato.Contato;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    ClienteRepository clienteRepository;

    ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository);
    }

    /** Simula o banco: atribui ids ao cliente e aos contatos ao salvar. */
    private void salvarAtribuindoIds() {
        AtomicLong seq = new AtomicLong(1);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", seq.getAndIncrement());
            c.getContatos().forEach(ct -> ReflectionTestUtils.setField(ct, "id", seq.getAndIncrement() * 10));
            return c;
        });
    }

    @Test
    void criaClienteSomenteComNomeEDevolveListaDeContatosVazia() {
        salvarAtribuindoIds();

        ClienteResponse response = service.criar(new ClienteRequest("Ana", null));

        assertEquals("Ana", response.nome());
        assertEquals(1L, response.id());
        assertTrue(response.contatos().isEmpty());
    }

    @Test
    void criaClienteComListaVaziaDeContatos() {
        salvarAtribuindoIds();

        assertTrue(service.criar(new ClienteRequest("Ana", List.of())).contatos().isEmpty());
    }

    @Test
    void criaClienteComDoisContatosEOsDevolveComClienteId() {
        salvarAtribuindoIds();
        var request = new ClienteRequest("Ana", List.of(
                new ContatoRequest("48123", "a@x.com"),
                new ContatoRequest("48987", "b@x.com")));

        ClienteResponse response = service.criar(request);

        assertEquals(2, response.contatos().size());
        assertEquals(List.of("48123", "48987"), response.contatos().stream().map(ContatoResponse::telefone).toList());
        response.contatos().forEach(c -> assertEquals(response.id(), c.clienteId()));
    }

    @Test
    void salvaUmaUnicaVezClienteJaComOsContatosAssociados() {
        salvarAtribuindoIds();
        service.criar(new ClienteRequest("Ana", List.of(new ContatoRequest("48123", "a@x.com"))));

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(captor.capture());
        Cliente salvo = captor.getValue();
        assertEquals(1, salvo.getContatos().size());
        assertEquals(salvo, salvo.getContatos().get(0).getCliente());
    }

    private static Cliente clienteComContatos(long id, String nome, long... idsDosContatos) {
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", id);
        cliente.setNome(nome);
        for (long idContato : idsDosContatos) {
            Contato contato = new Contato();
            ReflectionTestUtils.setField(contato, "id", idContato);
            contato.setTelefone("tel" + idContato);
            contato.setEmail(idContato + "@x.com");
            cliente.adicionarContato(contato);
        }
        return cliente;
    }

    @Test
    void listarConverteCadaClienteComSeusContatosPreservandoAOrdem() {
        when(clienteRepository.findAllComContatos()).thenReturn(List.of(
                clienteComContatos(1L, "Ana", 10L, 11L),
                clienteComContatos(2L, "Bia"),
                clienteComContatos(3L, "Caio", 12L)));

        List<ClienteResponse> resposta = service.listar();

        assertEquals(List.of("Ana", "Bia", "Caio"), resposta.stream().map(ClienteResponse::nome).toList());
        assertEquals(List.of(10L, 11L), resposta.get(0).contatos().stream().map(ContatoResponse::id).toList());
        assertEquals(3L, resposta.get(2).id());
        resposta.get(0).contatos().forEach(c -> assertEquals(1L, c.clienteId()));
    }

    @Test
    void listarClienteSemContatosDevolveListaVaziaNaoNula() {
        when(clienteRepository.findAllComContatos()).thenReturn(List.of(clienteComContatos(1L, "Ana")));

        assertTrue(service.listar().get(0).contatos().isEmpty());
    }

    @Test
    void listarSemClientesDevolveListaVazia() {
        when(clienteRepository.findAllComContatos()).thenReturn(List.of());

        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarUsaApenasFindAllComContatos() {
        when(clienteRepository.findAllComContatos()).thenReturn(List.of());

        service.listar();

        verify(clienteRepository).findAllComContatos();
        verifyNoMoreInteractions(clienteRepository);
    }

    @Test
    void propagaAFalhaDoRepositorioSemEngolirOErro() {
        when(clienteRepository.save(any(Cliente.class))).thenThrow(new DataIntegrityViolationException("falha"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.criar(new ClienteRequest("Ana", null)));
    }
}
