package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.cliente.dto.PaginaDeClientes;
import io.github.suzanacavalcante.desafio.contato.Contato;
import io.github.suzanacavalcante.desafio.contato.ContatoRepository;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    ClienteRepository clienteRepository;

    @Mock
    ContatoRepository contatoRepository;

    ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository, contatoRepository);
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

    /**
     * Os contatos viajam pelo cascade do cliente, na mesma operação de {@code save}. Se o service passasse a
     * gravá-los por {@code ContatoRepository}, cliente e contatos deixariam de ser atômicos (a falha de um
     * contato deixaria o cliente gravado).
     */
    @Test
    void criarComContatosNaoGravaContatosPeloContatoRepository() {
        salvarAtribuindoIds();

        service.criar(new ClienteRequest("Ana", List.of(new ContatoRequest("48123", "a@x.com"))));

        verifyNoInteractions(contatoRepository);
    }

    /**
     * O enunciado não pede unicidade de contato: este teste documenta que não há deduplicação (dois contatos
     * idênticos viram dois). Se a regra mudar, troque o teste junto com ela.
     */
    @Test
    void criarMantemContatosIguaisSemDeduplicar() {
        salvarAtribuindoIds();
        var request = new ClienteRequest("Ana", List.of(
                new ContatoRequest("48123", "a@x.com"),
                new ContatoRequest("48123", "a@x.com")));

        ClienteResponse response = service.criar(request);

        assertEquals(List.of("48123", "48123"), response.contatos().stream().map(ContatoResponse::telefone).toList());
        assertEquals(2, response.contatos().stream().map(ContatoResponse::id).distinct().count());
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

    private static Page<Long> paginaDeIds(List<Long> ids, int page, int size, long total) {
        return new PageImpl<>(ids, PageRequest.of(page, size), total);
    }

    @Test
    void listarConverteCadaClienteComSeusContatosPreservandoAOrdemEMontaOEnvelope() {
        when(clienteRepository.findIds(PageRequest.of(1, 3))).thenReturn(paginaDeIds(List.of(1L, 2L, 3L), 1, 3, 7));
        when(clienteRepository.findComContatosByIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(
                clienteComContatos(1L, "Ana", 10L, 11L),
                clienteComContatos(2L, "Bia"),
                clienteComContatos(3L, "Caio", 12L)));

        PaginaDeClientes pagina = service.listar(1, 3);

        List<ClienteResponse> resposta = pagina.content();
        assertEquals(List.of("Ana", "Bia", "Caio"), resposta.stream().map(ClienteResponse::nome).toList());
        assertEquals(List.of(10L, 11L), resposta.get(0).contatos().stream().map(ContatoResponse::id).toList());
        assertEquals(3L, resposta.get(2).id());
        resposta.get(0).contatos().forEach(c -> assertEquals(1L, c.clienteId()));
        assertEquals(1, pagina.page());
        assertEquals(3, pagina.size());
        assertEquals(7, pagina.totalElements());
        assertEquals(3, pagina.totalPages());
    }

    @Test
    void listarClienteSemContatosDevolveListaVaziaNaoNula() {
        when(clienteRepository.findIds(any(Pageable.class))).thenReturn(paginaDeIds(List.of(1L), 0, 20, 1));
        when(clienteRepository.findComContatosByIdIn(List.of(1L))).thenReturn(List.of(clienteComContatos(1L, "Ana")));

        assertTrue(service.listar(0, 20).content().get(0).contatos().isEmpty());
    }

    @Test
    void listarPaginaVaziaNaoConsultaOsContatos() {
        when(clienteRepository.findIds(any(Pageable.class))).thenReturn(paginaDeIds(List.of(), 5, 20, 3));

        PaginaDeClientes pagina = service.listar(5, 20);

        assertTrue(pagina.content().isEmpty());
        assertEquals(3, pagina.totalElements());
        verify(clienteRepository, never()).findComContatosByIdIn(any());
    }

    @Test
    void listarSemNenhumClienteDevolveEnvelopeVazio() {
        when(clienteRepository.findIds(any(Pageable.class))).thenReturn(paginaDeIds(List.of(), 0, 20, 0));

        PaginaDeClientes pagina = service.listar(0, 20);

        assertTrue(pagina.content().isEmpty());
        assertEquals(0, pagina.totalElements());
        assertEquals(0, pagina.totalPages());
    }

    @Test
    void listarUsaApenasAsDuasConsultasDaPaginacaoEPassaPageESizeAoRepositorio() {
        when(clienteRepository.findIds(any(Pageable.class))).thenReturn(paginaDeIds(List.of(4L), 2, 10, 21));
        when(clienteRepository.findComContatosByIdIn(List.of(4L))).thenReturn(List.of(clienteComContatos(4L, "Duda")));

        service.listar(2, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clienteRepository).findIds(captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
        verify(clienteRepository).findComContatosByIdIn(List.of(4L));
        verifyNoMoreInteractions(clienteRepository);
    }

    @Test
    void propagaAFalhaDoRepositorioSemEngolirOErro() {
        when(clienteRepository.save(any(Cliente.class))).thenThrow(new DataIntegrityViolationException("falha"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.criar(new ClienteRequest("Ana", null)));
    }

    private static Contato contatoDe(Cliente cliente, long id) {
        Contato contato = new Contato();
        ReflectionTestUtils.setField(contato, "id", id);
        contato.setTelefone("tel" + id);
        contato.setEmail(id + "@x.com");
        contato.setCliente(cliente);
        return contato;
    }

    @Test
    void contatosDoClienteConverteNaOrdemDoRepositorioComClienteId() {
        Cliente ana = clienteComContatos(7L, "Ana");
        when(clienteRepository.existsById(7L)).thenReturn(true);
        when(contatoRepository.findByClienteIdOrderById(7L))
                .thenReturn(List.of(contatoDe(ana, 10L), contatoDe(ana, 11L)));

        List<ContatoResponse> resposta = service.contatosDoCliente(7L);

        assertEquals(List.of(10L, 11L), resposta.stream().map(ContatoResponse::id).toList());
        assertEquals(List.of("tel10", "tel11"), resposta.stream().map(ContatoResponse::telefone).toList());
        resposta.forEach(c -> assertEquals(7L, c.clienteId()));
    }

    @Test
    void contatosDoClienteSemContatosDevolveListaVazia() {
        when(clienteRepository.existsById(7L)).thenReturn(true);
        when(contatoRepository.findByClienteIdOrderById(7L)).thenReturn(List.of());

        assertTrue(service.contatosDoCliente(7L).isEmpty());
    }

    @Test
    void contatosDeClienteInexistenteLancaExcecaoSemConsultarContatos() {
        when(clienteRepository.existsById(99L)).thenReturn(false);

        ClienteNaoEncontradoException ex = assertThrows(ClienteNaoEncontradoException.class,
                () -> service.contatosDoCliente(99L));

        assertEquals(99L, ex.getClienteId());
        verify(contatoRepository, never()).findByClienteIdOrderById(any());
    }
}
