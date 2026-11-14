package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.cliente.dto.PaginaDeClientes;
import io.github.suzanacavalcante.desafio.openapi.Resposta400Paginacao;
import io.github.suzanacavalcante.desafio.openapi.Resposta400IdInvalido;
import io.github.suzanacavalcante.desafio.openapi.Resposta400Validacao;
import io.github.suzanacavalcante.desafio.openapi.Resposta404ClienteNaoEncontrado;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Cadastro de clientes e consulta dos seus contatos")
public class ClienteController {

    /** Tamanho de página quando o cliente não informa {@code size}. */
    public static final int TAMANHO_PADRAO = 20;
    /** Maior {@code size} aceito: acima disso é 400 (a resposta não pode crescer sem limite). */
    public static final int TAMANHO_MAXIMO = 100;
    /**
     * Maior {@code page} aceito. O deslocamento ({@code page * size}) precisa caber em um {@code int} (limite do
     * Spring Data/JPA): com {@code size = 100}, 10 milhões de páginas dão 1 bilhão de linhas, folga de sobra, e
     * acima disso a consulta falharia com 500 em vez de responder 400.
     */
    public static final int PAGINA_MAXIMA = 10_000_000;

    private final ClienteService clienteService;

    @Operation(summary = "Cadastra um cliente",
            description = "Cria o cliente e, se vierem, os seus contatos, tudo na mesma transação: se um contato "
                    + "falhar, nada é gravado. `contatos` é opcional (até 50).")
    @ApiResponse(responseCode = "201", description = "Cliente criado, com os contatos (lista vazia se não vieram)")
    @Resposta400Validacao
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.criar(request));
    }

    @Operation(summary = "Lista os clientes com seus contatos (paginado)",
            description = "Devolve uma página de clientes, ordenados por id, cada um com seus contatos (também por "
                    + "id). Cliente sem contatos vem com `contatos: []`. A resposta é um envelope com `content`, "
                    + "`page`, `size`, `totalElements` e `totalPages`. `size` vai de 1 a 100 (padrão 20) e `page` de 0 a 10.000.000; fora dos "
                    + "limites a resposta é 400, nunca um corte silencioso. Página além da última devolve "
                    + "`content: []` com 200.")
    @ApiResponse(responseCode = "200", description = "Página de clientes (`content` vazio se não houver nenhum)")
    @Resposta400Paginacao
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PaginaDeClientes listar(
            @Parameter(description = "Número da página, começando em 0 (máximo " + PAGINA_MAXIMA + ")", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) @Max(PAGINA_MAXIMA) int page,
            @Parameter(description = "Clientes por página (1 a " + TAMANHO_MAXIMO + ")", example = "20")
            @RequestParam(defaultValue = "" + TAMANHO_PADRAO) @Min(1) @Max(TAMANHO_MAXIMO) int size) {
        return clienteService.listar(page, size);
    }

    @Operation(summary = "Lista os contatos de um cliente",
            description = "Contatos do cliente, ordenados por id. Cliente existente sem contatos responde 200 com `[]`.")
    @ApiResponse(responseCode = "200", description = "Contatos do cliente (lista vazia se não tiver nenhum)",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ContatoResponse.class))))
    @Resposta400IdInvalido
    @Resposta404ClienteNaoEncontrado
    @GetMapping(value = "/{id}/contatos", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ContatoResponse> contatosDoCliente(
            @Parameter(description = "Identificador do cliente", example = "1") @PathVariable Long id) {
        return clienteService.contatosDoCliente(id);
    }
}
