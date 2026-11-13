package io.github.suzanacavalcante.desafio.config;

/**
 * Corpos de erro de exemplo usados nas anotações OpenAPI dos controllers (precisam ser constantes de
 * compilação). Seguem exatamente o formato que o {@link ApiExceptionHandler} devolve.
 */
public final class ExemplosDeErro {

    private ExemplosDeErro() {
    }

    public static final String VALIDACAO_400 = """
            {
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Há campos inválidos na requisição.",
              "instance": "/clientes",
              "erros": {
                "nome": "não deve estar em branco",
                "contatos[0].email": "deve ser um endereço de e-mail bem formado"
              }
            }""";

    public static final String CORPO_INVALIDO_400 = """
            {
              "title": "Corpo inválido",
              "status": 400,
              "detail": "Tipo incorreto no campo 'clienteId'.",
              "instance": "/contatos"
            }""";

    public static final String ID_INVALIDO_400 = """
            {
              "title": "Parâmetro inválido",
              "status": 400,
              "detail": "Valor inválido para o parâmetro 'id'.",
              "instance": "/clientes/abc/contatos"
            }""";

    public static final String CLIENTE_NAO_ENCONTRADO_404 = """
            {
              "title": "Cliente não encontrado",
              "status": 404,
              "detail": "Cliente 99 não encontrado",
              "instance": "/clientes/99/contatos"
            }""";
}
