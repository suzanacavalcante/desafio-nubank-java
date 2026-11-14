package io.github.suzanacavalcante.desafio.cliente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * 1ª consulta da listagem paginada: só os <b>ids</b> da página, por ordem de id (mais a contagem total,
     * que o Spring Data omite quando a primeira página não está cheia).
     * <p>
     * Os ids vêm sozinhos, sem {@code JOIN FETCH}, de propósito: com fetch de coleção o Hibernate
     * não pagina no banco; carrega a base inteira e corta em memória ({@code HHH90003004}), o que anularia
     * o ganho. Os contatos são buscados em seguida por {@link #findComContatosByIdIn}.
     */
    @Query("select c.id from Cliente c order by c.id")
    Page<Long> findIds(Pageable pageable);

    /**
     * 2ª consulta da listagem: os clientes dos {@code ids} já com seus contatos, em uma única consulta (sem
     * N+1). {@code LEFT JOIN} mantém os clientes sem contatos. A ordem é determinística: clientes por id e,
     * dentro de cada cliente, contatos por id (a coleção é uma {@code List} sem {@code @OrderColumn}, então
     * sem {@code ct.id} no {@code order by} sairiam na ordem que o banco devolvesse).
     * <p>
     * Não precisa de {@code DISTINCT}: o Hibernate 6+ já deduplica a entidade-raiz em consultas com
     * {@code JOIN FETCH} de coleção. Com ids vazios devolve lista vazia; o service nem chega a chamar.
     */
    @Query("select c from Cliente c left join fetch c.contatos ct where c.id in :ids order by c.id, ct.id")
    List<Cliente> findComContatosByIdIn(@Param("ids") List<Long> ids);
}
