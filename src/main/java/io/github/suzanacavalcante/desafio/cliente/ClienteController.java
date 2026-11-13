package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.config.ExemplosDeErro;
import io.github.suzanacavalcante.desafio.config.Problema;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Cadastro de clientes e consulta dos seus contatos")
public class ClienteController {

    private final ClienteService clienteService;

    @Operation(summary = "Cadastra um cliente",
            description = "Cria o cliente e, se vierem, os seus contatos, tudo na mesma transação: se um contato "
                    + "falhar, nada é gravado. `contatos` é opcional (até 50).")
    @ApiResponse(responseCode = "201", description = "Cliente criado, com os contatos (lista vazia se não vieram)")
    @ApiResponse(responseCode = "400", description = "Campo inválido (`erros` diz qual) ou corpo malformado",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problema.class),
                    examples = @ExampleObject(name = "validação", value = ExemplosDeErro.VALIDACAO_400)))
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.criar(request));
    }

    @Operation(summary = "Lista os clientes com seus contatos",
            description = "Devolve todos os clientes, ordenados por id, cada um com seus contatos (também por id). "
                    + "Cliente sem contatos vem com `contatos: []`. **Sem paginação**: a resposta cresce com a "
                    + "base inteira.")
    @ApiResponse(responseCode = "200", description = "Lista de clientes (vazia se não houver nenhum)")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ClienteResponse> listar() {
        return clienteService.listar();
    }

    @Operation(summary = "Lista os contatos de um cliente",
            description = "Contatos do cliente, ordenados por id. Cliente existente sem contatos responde 200 com `[]`.")
    @ApiResponse(responseCode = "200", description = "Contatos do cliente (lista vazia se não tiver nenhum)",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ContatoResponse.class))))
    @ApiResponse(responseCode = "400", description = "`id` que não é um número",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problema.class),
                    examples = @ExampleObject(name = "id inválido", value = ExemplosDeErro.ID_INVALIDO_400)))
    @ApiResponse(responseCode = "404", description = "Cliente inexistente",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problema.class),
                    examples = @ExampleObject(name = "não encontrado",
                            value = ExemplosDeErro.CLIENTE_NAO_ENCONTRADO_404)))
    @GetMapping(value = "/{id}/contatos", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ContatoResponse> contatosDoCliente(
            @Parameter(description = "Identificador do cliente", example = "1") @PathVariable Long id) {
        return clienteService.contatosDoCliente(id);
    }
}
