package com.joblens.api.common.web;

import java.time.Instant;
import java.util.Map;

/**
 * Single error shape returned by every failing API call.
 *
 * @param timestamp when the error was produced
 * @param status    HTTP status code
 * @param error     short, stable machine-readable code
 * @param message   human-readable explanation
 * @param path      request path that failed
 * @param details   optional field-level messages (validation failures)
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> details
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, Map.of());
    }
}
