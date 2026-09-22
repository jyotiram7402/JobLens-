package com.joblens.api.common.exception;

import com.joblens.api.common.response.ApiError;
import com.joblens.api.common.web.CorrelationIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every exception into the single {@link ApiError} body.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} means the responses
 * Spring MVC generates itself -- unreadable JSON, wrong method, unsupported
 * media type, missing parameter -- are funnelled through the same shape rather
 * than arriving as Spring's default body. A client therefore only ever has to
 * parse one error format.
 *
 * <p>The distinction that matters here is between expected and unexpected
 * failures. An {@link ApplicationException} is expected: its message was written
 * for the caller and is returned verbatim. Anything else is a bug, so it is
 * logged in full with its correlation id and the client is told only that
 * something went wrong -- exception messages routinely contain table names,
 * SQL fragments and file paths, none of which belong in a response.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Known, deliberate failures raised by the domain modules.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiError> handleApplicationException(ApplicationException ex,
                                                               WebRequest request) {
        ErrorCode errorCode = ex.errorCode();
        log.warn("{} at {}: {}", errorCode, pathOf(request), ex.getMessage());

        ApiError body = ApiError.of(errorCode, ex.getMessage(), pathOf(request), traceId());
        return ResponseEntity.status(errorCode.status()).body(body);
    }

    /**
     * Validation of method parameters annotated directly on a controller,
     * for example {@code @RequestParam @Min(1) int page}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              WebRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            details.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(), pathOf(request), traceId(), details);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Last resort. Anything reaching here is a defect, so it is logged with the
     * stack trace and the client gets nothing but the correlation id.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception at {}", pathOf(request), ex);

        ApiError body = ApiError.of(ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(), pathOf(request), traceId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Request body validation failures, reported field by field so the frontend
     * can attach each message to its input.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> details.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(), pathOf(request), traceId(), details);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    /**
     * Every other exception {@link ResponseEntityExceptionHandler} recognises.
     * Spring passes a {@code null} body for most of them, which is where the
     * default (non-{@code ApiError}) response would otherwise come from.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        if (body instanceof ApiError) {
            return super.handleExceptionInternal(ex, body, headers, statusCode, request);
        }

        HttpStatus status = HttpStatus.resolve(statusCode.value());
        boolean clientError = status != null && status.is4xxClientError();

        if (clientError) {
            log.warn("{} at {}: {}", statusCode.value(), pathOf(request), ex.getMessage());
        } else {
            log.error("{} at {}", statusCode.value(), pathOf(request), ex);
        }

        ApiError apiError = ApiError.of(
                statusCode.value(),
                errorCodeFor(statusCode),
                clientError ? safeMessage(ex) : ErrorCode.INTERNAL_ERROR.defaultMessage(),
                pathOf(request),
                traceId());

        return super.handleExceptionInternal(ex, apiError, headers, statusCode, request);
    }

    /**
     * A stable code for statuses Spring raises itself. The reason phrase is used
     * rather than inventing an enum constant per HTTP status, so the set of
     * declared {@link ErrorCode} values stays small and meaningful.
     */
    private String errorCodeFor(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return ErrorCode.INTERNAL_ERROR.name();
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return ErrorCode.MALFORMED_REQUEST.name();
        }
        if (status == HttpStatus.NOT_FOUND) {
            return ErrorCode.RESOURCE_NOT_FOUND.name();
        }
        return status.name();
    }

    /**
     * Spring's own messages for these cases describe the request, not our
     * internals, so they are safe to return. The class name is never included.
     */
    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? "The request could not be processed" : message;
    }

    private String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }

    private String traceId() {
        return CorrelationIdFilter.currentCorrelationId();
    }
}
