package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.suporte.UsaPostgresDeTeste;
import io.github.suzanacavalcante.desafio.contato.Contato;
import io.github.suzanacavalcante.desafio.contato.ContatoRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistência do agregado Cliente/Contato contra um PostgreSQL real e descartável (Testcontainers).
 * Cada teste roda numa transação que sofre rollback, então não deixa dados no banco.
 * <p>
 * Cobre os três cenários do agregado: (1) persistir cliente com contatos em cascata; (2) remover um
 * contato da lista apaga o órfão ({@code orphanRemoval}); (3) excluir o cliente apaga seus contatos.
 */
@UsaPostgresDeTeste
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ClienteRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    ClienteRepository clienteRepository;

    @Autowired
    ContatoRepository contatoRepository;

    private static Cliente cliente(String nome) {
        Cliente c = new Cliente();
        c.setNome(nome);
        return c;
    }

    private static Contato contato(String telefone, String email) {
        Contato ct = new Contato();
        ct.setTelefone(telefone);
        ct.setEmail(email);
        return ct;
    }

    private void limparCache() {
        em.flush();
        em.clear();
    }

    @Test
    void salvarClienteGravaOsContatosEmCascataComAFkPreenchida() {
        Cliente c = cliente("Ana");
        c.adicionarContato(contato("111", "a@x.com"));
        c.adicionarContato(contato("222", "b@x.com"));

        Cliente salvo = clienteRepository.save(c);
        limparCache();

        List<Contato> doBanco = contatoRepository.findByClienteIdOrderById(salvo.getId());
        assertEquals(2, doBanco.size());
        doBanco.forEach(ct -> assertNotNull(ct.getId()));
        assertEquals(List.of("111", "222"), doBanco.stream().map(Contato::getTelefone).toList());
    }

    @Test
    void findAllComContatosDevolveClienteSemContatosEClienteComContatosSemDuplicar() {
        Cliente semContato = clienteRepository.save(cliente("Sem contato"));
        Cliente comContatos = cliente("Com contatos");
        comContatos.adicionarContato(contato("1", "1@x.com"));
        comContatos.adicionarContato(contato("2", "2@x.com"));
        comContatos.adicionarContato(contato("3", "3@x.com"));
        clienteRepository.save(comContatos);
        limparCache();

        List<Cliente> todos = clienteRepository.findAllComContatos().stream()
                .filter(c -> c.getId().equals(semContato.getId()) || c.getId().equals(comContatos.getId()))
                .toList();

        assertEquals(2, todos.size(), "LEFT JOIN FETCH não pode duplicar a raiz nem perder cliente sem contato");
        assertTrue(todos.get(0).getContatos().isEmpty());
        assertEquals(3, todos.get(1).getContatos().size());
    }

    @Test
    void findAllComContatosOrdenaClientesEContatosPorId() {
        Cliente primeiro = cliente("Primeiro");
        primeiro.adicionarContato(contato("a", "a@x.com"));
        primeiro.adicionarContato(contato("b", "b@x.com"));
        primeiro.adicionarContato(contato("c", "c@x.com"));
        clienteRepository.save(primeiro);
        Cliente segundo = clienteRepository.save(cliente("Segundo"));
        limparCache();

        List<Cliente> resultado = clienteRepository.findAllComContatos().stream()
                .filter(c -> c.getId().equals(primeiro.getId()) || c.getId().equals(segundo.getId()))
                .toList();

        assertEquals(List.of(primeiro.getId(), segundo.getId()), resultado.stream().map(Cliente::getId).toList());
        assertEquals(List.of("a", "b", "c"),
                resultado.get(0).getContatos().stream().map(Contato::getTelefone).toList());
    }

    @Test
    void findByClienteIdOrderByIdParaClienteInexistenteDevolveListaVazia() {
        assertTrue(contatoRepository.findByClienteIdOrderById(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void findByClienteIdOrderByIdNaoMisturaContatosDeOutroCliente() {
        Cliente a = cliente("A");
        a.adicionarContato(contato("1", "a@x.com"));
        Cliente b = cliente("B");
        b.adicionarContato(contato("2", "b@x.com"));
        clienteRepository.save(a);
        clienteRepository.save(b);
        limparCache();

        List<Contato> deA = contatoRepository.findByClienteIdOrderById(a.getId());

        assertEquals(1, deA.size());
        assertEquals("1", deA.get(0).getTelefone());
    }

    @Test
    void removerContatoDoClienteApagaOContatoDoBanco() {
        Cliente c = cliente("Ana");
        c.adicionarContato(contato("1", "1@x.com"));
        c.adicionarContato(contato("2", "2@x.com"));
        clienteRepository.save(c);
        limparCache();

        Cliente recarregado = clienteRepository.findById(c.getId()).orElseThrow();
        assertTrue(recarregado.removerContato(recarregado.getContatos().get(0)));
        limparCache();

        List<Contato> restantes = contatoRepository.findByClienteIdOrderById(c.getId());
        assertEquals(1, restantes.size());
        assertEquals("2", restantes.get(0).getTelefone());
    }

    @Test
    void excluirClienteApagaSeusContatosEmCascata() {
        Cliente c = cliente("Ana");
        c.adicionarContato(contato("1", "1@x.com"));
        clienteRepository.save(c);
        limparCache();
        Long id = c.getId();

        clienteRepository.deleteById(id);
        limparCache();

        assertTrue(contatoRepository.findByClienteIdOrderById(id).isEmpty());
    }

    @Test
    void bancoRejeitaClienteSemNome() {
        assertThrows(DataIntegrityViolationException.class, () -> clienteRepository.saveAndFlush(cliente(null)));
    }

    @Test
    void bancoRejeitaNomeMaiorQueOLimite() {
        String nome = "a".repeat(Cliente.NOME_MAX + 1);
        assertThrows(DataIntegrityViolationException.class, () -> clienteRepository.saveAndFlush(cliente(nome)));
    }

    @Test
    void bancoAceitaNomeExatamenteNoLimite() {
        Cliente salvo = clienteRepository.saveAndFlush(cliente("a".repeat(Cliente.NOME_MAX)));

        assertNotNull(salvo.getId());
    }

    @Test
    void bancoRejeitaContatoSemEmail() {
        Cliente c = cliente("Ana");
        c.adicionarContato(contato("1", null));

        assertThrows(DataIntegrityViolationException.class, () -> clienteRepository.saveAndFlush(c));
    }

    @Test
    void bancoRejeitaTelefoneAcimaDoLimite() {
        Cliente telefoneLongo = cliente("T");
        telefoneLongo.adicionarContato(contato("1".repeat(Contato.TELEFONE_MAX + 1), "a@x.com"));
        assertThrows(DataIntegrityViolationException.class, () -> clienteRepository.saveAndFlush(telefoneLongo));
    }

    @Test
    void bancoRejeitaEmailAcimaDoLimite() {
        Cliente emailLongo = cliente("E");
        emailLongo.adicionarContato(contato("1", "e".repeat(Contato.EMAIL_MAX + 1)));
        assertThrows(DataIntegrityViolationException.class, () -> clienteRepository.saveAndFlush(emailLongo));
    }

    /** Critério da #14 (review do PR #24): {@code findAllComContatos} é <b>uma</b> consulta, sem N+1. */
    @Test
    void findAllComContatosExecutaUmaUnicaConsultaSql() {
        for (int i = 0; i < 3; i++) {
            Cliente c = cliente("Stat" + i);
            for (int j = 0; j < 3; j++) {
                c.adicionarContato(contato("t" + j, i + "-" + j + "@x.com"));
            }
            clienteRepository.save(c);
        }
        limparCache();
        Statistics estatisticas = em.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        List<Cliente> clientes = clienteRepository.findAllComContatos();
        // percorre as coleções: se fossem LAZY, cada uma dispararia uma consulta (N+1)
        clientes.forEach(c -> assertEquals(3, c.getContatos().size()));

        assertEquals(1, estatisticas.getPrepareStatementCount(),
                "esperava 1 SELECT; houve " + estatisticas.getPrepareStatementCount());
    }
}
