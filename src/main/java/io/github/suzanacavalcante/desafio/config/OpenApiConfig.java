package io.github.suzanacavalcante.desafio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Informações gerais da documentação OpenAPI. A interface fica em {@code /swagger-ui.html} e o documento em
 * {@code /v3/api-docs}; os endpoints são descritos por anotações nos controllers e nos DTOs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                // URL relativa: sem isto o springdoc monta servers[0].url com o cabeçalho Host da requisição,
                // que o cliente controla (Host forjado apareceria no documento)
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                    .title("API de Clientes e Contatos")
                    .version("1.0.0")
                    .description("""
                            Cadastro de clientes e de seus contatos (um cliente tem vários contatos).

                            Todo erro é um `ProblemDetail` (`application/problem+json`, RFC 7807): validação \
                            responde **400** com a propriedade `erros` (campo → mensagem) e cliente inexistente \
                            responde **404**."""));
    }

    /**
     * O springdoc traduz {@code @Size(max = N)} para {@code minLength: 0} e isso <b>sobrescreve</b> o
     * {@code minLength = 1} do {@code @Schema}; mas estes campos também têm {@code @NotBlank}: vazio e só espaços
     * dão 400. Um cliente gerado a partir do documento tem de saber que o vazio é inválido, então os campos de
     * texto obrigatórios dos corpos de entrada ficam com {@code minLength: 1}.
     */
    @Bean
    OpenApiCustomizer textoObrigatorioNaoVazio() {
        Map<String, List<String>> campos = Map.of(
                "ClienteRequest", List.of("nome"),
                "ContatoRequest", List.of("telefone", "email"),
                "NovoContatoRequest", List.of("telefone", "email"));
        return openApi -> campos.forEach((schema, propriedades) -> {
            Schema<?> definicao = openApi.getComponents().getSchemas().get(schema);
            propriedades.forEach(campo -> definicao.getProperties().get(campo).setMinLength(1));
        });
    }
}
