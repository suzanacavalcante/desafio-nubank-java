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

    private List<Cliente> comContatosDe(List<Cliente> clientes) {
        return clienteRepository.findComContatosByIdIn(clientes.stream().map(Cliente::getId).toList());
    }

    @Test
    void findComContatosByIdInDevolveClienteSemContatosEClienteComContatosSemDuplicar() {
        Cliente semContato = clienteRepository.save(cliente("Sem contato"));
        Cliente comContatos = cliente("Com contatos");
        comContatos.adicionarContato(contato("1", "1@x.com"));
        comContatos.adicionarContato(contato("2", "2@x.com"));
        comContatos.adicionarContato(contato("3", "3@x.com"));
        clienteRepository.save(comContatos);
        limparCache();

        List<Cliente> todos = comContatosDe(List.of(semContato, comContatos));

        assertEquals(2, todos.size(), "LEFT JOIN FETCH não pode duplicar a raiz nem perder cliente sem contato");
        assertTrue(todos.get(0).getContatos().isEmpty());
        assertEquals(3, todos.get(1).getContatos().size());
    }

    @Test
    void findComContatosByIdInOrdenaClientesEContatosPorId() {
        Cliente primeiro = cliente("Primeiro");
        primeiro.adicionarContato(contato("a", "a@x.com"));
        primeiro.adicionarContato(contato("b", "b@x.com"));
        primeiro.adicionarContato(contato("c", "c@x.com"));
        clienteRepository.save(primeiro);
        Cliente segundo = clienteRepository.save(cliente("Segundo"));
        limparCache();

        // ids pedidos fora de ordem: a ordem do resultado é a do banco (por id), não a da lista
        List<Cliente> resultado = clienteRepository.findComContatosByIdIn(List.of(segundo.getId(), primeiro.getId()));

        assertEquals(List.of(primeiro.getId(), segundo.getId()), resultado.stream().map(Cliente::getId).toList());
        assertEquals(List.of("a", "b", "c"),
                resultado.get(0).getContatos().stream().map(Contato::getTelefone).toList());
    }

    @Test
    void findComContatosByIdInSoTrazOsClientesPedidos() {
        Cliente a = clienteRepository.save(cliente("A"));
        clienteRepository.save(cliente("B"));
        limparCache();

        assertEquals(List.of(a.getId()), comContatosDe(List.of(a)).stream().map(Cliente::getId).toList());
    }

    @Test
    void findIdsPaginaPorIdEInformaOTotal() {
        List<Long> criados = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            criados.add(clienteRepository.save(cliente("P" + i)).getId());
        }
        limparCache();
        long jaExistiam = clienteRepository.count() - 5;

        var primeira = clienteRepository.findIds(org.springframework.data.domain.PageRequest.of(0, 2));
        var ultima = clienteRepository.findIds(org.springframework.data.domain.PageRequest.of(
                (int) ((jaExistiam + 5 - 1) / 2), 2));

        assertEquals(jaExistiam + 5, primeira.getTotalElements());
        assertEquals(2, primeira.getContent().size());
        assertEquals(primeira.getContent().stream().sorted().toList(), primeira.getContent(), "ids em ordem crescente");
        assertTrue(ultima.getContent().contains(criados.get(4)), "o maior id está na última página");
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

    /**
     * Critério da #29: a listagem paginada tem número <b>constante</b> de consultas, sem N+1: 1 de ids,
     * 1 de contagem (página inicial cheia) e 1 dos contatos, qualquer que seja o tamanho da página.
     */
    @Test
    void paginaDeIdsMaisFetchDosContatosTemNumeroConstanteDeConsultas() {
        for (int i = 0; i < 6; i++) {
            Cliente c = cliente("Stat" + i);
            for (int j = 0; j < 3; j++) {
                c.adicionarContato(contato("t" + j, i + "-" + j + "@x.com"));
            }
            clienteRepository.save(c);
        }
        long total = clienteRepository.count();
        Statistics estatisticas = em.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        for (int tamanho : new int[] {2, 5}) {
            limparCache();
            estatisticas.clear();

            var ids = clienteRepository.findIds(org.springframework.data.domain.PageRequest.of(0, tamanho));
            List<Cliente> clientes = clienteRepository.findComContatosByIdIn(ids.getContent());
            // percorre as coleções: se fossem LAZY, cada uma dispararia uma consulta (N+1)
            clientes.forEach(c -> assertEquals(3, c.getContatos().size()));

            assertEquals(tamanho, clientes.size());
            assertEquals(total >= tamanho ? 3 : 2, estatisticas.getPrepareStatementCount(),
                    "tamanho " + tamanho + ": esperava ids + contagem + contatos; consultas: "
                            + java.util.Arrays.toString(estatisticas.getQueries()));
        }
    }
}
