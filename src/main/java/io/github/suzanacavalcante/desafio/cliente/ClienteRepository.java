package io.github.suzanacavalcante.desafio.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Lista todos os clientes já com seus contatos, em uma única consulta (evita o N+1 de carregar
     * {@code contatos} um cliente por vez). {@code LEFT JOIN} mantém os clientes sem contatos.
     * <p>
     * A ordem é determinística: clientes por id e, dentro de cada cliente, contatos por id. Sem
     * {@code ct.id} no {@code order by} os contatos sairiam na ordem que o banco devolvesse, pois a
     * coleção é uma {@code List} sem {@code @OrderColumn}.
     * <p>
     * Não precisa de {@code DISTINCT}: o Hibernate 6+ já deduplica a entidade-raiz em consultas com
     * {@code JOIN FETCH} de coleção. Sem paginação de propósito: com fetch de coleção o Hibernate
     * paginaria em memória.
     */
    @Query("select c from Cliente c left join fetch c.contatos ct order by c.id, ct.id")
    List<Cliente> findAllComContatos();
}
