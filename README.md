# Desafio Nubank – Backend Java

API REST de clientes e contatos (1 cliente → N contatos), feita com **Java 21**, **Spring Boot 4.1** e **PostgreSQL**.

> README em construção. A versão completa (endpoints, decisões, exemplos) vem na issue #19.

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

No Windows (PowerShell), use `Copy-Item .env.example .env` e `.\mvnw.cmd spring-boot:run`.

### Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `DB_NAME` | `cadastro` | nome do banco |
| `DB_USER` | `postgres` | usuário (obrigatório, sem padrão na aplicação) |
| `DB_PASSWORD` | `postgres` | senha (obrigatória, sem padrão na aplicação) |
| `DB_PORT` | `5434` | porta no host (evita conflito com PostgreSQL local na 5432) |
| `DB_HOST` | `localhost` | host do banco |

- O banco só é publicado em `127.0.0.1`, não fica acessível pela rede.
- O perfil **`dev`** (`ddl-auto: update`, log do SQL) só é ativado pelo `spring-boot:run`. Em uma IDE, defina `SPRING_PROFILES_ACTIVE=dev`. O jar empacotado e os testes **não** usam o perfil `dev`.

### Testes

```bash
./mvnw verify
```

Por enquanto os testes precisam do banco no ar (`docker compose up -d`). A issue #14 troca isso por Testcontainers.

### Parar o banco

```bash
docker compose stop        # pausa (mantém os dados)
docker compose down -v     # remove container e dados
```
