package com.joblens.api.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.joblens.api.common.exception.ErrorCode;

import java.time.Instant;
import java.util.Map;

/**
 * The single error body returned by every failing API call.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-22T10:15:30Z",
 *   "status": 400,
 *   "error": "VALIDATION_ERROR",
 *   "message": "Request validation failed",
 *   "path": "/api/v1/companies",
 *   "traceId": "6f1c2b9e4a7d4c31",
 *   "details": { "name": "must not be blank" }
 * }
 * </pre>
 *
 * <p>{@code details} is omitted entirely when empty, so a simple failure stays
 * a simple response. {@code traceId} is the correlation identifier attached by
 * {@link com.joblens.api.common.web.CorrelationIdFilter}, which lets a user
 * quote an error and lets us find the exact request in the logs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId,
        Map<String, String> details
) {

    public static ApiError of(ErrorCode errorCode, String message, String path, String traceId) {
        return new ApiError(Instant.now(), errorCode.status().value(), errorCode.name(),
                message, path, traceId, Map.of());
    }

    public static ApiError of(ErrorCode errorCode, String message, String path, String traceId,
                              Map<String, String> details) {
        return new ApiError(Instant.now(), errorCode.status().value(), errorCode.name(),
                message, path, traceId, details);
    }

    /**
     * For responses produced by Spring itself (405, 415, and similar) where the
     * status is known but no {@link ErrorCode} applies directly.
     */
    public static ApiError of(int status, String error, String message, String path, String traceId) {
        return new ApiError(Instant.now(), status, error, message, path, traceId, Map.of());
    }
}
