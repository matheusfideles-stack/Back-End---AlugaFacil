package com.larenegocios.alugafacil.infra.exception;

import com.larenegocios.alugafacil.domain.pagamento.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<String> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<String> tratarRegraDeNegocio(RegraDeNegocioException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
    }

    @ExceptionHandler(TokenJWTInvalidoException.class)
    public ResponseEntity<String> tratarTokenInvalido(TokenJWTInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> tratarFalhaDeAutenticacao(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email ou senha invalidos");
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<String> tratarErroGatewayPagamento(PaymentGatewayException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<List<DadosErro>> tratarErroValidacao(MethodArgumentNotValidException ex) {
        List<DadosErro> erros = ex.getFieldErrors().stream()
                .map(this::toDadosErro)
                .toList();
        return ResponseEntity.badRequest().body(erros);
    }

    private DadosErro toDadosErro(FieldError erro) {
        return new DadosErro(erro.getField(), erro.getDefaultMessage());
    }
}
