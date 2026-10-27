package com.localdelivery.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    public record ApiError(OffsetDateTime timestamp, int status, String error,
                           String message, Map<String, String> fields) {
        public static ApiError of(HttpStatusCode status, String message, Map<String, String> fields) {
            var knownStatus = HttpStatus.resolve(status.value());
            return new ApiError(OffsetDateTime.now(ZoneOffset.UTC), status.value(),
                    knownStatus == null ? "Error" : knownStatus.getReasonPhrase(), message, fields);
        }

        /** For security filters and handlers that run outside Spring MVC. */
        public static void write(ObjectMapper json, HttpServletResponse response, HttpStatus status,
                                 String message) throws IOException {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), of(status, message, Map.of()));
        }
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    ResponseEntity<ApiError> unverified() {
        return response(HttpStatus.FORBIDDEN, "Verify your email before logging in. You can request a new verification email.", Map.of());
    }

    @ExceptionHandler({InvalidAccountTokenException.class, SelfAcceptanceException.class})
    ResponseEntity<ApiError> badRequest(RuntimeException error) {
        return response(HttpStatus.BAD_REQUEST, error.getMessage(), Map.of());
    }

    @ExceptionHandler(RateLimitException.class)
    ResponseEntity<ApiError> rateLimited(RateLimitException error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(error.retryAfter()))
                .body(errorBody(HttpStatus.TOO_MANY_REQUESTS, error.getMessage(), Map.of()));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated() {
        return response(HttpStatus.UNAUTHORIZED, "Invalid email or password. Please log in again.", Map.of());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden() {
        return response(HttpStatus.FORBIDDEN, "You are not allowed to perform this action.", Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException error) {
        return response(HttpStatus.NOT_FOUND, error.getMessage(), Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(
                item -> fields.putIfAbsent(item.getField(), item.getDefaultMessage()));
        return super.handleExceptionInternal(error,
                errorBody(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed.", fields),
                jsonHeaders(headers), HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> invalidInput() {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid request body or parameter.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict() {
        return response(HttpStatus.CONFLICT, "Request conflicts with existing data.", Map.of());
    }

    @ExceptionHandler({RequestNotOpenException.class, RequestAlreadyAcceptedException.class,
            InvalidStatusTransitionException.class})
    ResponseEntity<ApiError> invalidState(RuntimeException error) {
        return response(HttpStatus.CONFLICT, error.getMessage(), Map.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentUpdate() {
        return response(HttpStatus.CONFLICT,
                "Delivery request changed during update. Refresh and try again.", Map.of());
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    ResponseEntity<ApiError> unavailable() {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Database temporarily unavailable. Try again later.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception error) {
        logger.error("Unexpected API failure.", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.", Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message;
        if (error instanceof HttpMessageNotReadableException || error instanceof MethodArgumentTypeMismatchException) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            message = "Invalid request body or parameter.";
        } else if (status.is5xxServerError()) {
            logger.error("API request processing failed.", error);
            message = "An unexpected server error occurred.";
        } else {
            // Keep Spring's status and protocol headers, but never expose its exception details.
            message = switch (status.value()) {
                case 404 -> "Endpoint not found.";
                case 405 -> "HTTP method not allowed for this endpoint.";
                case 406 -> "Requested response format is not supported.";
                case 415 -> "Unsupported request content type. Use application/json.";
                default -> "Invalid HTTP request.";
            };
        }
        return super.handleExceptionInternal(error, errorBody(status, message, Map.of()),
                jsonHeaders(headers), status, request);
    }

    private static HttpHeaders jsonHeaders(HttpHeaders original) {
        var headers = new HttpHeaders();
        headers.putAll(original);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(errorBody(status, message, fields));
    }

    private ApiError errorBody(HttpStatusCode status, String message, Map<String, String> fields) {
        return ApiError.of(status, message, fields);
    }
}
