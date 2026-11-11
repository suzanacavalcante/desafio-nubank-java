# Desafio Nubank – Backend Java

API REST de **clientes e contatos** (1 cliente → N contatos), feita com **Java 21**, **Spring Boot 4.1** e **PostgreSQL**.

> README em evolução. A versão final (Swagger, CI, exemplos completos) vem na issue #19.

## Funcionalidades

| Método | Rota | Descrição | Sucesso |
|---|---|---|---|
| POST | `/clientes` | Cria cliente, com ou sem contatos | 201 |
| POST | `/contatos` | Cria contato para um cliente existente (`clienteId` no corpo) | 201 |
| GET | `/clientes` | Lista clientes com seus contatos | 200 |
| GET | `/clientes/{id}/contatos` | Lista os contatos de um cliente | 200 |

### Exemplos

```bash
# Criar cliente com contatos (os exemplos com id 1 pressupõem banco vazio: o id vem da sequência)
curl -X POST http://localhost:8080/clientes -H "Content-Type: application/json" \
  -d '{"nome":"Maria","contatos":[{"telefone":"11999990000","email":"maria@exemplo.com"}]}'

# Criar contato para o cliente 1
curl -X POST http://localhost:8080/contatos -H "Content-Type: application/json" \
  -d '{"clienteId":1,"telefone":"11888880000","email":"outro@exemplo.com"}'

# Listar clientes com contatos
curl http://localhost:8080/clientes

# Contatos do cliente 1
curl http://localhost:8080/clientes/1/contatos
```

### Erros

Toda resposta de erro é um `ProblemDetail` (`application/problem+json`, RFC 7807), sem stack trace e sem ecoar o valor rejeitado:

| Situação | Status |
|---|---|
| Campo inválido (`@Valid`) | **400**, com `erros` (campo → mensagem, em português) |
| JSON malformado ou de tipo errado; `{id}` que não é número | **400** |
| Cliente inexistente (`POST /contatos`, `GET /clientes/{id}/contatos`) | **404** |
| Método ou rota inexistente | **405** / **404** |
| Erro inesperado | **500** genérico (o detalhe fica só no log) |

```json
{"title":"Dados inválidos","status":400,"detail":"Há campos inválidos na requisição.",
 "instance":"/clientes","erros":{"nome":"não deve estar em branco","contatos[0].email":"deve ser um endereço de e-mail bem formado"}}
```

### Limitações atuais

Ainda **não** implementados (planejados nas issues):

- Swagger/OpenAPI (#15), Flyway (#16), Dockerfile (#17) e GitHub Actions (#18)
- A listagem `GET /clientes` não tem paginação

## Arquitetura

Organizada **por feature** (`cliente`, `contato`, `config`) em camadas Controller → Service → Repository, com DTOs em `record` e mapeamento dentro da transação (`open-in-view: false`). Detalhes e diagramas em [docs/arquitetura-c4.md](docs/arquitetura-c4.md) (modelo C4).

## Como rodar em desenvolvimento

Pré-requisitos: JDK 21 e Docker (com Docker Compose).

```bash
# 1. Variáveis de ambiente (lidas pelo Compose e pela aplicação)
cp .env.example .env

# 2. Banco PostgreSQL
docker compose up -d --wait

# 3. Aplicação (ativa o perfil dev automaticamente)
./mvnw spring-boot:run
```

No Windows (PowerShell), use `Copy-Item .env.example .env` e `.\mvnw.cmd spring-boot:run`. Os `curl` da seção Exemplos são para Bash; no PowerShell use `Invoke-RestMethod` (ou `curl.exe` com o JSON em um arquivo, `-d @corpo.json`).

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
- O perfil **`dev`** (`ddl-auto: update`, log do SQL) só é ativado pelo `spring-boot:run`. Em uma IDE, defina `SPRING_PROFILES_ACTIVE=dev`. O jar empacotado e os testes **não** usam o perfil `dev`.

## Testes

```bash
./mvnw verify
```

Cobrem entidades, mappers, services (Mockito), controllers (`@WebMvcTest`), repositórios (`@DataJpaTest`) e a API de ponta a ponta. Por enquanto os testes que sobem o contexto precisam do banco no ar (`docker compose up -d`); a issue #14 troca isso por Testcontainers.

Testes de regressão de bugs conhecidos e ainda abertos devem ter a tag `bug-conhecido` e ficam fora por padrão. Para rodá-los:

```bash
./mvnw test -Dgroups=bug-conhecido -DexcludedGroups=nenhum
```

## Parar o banco

```bash
docker compose stop        # pausa (mantém os dados)
docker compose down -v     # remove container e dados
```
