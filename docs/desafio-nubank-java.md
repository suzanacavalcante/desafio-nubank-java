# Desafio Nubank (Backend) em Java + Spring Boot

> Escrito para estudo. É a stack **pedida no enunciado original**. O código é um esboço **não executado**: confira versões antes de rodar.
> Contexto do desafio: ver [desafio-backend-nubank.md](desafio-backend-nubank.md).

## 1. O que construir

API REST de **clientes** e **contatos** (1 cliente → N contatos).

| Método | Rota | Descrição | Sucesso | Erros |
|---|---|---|---|---|
| POST | `/clientes` | Cria cliente (com ou sem contatos) | 201 | 400 |
| POST | `/contatos` | Cria contato para cliente existente | 201 | 400, 404 |
| GET | `/clientes` | Lista clientes com contatos | 200 | |
| GET | `/clientes/{id}/contatos` | Contatos de um cliente | 200 | 404 |

Exigências: Spring Boot, Spring Data JPA, PostgreSQL, `@OneToMany`/`@ManyToOne`, camadas, DTOs, tratamento de erros, Lombok. Diferenciais: Docker, testes, Swagger.

## 2. Stack

> **Atualização:** os trechos abaixo foram escritos para Spring Boot 3.4, que não está mais no Spring Initializr. O projeto usa **Spring Boot 4.1.2**: nomes de starters, pacotes de teste e a versão do springdoc podem diferir. Gere o `pom.xml` pelo Initializr e use este guia como referência de design, não de dependências.

Java 21 · Spring Boot 4.1 · Spring Web · Spring Data JPA · Bean Validation · PostgreSQL · Lombok · springdoc-openapi · JUnit 5 + Mockito.

## 3. Estrutura (package by feature, como sugerido na visão sênior do vídeo)

```
desafio/
├── docker-compose.yml
├── pom.xml
└── src/main/java/br/com/desafio/
    ├── DesafioApplication.java
    ├── cliente/
    │   ├── Cliente.java
    │   ├── ClienteRepository.java
    │   ├── ClienteService.java
    │   ├── ClienteController.java
    │   ├── ClienteMapper.java
    │   └── dto/ClienteRequest.java, ClienteResponse.java
    ├── contato/
    │   ├── Contato.java
    │   ├── ContatoRepository.java
    │   ├── ContatoService.java
    │   ├── ContatoController.java
    │   └── dto/ContatoRequest.java, NovoContatoRequest.java, ContatoResponse.java
    └── config/
        ├── ClienteNaoEncontradoException.java
        └── ApiExceptionHandler.java
```

## 4. Banco com Docker (`docker-compose.yml`)

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: cadastro
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: manager
    ports: ["5432:5432"]
```

## 5. Dependências (`pom.xml`, trecho)

```xml
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
  <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.6.0</version></dependency>  <!-- 2.6.0 é da série Boot 3.x: use a versão compatível com Boot 4 -->
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
</dependencies>
```

## 6. Configuração (`application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cadastro
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:manager}
  jpa:
    hibernate:
      ddl-auto: update        # só em desenvolvimento; em produção use Flyway
    show-sql: true
    properties:
      hibernate.format_sql: true
```

## 7. Entidades

```java
// cliente/Cliente.java
@Entity
@Table(name = "clientes")
@Getter @Setter @NoArgsConstructor
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contato> contatos = new ArrayList<>();

    public void adicionarContato(Contato contato) {
        contatos.add(contato);
        contato.setCliente(this);
    }
}
```

```java
// contato/Contato.java
@Entity
@Table(name = "contatos")
@Getter @Setter @NoArgsConstructor
public class Contato {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String telefone;
    @Column(nullable = false) private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
}
```
Como a API só expõe DTOs, não é preciso `@JsonManagedReference`/`@JsonBackReference`.

## 8. DTOs (records + Bean Validation)

```java
public record ContatoRequest(
    @NotBlank(message = "Telefone é obrigatório") String telefone,
    @NotBlank(message = "E-mail é obrigatório") @Email String email) {}

public record ClienteRequest(
    @NotBlank(message = "Nome é obrigatório") String nome,
    @Valid List<ContatoRequest> contatos) {}

public record NovoContatoRequest(
    @NotNull(message = "clienteId é obrigatório") Long clienteId,
    @NotBlank String telefone,
    @NotBlank @Email String email) {}

public record ContatoResponse(Long id, String telefone, String email, Long clienteId) {}
public record ClienteResponse(Long id, String nome, List<ContatoResponse> contatos) {}
```

## 9. Mapper (sem duplicação)

```java
public final class ClienteMapper {
    private ClienteMapper() {}

    public static ContatoResponse toResponse(Contato c) {
        return new ContatoResponse(c.getId(), c.getTelefone(), c.getEmail(), c.getCliente().getId());
    }

    public static ClienteResponse toResponse(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNome(),
                c.getContatos().stream().map(ClienteMapper::toResponse).toList());
    }

    public static Contato toEntity(ContatoRequest r) {
        Contato c = new Contato();
        c.setTelefone(r.telefone());
        c.setEmail(r.email());
        return c;
    }
}
```

## 10. Repositories

```java
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    // JOIN FETCH evita o problema N+1 na listagem
    @Query("select distinct c from Cliente c left join fetch c.contatos order by c.id")
    List<Cliente> findAllComContatos();
}

public interface ContatoRepository extends JpaRepository<Contato, Long> {
    List<Contato> findByClienteId(Long clienteId);
}
```

## 11. Services

```java
@Service
@RequiredArgsConstructor
public class ClienteService {
    private final ClienteRepository clienteRepository;
    private final ContatoRepository contatoRepository;

    @Transactional
    public ClienteResponse criar(ClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNome(request.nome());
        if (request.contatos() != null) {
            request.contatos().forEach(r -> cliente.adicionarContato(ClienteMapper.toEntity(r)));
        }
        return ClienteMapper.toResponse(clienteRepository.save(cliente));
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        return clienteRepository.findAllComContatos().stream().map(ClienteMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ContatoResponse> contatosDoCliente(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new ClienteNaoEncontradoException(clienteId);
        }
        return contatoRepository.findByClienteId(clienteId).stream().map(ClienteMapper::toResponse).toList();
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class ContatoService {
    private final ClienteRepository clienteRepository;
    private final ContatoRepository contatoRepository;

    @Transactional
    public ContatoResponse criar(NovoContatoRequest request) {
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new ClienteNaoEncontradoException(request.clienteId()));
        Contato contato = ClienteMapper.toEntity(new ContatoRequest(request.telefone(), request.email()));
        contato.setCliente(cliente);
        return ClienteMapper.toResponse(contatoRepository.save(contato));
    }
}
```

## 12. Controllers

```java
@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {
    private final ClienteService service;

    @PostMapping
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    @GetMapping
    public List<ClienteResponse> listar() { return service.listar(); }

    @GetMapping("/{id}/contatos")
    public List<ContatoResponse> contatos(@PathVariable Long id) { return service.contatosDoCliente(id); }
}

@RestController
@RequestMapping("/contatos")
@RequiredArgsConstructor
public class ContatoController {
    private final ContatoService service;

    @PostMapping
    public ResponseEntity<ContatoResponse> criar(@Valid @RequestBody NovoContatoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }
}
```

## 13. Tratamento de erros

```java
public class ClienteNaoEncontradoException extends RuntimeException {
    public ClienteNaoEncontradoException(Long id) { super("Cliente " + id + " não encontrado"); }
}

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ClienteNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(ClienteNaoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacao(MethodArgumentNotValidException ex) {
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(e -> erros.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(erros);
    }
}
```

## 14. Rodar e testar

```bash
docker compose up -d
./mvnw spring-boot:run
# Swagger: http://localhost:8080/swagger-ui.html
```

```bash
curl -X POST localhost:8080/clientes -H "Content-Type: application/json" \
  -d '{"nome":"Mateus","contatos":[{"telefone":"48123","email":"m@x.com"}]}'

curl -X POST localhost:8080/contatos -H "Content-Type: application/json" \
  -d '{"clienteId":1,"telefone":"48987","email":"t@x.com"}'

curl localhost:8080/clientes
curl localhost:8080/clientes/1/contatos
```

## 15. Testes automatizados

```java
@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {
    @Mock ClienteRepository clienteRepository;
    @Mock ContatoRepository contatoRepository;
    @InjectMocks ClienteService service;

    @Test
    void contatosDeClienteInexistenteLancaExcecao() {
        when(clienteRepository.existsById(99L)).thenReturn(false);
        assertThrows(ClienteNaoEncontradoException.class, () -> service.contatosDoCliente(99L));
    }

    @Test
    void criaClienteComContatos() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        var req = new ClienteRequest("Ana", List.of(new ContatoRequest("1", "a@x.com")));
        var resp = service.criar(req);
        assertEquals("Ana", resp.nome());
        assertEquals(1, resp.contatos().size());
    }
}
```
Para integração: `@WebMvcTest` nos controllers (valida `400`) e `@DataJpaTest` + Testcontainers nos repositories.

## 16. Próximos passos

- Trocar `ddl-auto=update` por **Flyway**.
- Paginação em `GET /clientes`.
- Testes de integração com Testcontainers.
- Dockerfile da aplicação e serviço `app` no compose.

## 17. Checklist

- [ ] 4 endpoints funcionando
- [ ] `@OneToMany` / `@ManyToOne` + cascade
- [ ] DTOs de entrada e saída, sem expor entidades
- [ ] Validação (`400`) e cliente inexistente (`404`)
- [ ] Lombok
- [ ] Docker, testes, Swagger
- [ ] README + repositório no GitHub
