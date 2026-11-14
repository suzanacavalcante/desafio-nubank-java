# Desafio Nubank – Backend Java

[![CI](https://github.com/suzanacavalcante/desafio-nubank-java/actions/workflows/ci.yml/badge.svg)](https://github.com/suzanacavalcante/desafio-nubank-java/actions/workflows/ci.yml)

API REST de **clientes e contatos** (1 cliente → N contatos), feita como solução do desafio técnico de backend do Nubank: estruturar uma API REST com boas práticas, relacionamento entre entidades e persistência em PostgreSQL.

## Visão geral

- Cadastro de clientes (com ou sem contatos), de contatos para um cliente existente e consultas.
- Validação de entrada, erros padronizados em `ProblemDetail` (RFC 7807) e documentação interativa (Swagger).
- Schema versionado com Flyway, testes com PostgreSQL descartável (Testcontainers), CI no GitHub Actions e Docker Compose para subir tudo com um comando.

### Stack

| Camada | Tecnologia |
|---|---|
| Linguagem / runtime | Java 21 |
| Framework | Spring Boot 4.1 (Spring MVC, Spring Data JPA, Bean Validation) |
| Banco | PostgreSQL 16, migrations com Flyway |
| Documentação da API | springdoc-openapi 3.2.0 (Swagger UI) |
| Testes | JUnit 5, Mockito, `@WebMvcTest`, `@DataJpaTest`, Testcontainers |
| Build / entrega | Maven Wrapper, Dockerfile *multi-stage*, Docker Compose, GitHub Actions |

## Começando em 5 minutos

Pré-requisito: **só o Docker** (com Docker Compose). Não precisa de JDK nem de `.env`.

```bash
git clone https://github.com/suzanacavalcante/desafio-nubank-java.git
cd desafio-nubank-java
docker compose up --build
```

Quando o log mostrar `Started DesafioNubankJavaApplication`, a API está em **http://localhost:8080**:

```bash
# cria um cliente com um contato
curl -X POST http://localhost:8080/clientes -H "Content-Type: application/json" \
  -d '{"nome":"Maria","contatos":[{"telefone":"11999990000","email":"maria@exemplo.com"}]}'

# lista os clientes com seus contatos
curl http://localhost:8080/clientes
```

- **Swagger UI:** http://localhost:8080/swagger-ui.html (os 4 endpoints, com exemplos e códigos de resposta).
- **Coleção de requisições:** [docs/requests.http](docs/requests.http) (VS Code com a extensão REST Client, ou IntelliJ): todas as chamadas abaixo, mais os casos de erro.
- Para rodar a aplicação direto na máquina (com JDK 21), veja [Como rodar em desenvolvimento](#como-rodar-em-desenvolvimento); para os testes, [Testes](#testes).

## API

| Método | Rota | Descrição | Sucesso | Erros |
|---|---|---|---|---|
| POST | `/clientes` | Cria cliente, com ou sem contatos | 201 | 400 |
| POST | `/contatos` | Cria contato para um cliente existente (`clienteId` no corpo) | 201 | 400, 404 |
| GET | `/clientes` | Lista clientes com seus contatos | 200 | |
| GET | `/clientes/{id}/contatos` | Lista os contatos de um cliente | 200 | 400, 404 |

> **`GET /clientes` não é paginado.** A resposta traz a base inteira (o review do PR #28 estimou ~8 MB para 20 mil clientes). A paginação é a evolução prevista na issue [#29](https://github.com/suzanacavalcante/desafio-nubank-java/issues/29); até lá, não use esse endpoint com volumes grandes.

### Exemplos

Os exemplos com id 1 pressupõem banco vazio (o id vem da sequência). Os `curl` são para Bash; no PowerShell use `Invoke-RestMethod` (ou `curl.exe` com o JSON em um arquivo, `-d @corpo.json`), ou o [docs/requests.http](docs/requests.http).

```bash
# Criar cliente com contatos  ->  201
curl -X POST http://localhost:8080/clientes -H "Content-Type: application/json" \
  -d '{"nome":"Maria","contatos":[{"telefone":"11999990000","email":"maria@exemplo.com"}]}'
# {"id":1,"nome":"Maria","contatos":[{"id":1,"telefone":"11999990000","email":"maria@exemplo.com","clienteId":1}]}

# Criar cliente sem contatos  ->  201 (contatos: [])
curl -X POST http://localhost:8080/clientes -H "Content-Type: application/json" -d '{"nome":"João"}'

# Criar contato para o cliente 1  ->  201
curl -X POST http://localhost:8080/contatos -H "Content-Type: application/json" \
  -d '{"clienteId":1,"telefone":"11888880000","email":"outro@exemplo.com"}'

# Listar clientes com contatos  ->  200
curl http://localhost:8080/clientes

# Contatos do cliente 1  ->  200
curl http://localhost:8080/clientes/1/contatos

# Cliente inexistente  ->  404 (application/problem+json)
curl -i http://localhost:8080/clientes/999/contatos
```

### Erros

Todo erro tratado pelo Spring MVC é um `ProblemDetail` (`application/problem+json`, RFC 7807), sem stack trace e sem repetir o valor dos campos rejeitados (o `instance` traz só o caminho da requisição, e as mensagens do próprio Spring para 405 e 415 citam o método e o `Content-Type` recebidos):

| Situação | Status |
|---|---|
| Campo inválido (`@Valid`) | **400**, com `erros` (campo → mensagem, em português) |
| JSON malformado; tipo errado em um campo (a resposta diz o campo, não o valor); `{id}` que não é número | **400** |
| Cliente inexistente (`POST /contatos`, `GET /clientes/{id}/contatos`) | **404** |
| Método ou rota inexistente | **405** / **404** |
| Erro inesperado | **500** genérico (o detalhe fica só no log) |

```json
{"title":"Dados inválidos","status":400,"detail":"Há campos inválidos na requisição.",
 "instance":"/clientes","erros":{"nome":"não deve estar em branco","contatos[0].email":"deve ser um endereço de e-mail bem formado"}}
```

Ficam fora do formato os erros que acontecem **antes** do MVC ou fora dele: URL com percent-encoding inválido (`/clientes/%zz`) e cabeçalhos gigantes (página de erro do Tomcat), `TRACE` e `GET /error` direto (JSON padrão do Spring Boot). As mensagens do próprio Spring MVC (rota inexistente, método não suportado) saem em inglês.

### Limites da validação

| Campo | Regra |
|---|---|
| `nome` | obrigatório, até **150** caracteres |
| `telefone` | obrigatório, até **30** caracteres |
| `email` | obrigatório, formato de e-mail válido, até **254** caracteres |
| `contatos` (em `POST /clientes`) | opcional, no máximo **50** contatos, nenhum nulo |
| `clienteId` (em `POST /contatos`) | obrigatório |

`nome` e `telefone` também não aceitam o caractere NUL nem surrogate UTF-16 solto. Os tamanhos contam unidades UTF-16 (um emoji conta 2).

## Decisões de design

| Decisão | Por quê |
|---|---|
| **Pacotes por feature** (`cliente`, `contato`) em vez de por camada | Cada feature carrega seu controller, service, repository, mapper e DTOs: o que muda junto fica junto, e dá para entender uma feature abrindo uma pasta. Pacotes transversais só para o que é compartilhado: `config` (erros), `validacao` e `openapi`. |
| **DTOs em `record`**, a API nunca expõe entidades | Evita `LazyInitializationException` na serialização, recursão JSON cliente ↔ contato e vazamento do modelo de persistência para o contrato. O contrato da API muda só quando o DTO muda. |
| `open-in-view: false` e mapeamento para DTO **dentro** da transação do service | Acesso preguiçoso só onde há transação; se alguém mapear fora, falha cedo em vez de gerar consultas escondidas na camada web. |
| `JOIN FETCH` na listagem | Uma consulta em vez de N+1. O custo é a resposta inteira em memória: por isso a paginação (#29) é a evolução natural. |
| **404** (e não 400) para cliente inexistente | O recurso referenciado não existe: o erro não é de formato da requisição (400), é de recurso ausente. Vale também para `POST /contatos`, em que o `clienteId` está no corpo, mantendo uma regra única: *id de cliente que não existe → 404*. 400 fica para requisição malformada ou inválida. |
| Erros em **`ProblemDetail`** (RFC 7807), sem stack trace nem eco do valor rejeitado | Formato padrão e único para todos os erros, que o cliente da API consegue tratar de forma genérica; nada sensível vaza. |
| **Flyway** + `ddl-auto: validate` | O schema é versionado e reproduzível; o Hibernate só confere que as entidades batem com ele. |
| Credenciais **sem valor padrão** na aplicação | Segredos vêm do `.env` ou do ambiente, nunca do código. |
| Testes com **Testcontainers** | Rodam contra um PostgreSQL de verdade e descartável, sem depender de banco local, do Compose nem do `.env`. |

Diagramas (contexto, contêineres, componentes e código) em [docs/arquitetura-c4.md](docs/arquitetura-c4.md) (modelo C4).

## O que eu faria a seguir

- **Paginação** em `GET /clientes` (#29), com ordenação estável e limite de página máximo; ajustar o *healthcheck* do Compose, que hoje lê a tabela inteira.
- **Resiliência a queda do banco** (#34): timeout curto de conexão e resposta **503** com `Retry-After`, em vez de 500.
- **Operações que faltam ao CRUD**: buscar, atualizar e remover clientes e contatos (o desafio pede só cadastro e consulta); ao remover, a FK já tem `ON DELETE CASCADE`.
- **Autenticação e autorização** (por exemplo, OAuth2/JWT com Spring Security), e *rate limiting* nas rotas de escrita.
- **Observabilidade**: Actuator com *health* e métricas, logs estruturados e *correlation id* por requisição.
- **Separar o usuário de migration do de execução** (permissão de DDL só para o Flyway) e proteger o `/swagger-ui.html` e o `/v3/api-docs` fora de desenvolvimento.
- **Microsserviços**: só se o domínio crescer a ponto de justificar. Hoje clientes e contatos formam um único agregado (`Cliente` é a raiz), com transação e consistência simples; dividir agora trocaria isso por chamadas de rede e consistência eventual sem ganho.

## Como rodar em desenvolvimento

Pré-requisitos: JDK 21 e Docker (com Docker Compose).

```bash
# 1. Variáveis de ambiente (lidas pelo Compose e pela aplicação)
cp .env.example .env

# 2. Banco PostgreSQL (só o serviço db; o serviço app é a opção da próxima seção)
docker compose up -d --wait db

# 3. Aplicação (ativa o perfil dev automaticamente)
./mvnw spring-boot:run
```

No Windows (PowerShell), use `Copy-Item .env.example .env` e `.\mvnw.cmd spring-boot:run`. Os `curl` da seção Exemplos são para Bash; no PowerShell use `Invoke-RestMethod` (ou `curl.exe` com o JSON em um arquivo, `-d @corpo.json`).

### Tudo em contêineres (banco + API)

Pré-requisito: só o Docker. Não precisa de JDK nem de `.env` (os padrões do Compose servem; para mudar usuário, senha ou portas, crie o `.env` como acima).

```bash
docker compose up --build
```

Sobe o PostgreSQL, espera o *healthcheck* dele e então sobe a API em `http://localhost:8080` (Swagger em `/swagger-ui.html`). As migrations rodam na subida.

- A imagem (`Dockerfile`) é *multi-stage*: compila com JDK 21 + Maven Wrapper e roda só com o JRE 21, como usuário sem privilégios. O build **não executa os testes** (eles precisam de Docker, que não existe dentro do build): rode `./mvnw verify` antes.
- Dentro da rede do Compose a API fala com o banco em `db:5432`; `DB_PORT` (padrão 5434) é só a porta publicada no host. `APP_PORT` (padrão 8080) muda a porta da API no host. Os dois ficam acessíveis apenas em `127.0.0.1`.
- Após mudar o código, rode `docker compose up --build` de novo. Para parar: `docker compose down` (ou `down -v` para apagar também os dados).
- Esta opção **não** usa o perfil `dev` (sem log de SQL). A API tem limite de 512 MB de memória (`mem_limit`).
- **Se a API ficar reiniciando** (`docker compose ps` mostra `restarting`), veja `docker compose logs app`. A causa mais comum é um volume `pgdata` criado antes do Flyway (veja "Banco de dados e migrations") ou um `DB_PASSWORD` alterado depois que o volume foi criado (o PostgreSQL mantém a senha antiga: use `docker compose down -v`).

### Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `DB_NAME` | `cadastro` | nome do banco |
| `DB_USER` | `postgres` | usuário (obrigatório, sem padrão na aplicação) |
| `DB_PASSWORD` | `postgres` | senha (obrigatória, sem padrão na aplicação) |
| `DB_PORT` | `5434` | porta no host (evita conflito com PostgreSQL local na 5432) |
| `DB_HOST` | `localhost` | host do banco |

- No `.env`, escreva os valores **sem aspas**, sem `export` e sem comentário na mesma linha (o Compose e o Spring leem o arquivo de formas diferentes). Detalhes no próprio `.env.example`.
- O banco só é publicado em `127.0.0.1`, não fica acessível pela rede.
- O perfil **`dev`** (log do SQL) só é ativado pelo `spring-boot:run`. Em uma IDE, defina `SPRING_PROFILES_ACTIVE=dev`. O jar empacotado e os testes **não** usam o perfil `dev`.

### Banco de dados e migrations

O schema é criado pelo **Flyway** na subida da aplicação (`src/main/resources/db/migration`, arquivos `V<n>__descricao.sql`); o Hibernate só **valida** (`ddl-auto: validate`) e a aplicação não sobe se uma entidade divergir das migrations. Para mudar o schema, crie uma nova migration (`V2__...sql`); **nunca edite uma já aplicada** (o Flyway acusa o checksum alterado).

- **Banco já existente criado antes do Flyway** (pelo `ddl-auto: update` antigo): a aplicação não sobe e **não altera nenhum dado**; o Flyway diz `Found non-empty schema(s) "public" but no schema history table. Use baseline() or set baselineOnMigrate to true`. **Não** use `baselineOnMigrate`: ele marcaria o banco como se a V1 já estivesse aplicada e esconderia qualquer divergência entre o banco e as entidades. Recrie o banco de desenvolvimento: `docker compose down -v && docker compose up -d` (apaga os dados) e suba a aplicação de novo.
- **Permissão de DDL:** as migrations rodam na subida com as mesmas credenciais da aplicação (`DB_USER`), que portanto precisa poder criar objetos no schema `public`. Quem separar um usuário de migration de um de execução em produção deve configurar `spring.flyway.user`/`password` à parte.
- O `validate` do Hibernate não confere tamanho de coluna, índices nem chaves: mudanças desse tipo numa nova migration devem vir com teste equivalente ao `MigrationsFlywayTest`.

## Testes

```bash
./mvnw verify
```

Cobrem entidades, mappers, services (Mockito), controllers (`@WebMvcTest`), repositórios (`@DataJpaTest`) e a API de ponta a ponta.

**Pré-requisito: Docker.** Os testes de integração usam o **Testcontainers** com `postgres:16`: sobem um PostgreSQL descartável sozinhos (um por execução, compartilhado entre os testes), então **não** dependem de banco local, do Docker Compose nem do `.env`, e não tocam no banco de desenvolvimento. Os testes sem banco (unitários e `@WebMvcTest`) não precisam de Docker.

> No Windows com o Docker instalado só no WSL, rode o `./mvnw verify` **dentro do WSL** (o Testcontainers precisa alcançar o daemon do Docker).
> Dentro do WSL, prefira um clone/cópia do projeto **no disco do WSL** (`~/...`): em `/mnt/e/...` o `verify` levou 1 min 18 s contra ~25 s no disco do WSL.

**Regra:** toda classe de teste que usa banco precisa da anotação `@UsaPostgresDeTeste` (pacote `suporte`). Há uma guarda em `src/test/resources/application.properties`: sem a anotação, a classe tenta conectar numa URL inválida e falha com `Connection refused`, em vez de conectar silenciosamente no banco de desenvolvimento do `.env` (alguns testes fazem `truncate`).

Testes de regressão de bugs conhecidos e ainda abertos devem ter a tag `bug-conhecido` e ficam fora por padrão. Para rodá-los:

```bash
./mvnw test -Dgroups=bug-conhecido -DexcludedGroups=nenhum
```

### Integração contínua

O workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) roda em todo PR e em todo push na `main`, com dois jobs:

- **Build e testes:** JDK 21 (Temurin, com cache do Maven) e `./mvnw -B verify`. O Testcontainers funciona no runner `ubuntu-24.04`, que já tem Docker. Se falhar, os relatórios do Surefire ficam como artefato da execução.
- **Imagem Docker e Compose:** `docker compose up --build -d --wait` (constrói a imagem do `Dockerfile`, que o `verify` não exercita, e sobe banco + API do zero) e confere `GET /clientes` = 200.

O **Dependabot** (`.github/dependabot.yml`) abre PRs semanais para Maven, GitHub Actions e as imagens do Docker (`Dockerfile` e `docker-compose.yml`). Em Maven e Docker, saltos de versão **maior** (outro JDK, outra versão do PostgreSQL ou do Spring Boot) são ignorados: são decisão manual; minor e patch chegam normalmente. O springdoc, que o BOM do Spring Boot não gerencia, também é acompanhado por ele.

## Parar o banco (e a API)

```bash
docker compose stop        # pausa (mantém os dados)
docker compose down -v     # remove contêineres e dados
```
