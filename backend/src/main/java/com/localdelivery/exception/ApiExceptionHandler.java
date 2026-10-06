package com.localdelivery.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(OffsetDateTime timestamp, int status, String error,
                           String message, Map<String, String> fields) {}

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException error) {
        return response(HttpStatus.NOT_FOUND, error.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException error) {
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(
                item -> fields.putIfAbsent(item.getField(), item.getDefaultMessage()));
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    ResponseEntity<ApiError> invalidInput(Exception error) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid request body or parameter.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict() {
        return response(HttpStatus.CONFLICT, "Request conflicts with existing data.", Map.of());
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    ResponseEntity<ApiError> unavailable() {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Database temporarily unavailable. Try again later.", Map.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(
                OffsetDateTime.now(ZoneOffset.UTC), status.value(), status.getReasonPhrase(), message, fields));
    }
}
