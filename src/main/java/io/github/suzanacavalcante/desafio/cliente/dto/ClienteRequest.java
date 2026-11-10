package io.github.suzanacavalcante.desafio.cliente.dto;

import io.github.suzanacavalcante.desafio.cliente.Cliente;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoRequest;
import io.github.suzanacavalcante.desafio.validacao.SemCaracteresInvalidos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo de {@code POST /clientes}.
 * <p>
 * Os limites de tamanho vêm das colunas ({@link Cliente#NOME_MAX}); o de contatos
 * ({@link #CONTATOS_MAX}) existe para não aceitar payloads gigantes em uma única requisição.
 *
 * @param nome     obrigatório, não pode ser vazio nem só espaços
 * @param contatos opcional: pode ser {@code null} ou vazio (o cliente é criado sem contatos); não pode
 *                 conter elementos {@code null}, e cada contato é validado
 */
public record ClienteRequest(
        @NotBlank @SemCaracteresInvalidos @Size(max = Cliente.NOME_MAX) String nome,
        @Size(max = CONTATOS_MAX) List<@NotNull @Valid ContatoRequest> contatos) {

    /** Máximo de contatos aceitos em um único {@code POST /clientes}. */
    public static final int CONTATOS_MAX = 50;
}
