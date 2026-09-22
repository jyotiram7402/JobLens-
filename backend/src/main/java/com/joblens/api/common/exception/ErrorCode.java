package com.joblens.api.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The stable, machine-readable error codes the API can return.
 *
 * <p>Clients are expected to branch on these values, so a code is part of the
 * public API contract: rename one and you break callers. The human-readable
 * message attached to a response may change freely; the code may not.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "The resource already exists"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
