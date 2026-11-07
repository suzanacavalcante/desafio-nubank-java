package io.github.suzanacavalcante.desafio.contato;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContatoRepository extends JpaRepository<Contato, Long> {

    /**
     * Contatos de um cliente, ordenados por id. Filtra por {@code cliente.id}, sem carregar o
     * {@code Cliente}.
     * <p>
     * Atenção: para um cliente que não existe devolve lista vazia, igual a um cliente sem contatos.
     * Quem expõe isso na API deve checar {@code ClienteRepository.existsById} antes e responder 404.
     */
    List<Contato> findByClienteIdOrderById(Long clienteId);
}
