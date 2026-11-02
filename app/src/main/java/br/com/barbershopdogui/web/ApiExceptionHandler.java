package br.com.barbershopdogui.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Respostas de erro padronizadas, sem vazar detalhes internos nem dados pessoais. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError e : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(e.getField(), e.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(Map.of("mensagem", "Revise os campos informados.", "erros", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> malformed() {
        return ResponseEntity.badRequest().body(Map.of("mensagem", "O formato dos dados enviados é inválido."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> bad(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", ex.getMessage() == null ? "Requisição inválida." : ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("mensagem", ex.getReason() == null ? "Não foi possível processar a solicitação." : ex.getReason()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> database(DataAccessException ex) {
        LOG.error("Erro de banco de dados: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("mensagem", "Serviço temporariamente indisponível. Tente novamente em instantes."));
    }

    /** 404, 405, parâmetro ausente etc. mantêm o status correto em vez de virar 500. */
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, String>> standardWeb(Exception ex) {
        org.springframework.http.HttpStatusCode code = ex instanceof org.springframework.web.ErrorResponse er ? er.getStatusCode() : HttpStatus.BAD_REQUEST;
        String msg = code.value() == 404 ? "Recurso não encontrado." : code.value() == 405 ? "Método não permitido." : "Requisição inválida.";
        return ResponseEntity.status(code).body(Map.of("mensagem", msg));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unknown(Exception ex) {
        LOG.error("Erro inesperado: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensagem", "Ocorreu um erro inesperado. Tente novamente mais tarde."));
    }
}
