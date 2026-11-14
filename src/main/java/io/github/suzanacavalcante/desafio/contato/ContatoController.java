package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.openapi.Resposta400CorpoInvalido;
import io.github.suzanacavalcante.desafio.openapi.Resposta404ClienteNaoEncontrado;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import io.github.suzanacavalcante.desafio.contato.dto.NovoContatoRequest;
import io.swagger.v3.oas.annotations.Operation;
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
    @Resposta400CorpoInvalido
    @Resposta404ClienteNaoEncontrado
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContatoResponse> criar(@Valid @RequestBody NovoContatoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contatoService.criar(request));
    }
}
