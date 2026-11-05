# Issues do projeto: Desafio Nubank (Backend Java)

> Proposta para você revisar e opinar. Nada foi implementado ainda.
> Referências: [desafio-nubank-java.md](desafio-nubank-java.md) (guia técnico) e [../../desafio-backend-nubank.md](../../desafio-backend-nubank.md) (enunciado).

**Convenções**
- Labels: `setup`, `feature`, `validation`, `tests`, `docs`, `devops`, `refactor`, `diferencial`.
- Tamanho: **P** (até ~1h), **M** (algumas horas), **G** (meio dia ou mais).
- Cada issue deve virar um branch e um PR pequeno, com commits no padrão Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).

## Visão geral

| Marco | Objetivo | Issues |
|---|---|---|
| **M1 – Fundação** | Projeto roda e conecta no banco | #1 a #3 |
| **M2 – Domínio e API** | 4 endpoints do enunciado funcionando | #4 a #9 |
| **M3 – Qualidade** | Validação, erros e testes | #10 a #14 |
| **M4 – Diferenciais e entrega** | Swagger, Docker, Flyway, CI, README | #15 a #19 |

```
#1 → #2 → #3 → #4 → #5 → #6 → #7 → #8 → #9
                                   ↓
                      #10 → #11 → #12 → #13 → #14
                                   ↓
                      #15, #16, #17, #18, #19 (independentes entre si)
```

---

# M1 – Fundação

## #1 Inicializar projeto Spring Boot
**Labels:** `setup` · **Tamanho:** P

**Descrição:** Criar o esqueleto do projeto e a estrutura de pacotes.

**Tarefas**
- [ ] Gerar projeto no Spring Initializr: Maven, Java 21, Spring Boot **4.1.2** (última versão estável disponível; evitar SNAPSHOT e RC)
- [ ] Atenção: o Boot 4 usa Spring Framework 7, Hibernate 7 e Jackson 3, com starters modularizados. Usar o `pom.xml` gerado pelo Initializr como fonte de verdade e **não copiar** dependências de guias baseados no Boot 3.x
- [ ] Dependências: Web, Data JPA, Validation, PostgreSQL Driver, Lombok, DevTools
- [ ] Criar pacotes por feature: `cliente`, `contato`, `config`
- [ ] Adicionar `.gitignore` (Java/Maven/IDE) e `.editorconfig`
- [ ] Confirmar que `./mvnw verify` passa

**Critérios de aceite**
- Projeto compila e o contexto Spring sobe (ainda sem banco, ou com banco já levantado na #2)
- Estrutura de pastas segue o guia

---

## #2 Banco PostgreSQL com Docker Compose e configuração por ambiente
**Labels:** `setup`, `devops` · **Tamanho:** P · **Depende de:** #1

**Descrição:** Subir o Postgres localmente e configurar a conexão sem credenciais fixas no código.

**Tarefas**
- [ ] `docker-compose.yml` com `postgres:16`, banco `cadastro`, volume e healthcheck
- [ ] `application.yml` lendo `DB_URL`, `DB_USER`, `DB_PASSWORD` com defaults para dev
- [ ] Arquivo `.env.example` (e `.env` no `.gitignore`)
- [ ] `ddl-auto=update` apenas no perfil `dev` (ver decisão D2)

**Critérios de aceite**
- `docker compose up -d` + `./mvnw spring-boot:run` conectam no banco
- Nenhuma senha real versionada

---

## #3 Modelar entidades Cliente e Contato
**Labels:** `feature` · **Tamanho:** M · **Depende de:** #2

**Descrição:** Mapear o relacionamento 1:N com JPA.

**Tarefas**
- [ ] `Cliente`: `id` (IDENTITY), `nome` (not null), `contatos` (`@OneToMany(mappedBy="cliente", cascade=ALL, orphanRemoval=true)`)
- [ ] `Contato`: `id` (IDENTITY), `telefone` e `email` (not null), `cliente` (`@ManyToOne(fetch=LAZY, optional=false)` + `@JoinColumn(name="cliente_id")`)
- [ ] Método `Cliente.adicionarContato(...)` mantendo os dois lados sincronizados
- [ ] Usar Lombok (`@Getter @Setter @NoArgsConstructor`); **evitar `@Data`** nas entidades (equals/hashCode e relacionamentos)
- [ ] Confirmar as tabelas e a FK criadas no Postgres

**Critérios de aceite**
- Aplicação sobe e cria as tabelas `clientes` e `contatos` com FK
- Excluir um cliente exclui seus contatos

---

# M2 – Domínio e API

## #4 Criar DTOs e mappers
**Labels:** `feature`, `refactor` · **Tamanho:** M · **Depende de:** #3

**Descrição:** Separar entrada/saída da API das entidades.

**Tarefas**
- [ ] Records de entrada: `ClienteRequest(nome, contatos?)`, `ContatoRequest(telefone, email)`, `NovoContatoRequest(clienteId, telefone, email)`
- [ ] Records de saída: `ClienteResponse(id, nome, contatos)`, `ContatoResponse(id, telefone, email, clienteId)`
- [ ] `ClienteMapper` com métodos estáticos (entidade ↔ DTO), sem duplicar conversões
- [ ] Teste unitário simples do mapper

**Critérios de aceite**
- Nenhuma entidade é exposta pela API
- Uma única implementação de cada conversão

---

## #5 Criar repositories
**Labels:** `feature` · **Tamanho:** P · **Depende de:** #3

**Tarefas**
- [ ] `ClienteRepository extends JpaRepository<Cliente, Long>` com `findAllComContatos()` (`JOIN FETCH` para evitar N+1)
- [ ] `ContatoRepository extends JpaRepository<Contato, Long>` com `findByClienteId(Long)`

**Critérios de aceite**
- `findAllComContatos` retorna clientes sem duplicados e sem queries extras por cliente (verificar no log do SQL)

---

## #6 `POST /clientes`: cadastrar cliente (com ou sem contatos)
**Labels:** `feature` · **Tamanho:** M · **Depende de:** #4, #5

**Descrição:** Cria um cliente; se vier lista de contatos, cria todos na mesma transação.

**Tarefas**
- [ ] `ClienteService.criar(ClienteRequest)` com `@Transactional`
- [ ] `ClienteController.criar` retornando **201** + `ClienteResponse`
- [ ] Aceitar `contatos` ausente, `null` ou vazio

**Critérios de aceite**
- Cliente só com `nome` → 201 com `contatos: []`
- Cliente com 2 contatos → 201 e os 2 persistidos
- Falha ao salvar um contato não deixa cliente "pela metade" (rollback)

---

## #7 `POST /contatos`: cadastrar contato para cliente existente
**Labels:** `feature` · **Tamanho:** M · **Depende de:** #6

**Tarefas**
- [ ] `ContatoService.criar(NovoContatoRequest)`: busca o cliente; se não existir, lança `ClienteNaoEncontradoException`
- [ ] `ContatoController.criar` retornando **201** + `ContatoResponse`
- [ ] Definir a classe `ClienteNaoEncontradoException` (o mapeamento para HTTP fica na #11)

**Critérios de aceite**
- `clienteId` existente → 201 e contato associado
- `clienteId` inexistente → exceção de domínio (HTTP 404 após a #11)

---

## #8 `GET /clientes`: listar clientes com contatos
**Labels:** `feature` · **Tamanho:** P · **Depende de:** #6

**Tarefas**
- [ ] `ClienteService.listar()` com `@Transactional(readOnly = true)` usando `findAllComContatos()`
- [ ] `ClienteController.listar` retornando **200**

**Critérios de aceite**
- Cada cliente vem com sua lista de contatos (vazia, se não houver)
- Uma única consulta SQL principal

---

## #9 `GET /clientes/{id}/contatos`: listar contatos de um cliente
**Labels:** `feature` · **Tamanho:** P · **Depende de:** #7

**Tarefas**
- [ ] `ClienteService.contatosDoCliente(id)`: valida existência e retorna os contatos
- [ ] Endpoint retornando **200** com `List<ContatoResponse>`

**Critérios de aceite**
- Cliente existente sem contatos → 200 com `[]`
- Cliente inexistente → 404 (após a #11), **não** lista vazia

---

# M3 – Qualidade

## #10 Validação de entrada (Bean Validation)
**Labels:** `validation` · **Tamanho:** M · **Depende de:** #6, #7

**Tarefas**
- [ ] `@NotBlank` em `nome`, `telefone`, `email`; `@Email` em `email`; `@NotNull` em `clienteId`
- [ ] `@Valid` nos `@RequestBody` e nos contatos aninhados (`@Valid List<ContatoRequest>`)
- [ ] Definir tamanhos máximos (`@Size`) coerentes com as colunas (ver D6)

**Critérios de aceite**
- Campos em branco/nulos/e-mail malformado são rejeitados antes de chegar ao banco
- Validação funciona também para contatos dentro de `POST /clientes`

---

## #11 Tratamento global de erros
**Labels:** `validation`, `feature` · **Tamanho:** M · **Depende de:** #10

**Descrição:** Padronizar respostas de erro.

**Tarefas**
- [ ] `@RestControllerAdvice` (`ApiExceptionHandler`)
- [ ] `MethodArgumentNotValidException` → **400** com mapa `campo → mensagem`
- [ ] JSON malformado (`HttpMessageNotReadableException`) → **400**
- [ ] `ClienteNaoEncontradoException` → **404**
- [ ] `{id}` inválido (tipo errado) → **400**
- [ ] Qualquer outra exceção → **500** genérico, **sem vazar stack trace**, com log do erro
- [ ] Formato único de erro (ver D4: `ProblemDetail` RFC 7807 ou JSON próprio)

**Critérios de aceite**
- Nenhum endpoint devolve 500 por erro do cliente
- Todas as respostas de erro têm o mesmo formato

---

## #12 Testes unitários dos services
**Labels:** `tests` · **Tamanho:** M · **Depende de:** #9

**Tarefas**
- [ ] JUnit 5 + Mockito para `ClienteService` e `ContatoService`
- [ ] Casos: criar com/sem contatos; listar; contatos de cliente inexistente; criar contato com cliente inexistente
- [ ] Nomes de teste descritivos (`deveLancarExcecaoQuandoClienteNaoExiste`)

**Critérios de aceite**
- Todos os caminhos de sucesso e erro dos services cobertos

---

## #13 Testes da camada web (`@WebMvcTest`)
**Labels:** `tests` · **Tamanho:** M · **Depende de:** #11

**Tarefas**
- [ ] Testes dos dois controllers com `MockMvc` e service mockado (no Boot 4 os pacotes/starters de teste foram modularizados: conferir imports de `@WebMvcTest` e a dependência de teste correta)
- [ ] Verificar status e corpo para: 201, 200, 400 (campos inválidos, JSON quebrado), 404

**Critérios de aceite**
- Cada regra de validação da #10 tem pelo menos um teste
- Formato do erro da #11 está coberto

---

## #14 Testes de integração com Testcontainers
**Labels:** `tests` · **Tamanho:** G · **Depende de:** #13

**Tarefas**
- [ ] Testcontainers com `postgres:16`
- [ ] `@DataJpaTest` para `findAllComContatos` e cascade/orphanRemoval
- [ ] `@SpringBootTest` fluxo ponta a ponta: criar cliente → criar contato → listar → listar contatos
- [ ] Teste verificando que a listagem não gera N+1 (contagem de queries ou análise do log)

**Critérios de aceite**
- `./mvnw verify` roda tudo localmente com Docker disponível
- Testes não dependem de dados pré-existentes

---

# M4 – Diferenciais e entrega

## #15 Documentação com Swagger/OpenAPI
**Labels:** `diferencial`, `docs` · **Tamanho:** P · **Depende de:** #11

**Tarefas**
- [ ] `springdoc-openapi-starter-webmvc-ui`, **usando uma versão compatível com Spring Boot 4** (a série 2.x do springdoc é para Boot 3.x; conferir a matriz de compatibilidade na documentação do springdoc)
- [ ] `@Operation`, `@ApiResponse` nos endpoints (200/201/400/404), exemplos nos DTOs
- [ ] Informações gerais (título, versão) via `OpenAPI` bean

**Critérios de aceite**
- `http://localhost:8080/swagger-ui.html` lista os 4 endpoints com exemplos e códigos de erro

---

## #16 Migrations com Flyway
**Labels:** `diferencial`, `devops` · **Tamanho:** M · **Depende de:** #3

**Tarefas**
- [ ] Dependência `flyway-core` + `flyway-database-postgresql`
- [ ] `V1__cria_tabelas.sql` (tabelas, FK com `ON DELETE CASCADE`, índice em `contatos.cliente_id`)
- [ ] `spring.jpa.hibernate.ddl-auto=validate`
- [ ] Remover `ddl-auto=update`

**Critérios de aceite**
- Banco novo é criado só pelo Flyway; Hibernate apenas valida o schema
- Testes de integração (#14) passam usando as migrations

---

## #17 Dockerfile e aplicação no Compose
**Labels:** `diferencial`, `devops` · **Tamanho:** M · **Depende de:** #2

**Tarefas**
- [ ] Dockerfile multi-stage (build com Maven, runtime com JRE 21 e usuário não-root)
- [ ] Serviço `app` no `docker-compose.yml` com `depends_on` + healthcheck do banco
- [ ] `.dockerignore`

**Critérios de aceite**
- `docker compose up --build` sobe banco + API do zero e a API responde em `:8080`

---

## #18 Integração contínua (GitHub Actions)
**Labels:** `diferencial`, `devops` · **Tamanho:** P · **Depende de:** #14

**Tarefas**
- [ ] Workflow: checkout → JDK 21 → cache Maven → `./mvnw verify`
- [ ] Disparar em `push` e `pull_request`
- [ ] Badge de status no README

**Critérios de aceite**
- Build verde no GitHub a cada PR (Testcontainers funciona no runner `ubuntu-latest`)

---

## #19 README final e coleção de requisições
**Labels:** `docs` · **Tamanho:** M · **Depende de:** todas as anteriores

**Tarefas**
- [ ] README: visão geral, stack, como rodar (Docker e local), como testar, endpoints com exemplos `curl`
- [ ] Decisões de arquitetura (por que por feature, por que DTOs, por que 404 em vez de 400)
- [ ] Arquivo `docs/requests.http` para REST Client/IntelliJ
- [ ] Seção "O que eu faria a seguir" (paginação, autenticação, microsserviços)

**Critérios de aceite**
- Uma pessoa nova consegue clonar, subir e testar a API em menos de 5 minutos só lendo o README

---

# Backlog opcional (fora do escopo mínimo)

| # | Ideia | Observação |
|---|---|---|
| B1 | Paginação em `GET /clientes` | `Pageable` + `Page<ClienteResponse>`; cuidado com `JOIN FETCH` + paginação (usar 2 consultas) |
| B2 | `PUT`/`DELETE` de cliente e contato | Não pedido no enunciado; só se sobrar tempo |
| B3 | E-mail único por cliente | Exige constraint + erro `409 Conflict` |
| B4 | Observabilidade | Spring Actuator, logs estruturados |
| B5 | Segurança básica | Spring Security + JWT (provavelmente excesso para esta etapa) |

---

# Decisões em aberto: quero sua opinião

| # | Pergunta | Opções | Minha sugestão |
|---|---|---|---|
| **D1** | Organização dos pacotes | (a) por camada (`controller/`, `service/`…) · (b) por feature (`cliente/`, `contato/`) | **(b)**: é a "visão sênior" do vídeo e facilita evoluir para microsserviços. Para uma avaliação rápida, (a) é mais familiar ao avaliador. |
| **D2** | Schema do banco | (a) `ddl-auto=update` desde o início · (b) Flyway desde a #3 | **(a)** no começo para andar rápido e migrar na #16. Se preferir fazer certo desde o início, mova a #16 para o M1. |
| **D3** | DTOs | (a) `record` · (b) classes com Lombok | **(a)**: menos código. O Lombok continua sendo usado nas entidades, cumprindo o requisito. |
| **D4** | Formato de erro | (a) `ProblemDetail` (RFC 7807, nativo do Spring 6) · (b) JSON próprio | **(a)** para 404/500; para validação, adicionar o mapa `campo → mensagem` como propriedade extra. |
| **D5** | Cliente inexistente em `POST /contatos` | (a) 404 · (b) 400 (como no vídeo) | **(a)**: o recurso referenciado não existe. (b) também é defensável, pois o erro está no corpo da requisição. |
| **D6** | Regras de campo | Limites de tamanho de `nome`/`email`/`telefone`; formato de telefone; e-mail duplicado | Só `@Size(max=…)` agora; formato de telefone e unicidade ficam no backlog (B3). |
| **D7** | Rota de criação de contato | (a) `POST /contatos` com `clienteId` no corpo (enunciado) · (b) `POST /clientes/{id}/contatos` | **(a)**, como pede o enunciado. Opcionalmente oferecer (b) também. |
| **D8** | Versão do Java | 17 (vídeo) ou 21 (LTS) | **Decidido: 21.** LTS, suportada pelo Boot 4.1 e com recursos como records, pattern matching e virtual threads. |
| **D9** | Granularidade das issues | 19 issues (esta proposta) ou menos, juntando #4+#5, #8+#9, #12+#13 | Manter assim para PRs pequenos e histórico claro; juntar se for ser mais rápido. |
| **D10** | Gestão | Criar as issues no GitHub Projects (board Kanban) ou só no arquivo | Board simples com colunas `Backlog / Em andamento / Em revisão / Feito`. |

---

## Estimativa total

| Marco | Esforço aproximado |
|---|---|
| M1 | 2–3 h |
| M2 | 6–8 h |
| M3 | 8–10 h |
| M4 | 6–8 h |
| **Total** | **~22–29 h** (o núcleo do enunciado, M1 + M2, fica em ~8–11 h) |

**Caminho mínimo para entregar:** #1 → #9, mais #10, #11 e #12. Os demais itens são diferenciais.
