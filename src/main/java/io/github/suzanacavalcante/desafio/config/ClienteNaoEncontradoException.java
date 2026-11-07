package io.github.suzanacavalcante.desafio.config;

import lombok.Getter;

/**
 * Exceção de domínio: o cliente referenciado não existe. Compartilhada por mais de uma feature
 * (criar contato, listar contatos de um cliente). O mapeamento para HTTP 404 é feito no tratamento
 * global de erros (issue #11).
 */
@Getter
public class ClienteNaoEncontradoException extends RuntimeException {

    private final Long clienteId;

    public ClienteNaoEncontradoException(Long clienteId) {
        super("Cliente " + clienteId + " não encontrado");
        this.clienteId = clienteId;
    }
}
