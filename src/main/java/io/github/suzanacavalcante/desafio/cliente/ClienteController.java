package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.cliente.dto.ClienteRequest;
import io.github.suzanacavalcante.desafio.cliente.dto.ClienteResponse;
import io.github.suzanacavalcante.desafio.contato.dto.ContatoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping
    public ResponseEntity<ClienteResponse> criar(@RequestBody ClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.criar(request));
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        return clienteService.listar();
    }

    @GetMapping("/{id}/contatos")
    public List<ContatoResponse> contatosDoCliente(@PathVariable Long id) {
        return clienteService.contatosDoCliente(id);
    }
}
