package io.github.suzanacavalcante.desafio.config;

import io.github.suzanacavalcante.desafio.cliente.ClienteNaoEncontradoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tratamento global de erros: toda resposta de erro da API é um {@link ProblemDetail}
 * ({@code application/problem+json}, RFC 7807).
 * <p>
 * Cobre os erros que acontecem <b>dentro do Spring MVC</b>. Erros anteriores a ele (URL com
 * percent-encoding inválido, cabeçalho gigante) e o {@code /error} do container não passam por aqui.
 * <p>
 * Estende {@link ResponseEntityExceptionHandler}, que já converte para esse mesmo formato os erros do
 * próprio Spring MVC (método HTTP não suportado, tipo de conteúdo, rota inexistente etc.); aqui só se
 * ajustam os casos da API:
 * <ul>
 *   <li>validação ({@code @Valid}) → 400 com a propriedade {@code erros} (campo → mensagem);</li>
 *   <li>JSON malformado ou de tipo errado, e {@code {id}} de tipo errado → 400;</li>
 *   <li>{@link ClienteNaoEncontradoException} → 404;</li>
 *   <li>qualquer outra exceção → 500 genérico, logado, sem expor mensagem, classe ou stack trace.</li>
 * </ul>
 * As respostas de 400 não ecoam o valor rejeitado (só dizem o campo e o motivo).
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ClienteNaoEncontradoException.class)
    ResponseEntity<Object> clienteNaoEncontrado(ClienteNaoEncontradoException ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problema.setTitle("Cliente não encontrado");
        return handleExceptionInternal(ex, problema, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    /** Rede de segurança: erro inesperado vira 500 genérico; o detalhe fica só no log. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> erroInesperado(Exception ex, WebRequest request) {
        log.error("Erro inesperado ao processar a requisição", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente mais tarde.");
        problema.setTitle("Erro interno");
        return handleExceptionInternal(ex, problema, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // O Hibernate Validator não garante a ordem das violações: ordenar as mensagens de cada campo
        // mantém a resposta idêntica para a mesma entrada.
        Map<String, SortedSet<String>> porCampo = new TreeMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            porCampo.computeIfAbsent(erro.getField(), k -> new TreeSet<>()).add(String.valueOf(erro.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(erro ->
                porCampo.computeIfAbsent(erro.getObjectName(), k -> new TreeSet<>())
                        .add(String.valueOf(erro.getDefaultMessage())));
        Map<String, String> erros = new TreeMap<>();
        porCampo.forEach((campo, mensagens) -> erros.put(campo, String.join("; ", mensagens)));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, "Há campos inválidos na requisição.");
        problema.setTitle("Dados inválidos");
        problema.setProperty("erros", erros);
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detalhe = "Corpo da requisição ausente ou malformado.";
        if (ex.getCause() instanceof MismatchedInputException tipoErrado && !tipoErrado.getPath().isEmpty()) {
            // diz só o caminho do campo (nunca o valor), para o cliente saber o que corrigir
            detalhe = "Tipo incorreto no campo '" + caminho(tipoErrado) + "'.";
        }
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle("Corpo inválido");
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String nome = ex instanceof MethodArgumentTypeMismatchException tipo ? tipo.getName() : ex.getPropertyName();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                status, "Valor inválido para o parâmetro '" + nome + "'.");
        problema.setTitle("Parâmetro inválido");
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    /** Caminho do campo no JSON, por exemplo {@code contatos[0].telefone}. */
    private static String caminho(MismatchedInputException ex) {
        StringBuilder caminho = new StringBuilder();
        for (JacksonException.Reference ref : ex.getPath()) {
            if (ref.getPropertyName() != null) {
                caminho.append(caminho.isEmpty() ? "" : ".").append(ref.getPropertyName());
            } else {
                caminho.append('[').append(ref.getIndex()).append(']');
            }
        }
        return caminho.toString();
    }
}
