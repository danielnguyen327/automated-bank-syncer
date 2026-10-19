package io.github.danielnguyen327.ledgersync.web;

import io.github.danielnguyen327.ledgersync.bank.PlaidException;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errors come back as problem details (RFC 9457). The frontend shows the "detail" field as-is, so it
 * is always a sentence written for people.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  /** Plaid problems on our side: missing keys, or Plaid can't be reached. Anything else came from Plaid. */
  private static final Set<String> OUR_SIDE = Set.of("NOT_CONFIGURED", "NETWORK_ERROR");

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String detail = ex.getBindingResult().getFieldErrors().stream()
        .sorted(Comparator.comparing(FieldError::getField))
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining(" "));
    ex.getBody().setDetail(detail);
    return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
  }

  @ExceptionHandler(PlaidException.class)
  ProblemDetail handlePlaid(PlaidException ex) {
    logger.warn("Plaid error " + ex.code() + ": " + ex.getMessage()); // Plaid's messages never contain tokens
    HttpStatus status = OUR_SIDE.contains(ex.code()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
    return ProblemDetail.forStatusAndDetail(status, ex.userMessage());
  }
}
