package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.config.ExemplosDeErro;
import io.github.suzanacavalcante.desafio.config.Problema;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contatos")
@RequiredArgsConstructor
@Tag(name = "Contatos", description = "Cadastro de contatos de clientes que já existem")
public class ContatoController {

    private final ContatoService contatoService;

    @Operation(summary = "Cadastra um contato para um cliente existente",
            description = "O cliente é informado em `clienteId`, no corpo.")
    @ApiResponse(responseCode = "201", description = "Contato criado")
    @ApiResponse(responseCode = "400", description = "Campo inválido (`erros` diz qual) ou corpo malformado",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problema.class),
                    examples = @ExampleObject(name = "corpo inválido", value = ExemplosDeErro.CORPO_INVALIDO_400)))
    @ApiResponse(responseCode = "404", description = "Cliente inexistente",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Problema.class),
                    examples = @ExampleObject(name = "não encontrado",
                            value = ExemplosDeErro.CLIENTE_NAO_ENCONTRADO_404)))
    @PostMapping
    public ResponseEntity<ContatoResponse> criar(@Valid @RequestBody NovoContatoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contatoService.criar(request));
    }
}
