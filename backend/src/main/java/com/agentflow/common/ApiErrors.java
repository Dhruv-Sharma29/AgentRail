package com.agentflow.common;

import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorView> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ErrorView(exception.getStatusCode().value(),
                exception.getReason(), Instant.now()));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorView> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorView(400, "Invalid request or workflow graph", Instant.now()));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    ResponseEntity<ErrorView> conflict(Exception exception) {
        return ResponseEntity.status(409).body(new ErrorView(409, "Concurrent change; reload and retry", Instant.now()));
    }
    public record ErrorView(int status, String message, Instant timestamp) {}
}
