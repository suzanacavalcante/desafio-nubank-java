package io.github.suzanacavalcante.desafio.cliente;

import lombok.Getter;

/**
 * Exceção de domínio: o cliente referenciado não existe. Pertence ao pacote {@code cliente} (e não a
 * {@code config}) porque é um conceito do domínio; é usada também por {@code contato} (criar contato,
 * listar contatos de um cliente), que já depende de {@code cliente}. O mapeamento para HTTP 404 é
 * feito no tratamento global de erros (issue #11), que fica em {@code config} e depende desta classe.
 */
@Getter
public class ClienteNaoEncontradoException extends RuntimeException {

    private final Long clienteId;

    public ClienteNaoEncontradoException(Long clienteId) {
        super("Cliente " + clienteId + " não encontrado");
        this.clienteId = clienteId;
    }
}
