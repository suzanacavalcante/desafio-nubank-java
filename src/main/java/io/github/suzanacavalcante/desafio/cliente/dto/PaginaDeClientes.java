package io.github.suzanacavalcante.desafio.cliente.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Uma página de {@code GET /clientes}. É um tipo próprio (e não o {@code Page} do Spring Data) para o formato
 * do contrato ficar sob controle da API e independente da serialização interna do Spring.
 */
public record PaginaDeClientes(
        @Schema(description = "Clientes desta página, ordenados por id, cada um com seus contatos") List<ClienteResponse> content,
        @Schema(description = "Número desta página, começando em 0", example = "0") int page,
        @Schema(description = "Tamanho de página pedido (máximo de clientes em `content`)", example = "20") int size,
        @Schema(description = "Total de clientes cadastrados, somando todas as páginas", example = "42") long totalElements,
        @Schema(description = "Total de páginas para este `size` (0 se não houver clientes)", example = "3") int totalPages) {
}
