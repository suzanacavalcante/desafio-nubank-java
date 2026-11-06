package io.github.suzanacavalcante.desafio.contato;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContatoRepository extends JpaRepository<Contato, Long> {

    /** Contatos de um cliente. Filtra por {@code cliente.id}, sem carregar o {@code Cliente}. */
    List<Contato> findByClienteId(Long clienteId);
}
