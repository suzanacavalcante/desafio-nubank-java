# syntax=docker/dockerfile:1

# ---- Estágio 1: build (JDK 21 + Maven Wrapper) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Primeiro só o que define as dependências: esta camada fica em cache enquanto o pom.xml não mudar.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# O modo executável do mvnw depende do checkout (no Windows costuma vir sem ele): não confie nele.
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# Depois o código. Os testes de integração precisam de Docker (Testcontainers), que não existe aqui:
# rode `./mvnw verify` antes (no CI ou na máquina) e use a imagem só para empacotar.
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && mv target/desafio-nubank-java-*.jar /build/app.jar

# ---- Estágio 2: runtime (só o JRE 21, usuário sem privilégios) ----
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app
EXPOSE 8080
# Heap proporcional ao limite de memória do contêiner (o padrão da JVM é 25%).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
