package io.github.suzanacavalcante.desafio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Informações gerais da documentação OpenAPI. A interface fica em {@code /swagger-ui.html} e o documento em
 * {@code /v3/api-docs}; os endpoints são descritos por anotações nos controllers e nos DTOs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("API de Clientes e Contatos")
                .version("1.0.0")
                .description("""
                        Cadastro de clientes e de seus contatos (um cliente tem vários contatos).

                        Todo erro é um `ProblemDetail` (`application/problem+json`, RFC 7807): validação \
                        responde **400** com a propriedade `erros` (campo → mensagem) e cliente inexistente \
                        responde **404**."""));
    }
}
