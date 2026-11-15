# Desafio Nubank – Backend Java

[![CI](https://github.com/suzanacavalcante/desafio-nubank-java/actions/workflows/ci.yml/badge.svg)](https://github.com/suzanacavalcante/desafio-nubank-java/actions/workflows/ci.yml)

API REST de **clientes e contatos** (1 cliente → N contatos), feita como solução do desafio técnico de backend do Nubank: estruturar uma API REST com boas práticas, relacionamento entre entidades e persistência em PostgreSQL.

## Visão geral

- Cadastro de clientes (com ou sem contatos), de contatos para um cliente existente e consultas.
- Validação de entrada, erros padronizados em `ProblemDetail` (RFC 7807) e documentação interativa (Swagger).
- Listagem de clientes **paginada** e resposta **503 com `Retry-After`** quando o banco cai, com timeouts curtos (a API não trava esperando o banco).
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

# lista os clientes com seus contatos (paginado: 20 por página por padrão)
curl http://localhost:8080/clientes
```

- **Swagger UI:** http://localhost:8080/swagger-ui.html (os 4 endpoints, com exemplos e códigos de resposta).
- **Coleção de requisições:** [docs/requests.http](docs/requests.http) (VS Code com a extensão REST Client, ou IntelliJ): todas as chamadas abaixo, mais os casos de erro.
- Para rodar a aplicação direto na máquina (com JDK 21), veja [Como rodar em desenvolvimento](#como-rodar-em-desenvolvimento); para os testes, [Testes](#testes).

## API

| Método | Rota | Descrição | Sucesso | Erros |
|---|---|---|---|---|
| POST | `/clientes` | Cria cliente, com ou sem contatos | 201 | 400, 503 |
| POST | `/contatos` | Cria contato para um cliente existente (`clienteId` no corpo) | 201 | 400, 404, 503 |
| GET | `/clientes` | Lista clientes com seus contatos, **paginado** (`page`, `size`) | 200 | 400, 503 |
| GET | `/clientes/{id}/contatos` | Lista os contatos de um cliente | 200 | 400, 404, 503 |

O **503** só ocorre com o banco indisponível (veja [Banco fora do ar](#banco-fora-do-ar)).

### Paginação de `GET /clientes`

`GET /clientes?page=0&size=20` devolve uma página de clientes (por id), cada um com todos os seus contatos:

| Parâmetro | Padrão | Limites | Observação |
|---|---|---|---|
| `page` | `0` | `0` a **`10.000.000`** | a primeira página é a 0; o teto existe porque `page * size` precisa caber em um `int` |
| `size` | `20` | `1` a **`100`** | acima de 100 é **400** (não há corte silencioso) |

```json
{
  "content": [ { "id": 1, "nome": "Maria", "contatos": [ { "id": 1, "telefone": "11999990000", "email": "maria@exemplo.com", "clienteId": 1 } ] } ],
  "page": 0, "size": 20, "totalElements": 42, "totalPages": 3
}
```

- A ordem é **sempre por id**: não existe `sort` (um `?sort=...` é ignorado, como qualquer parâmetro desconhecido).
- Uma página além da última responde **200** com `content: []` (e o `totalElements` correto).
- Valor fora dos limites (inclusive `page` acima de 10.000.000) ou que não é número responde **400** no formato de erro padrão (`erros` diz qual parâmetro).
- O custo é **constante em consultas**: uma de ids, uma de contagem (dispensada quando a primeira página não está cheia) e uma dos contatos daquela página (`JOIN FETCH`), sem N+1, qualquer que seja o `size`. A paginação é em duas consultas de propósito: com `JOIN FETCH` de coleção o Hibernate paginaria em memória.
- **Mudança de contrato:** antes a resposta era uma lista JSON pura; agora é o envelope acima. Em dados que mudam entre uma página e outra (clientes novos), a paginação por `page` pode repetir ou pular itens; para este desafio isso é aceitável.

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

# Listar clientes com contatos  ->  200 (paginado: padrão page=0, size=20)
curl http://localhost:8080/clientes

# Segunda página de 10  ->  200
curl "http://localhost:8080/clientes?page=1&size=10"

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
| JSON malformado; tipo errado em um campo (a resposta diz o campo, não o valor); `{id}`, `page` ou `size` que não é número | **400** |
| `page` ou `size` fora dos limites (`size` acima de 100, `page` acima de 10.000.000) | **400**, com `erros` (parâmetro → mensagem) |
| Cliente inexistente (`POST /contatos`, `GET /clientes/{id}/contatos`) | **404** |
| Método ou rota inexistente | **405** / **404** |
| Banco de dados indisponível | **503** com cabeçalho `Retry-After: 5` (ver abaixo) |
| Erro inesperado | **500** genérico (o detalhe fica só no log) |

```json
{"title":"Dados inválidos","status":400,"detail":"Há campos inválidos na requisição.",
 "instance":"/clientes","erros":{"nome":"não deve estar em branco","contatos[0].email":"deve ser um endereço de e-mail bem formado"}}
```

#### Banco fora do ar

Se o banco cair com a aplicação no ar, as rotas que o usam respondem **503** (`application/problem+json`) com `Retry-After: 5`, em vez de esperar e devolver 500:

```json
{"title":"Serviço indisponível","status":503,"detail":"Serviço temporariamente indisponível. Tente novamente em instantes.","instance":"/clientes"}
```

- **Rápido na recusa:** o `spring.datasource.hikari.connection-timeout` é de **3 s** (variável `DB_CONNECTION_TIMEOUT_MS`), contra os 30 s padrão do Hikari. Com 30 s por requisição, as threads do Tomcat se esgotavam e até rotas que não usam o banco travavam. 3 s sobra para um banco saudável (obter conexão do pool leva milissegundos). Esse limite vale para **obter conexão do pool** (banco que recusa ou pool sem conexão livre).
- **Rápido também quando o banco trava sem recusar** (host que cai sem RST, firewall que descarta pacotes, tabela travada por lock): numa conexão já aberta o `connection-timeout` não vale, então há um `socketTimeout` de **10 s** (variável `DB_SOCKET_TIMEOUT_S`, mais `tcpKeepAlive`). Estourado, vira 503. O teste de conexões ociosas do pool também é limitado (`validation-timeout` de 1 s; o padrão do Hikari seria 5 s). Esse é o teto de uma requisição pendurada; escolha um valor bem acima da consulta mais lenta esperada (as daqui levam milissegundos).
- **Sem vazar nada:** a resposta não traz URL JDBC, host, porta nem SQL. A mensagem do driver fica só no log: o *stack trace* completo (`ERROR`, ~13 KB) aparece na **primeira** ocorrência e depois no máximo **uma vez por minuto**; as demais saem em uma linha (`WARN`) com a causa raiz, para uma queda com tráfego não encher o disco de logs.
- **Conexões mortas do pool:** logo depois da queda (ou de um reinício do banco sob carga), o pool ainda guarda conexões mortas; cada uma falha uma vez (consulta e rollback) e é descartada. Até o tamanho do pool (10) de requisições seguidas podem receber 503 mesmo com o banco já de volta, e depois normaliza. Só falhas de conexão viram 503; erro de dados (restrição, SQL) continua 500.
- **Volta sozinha:** quando o banco volta, o pool recria as conexões e a API responde normalmente, sem reiniciar (o Hikari espera um pouco entre as tentativas, então a volta pode levar alguns segundos, com 503 nesse intervalo).
- A validação (400) e as rotas que não usam o banco (`/v3/api-docs`, Swagger) continuam respondendo rápido durante a queda, **até o limite das threads do Tomcat**: medido com o banco parado, 60 requisições simultâneas não atrapalham, mas com centenas simultâneas (250 a 500) o `/v3/api-docs` chegou a levar 2 a 5 s (ainda muito melhor que os 30 s de antes). Os testes `BancoForaDoArApiTest` derrubam, congelam e religam o banco (um proxy TCP na frente do PostgreSQL do Testcontainers) e conferem tudo isso.

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
| **Paginação em duas consultas** (ids da página + `JOIN FETCH` dos contatos) | `JOIN FETCH` de coleção junto com `Pageable` paginaria **em memória**; buscar os ids primeiro mantém o `LIMIT` no banco e o número de consultas constante, sem N+1. `size` máximo 100 (acima é 400), envelope próprio em vez do `Page` do Spring. |
| **503 + `Retry-After`** e timeouts curtos com o banco fora do ar | O padrão do Hikari (30 s por requisição) esgotava as threads do Tomcat e travava até rotas que não usam o banco. Falhar rápido e avisar quando tentar de novo é melhor do que pendurar o cliente. |
| **404** (e não 400) para cliente inexistente | O recurso referenciado não existe: o erro não é de formato da requisição (400), é de recurso ausente. Vale também para `POST /contatos`, em que o `clienteId` está no corpo, mantendo uma regra única: *id de cliente que não existe → 404*. 400 fica para requisição malformada ou inválida. |
| Erros em **`ProblemDetail`** (RFC 7807), sem stack trace nem eco do valor rejeitado | Formato padrão e único para todos os erros, que o cliente da API consegue tratar de forma genérica; nada sensível vaza. |
| **Flyway** + `ddl-auto: validate` | O schema é versionado e reproduzível; o Hibernate só confere que as entidades batem com ele. |
| Credenciais **sem valor padrão** na aplicação | Segredos vêm do `.env` ou do ambiente, nunca do código. |
| Testes com **Testcontainers** | Rodam contra um PostgreSQL de verdade e descartável, sem depender de banco local, do Compose nem do `.env`. |

Diagramas (contexto, contêineres, componentes e código) em [docs/arquitetura-c4.md](docs/arquitetura-c4.md) (modelo C4).

## O que eu faria a seguir

- **Paginação por cursor** (*keyset*, `id > último`) em `GET /clientes`, se a base crescer a ponto de o `OFFSET` pesar ou de a paginação por `page` repetir/pular itens com escritas concorrentes.
- **Resiliência a banco lento ou instável**: hoje só a queda total é tratada (503 + `Retry-After`); faltam *circuit breaker*, métricas do pool e `statement_timeout` por consulta para evitar enfileirar requisições durante a oscilação.
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
| `DB_CONNECTION_TIMEOUT_MS` | `3000` | quanto uma requisição espera por conexão do banco antes de falhar com 503 (**mínimo 250**; abaixo disso a aplicação não sobe, e `0` desliga o limite: não use) |
| `DB_SOCKET_TIMEOUT_S` | `10` | segundos esperando resposta numa conexão já aberta (banco que trava sem recusar) antes de falhar com 503 |

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
