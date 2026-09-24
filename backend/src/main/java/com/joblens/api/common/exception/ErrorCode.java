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

    // Generic codes, used when nothing more specific applies.
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "The resource already exists"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),

    // Domain-specific codes. A module gets its own code when a client would
    // plausibly react differently -- "this company already exists" can be
    // handled by linking to it, where a generic conflict cannot. Codes are
    // registered here rather than in each module so that this enum stays the
    // single list of everything the API can return.
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "Company not found"),
    COMPANY_ALREADY_EXISTS(HttpStatus.CONFLICT, "Company already exists"),

    // Authentication and accounts.
    //
    // INVALID_CREDENTIALS covers a wrong email and a wrong password alike, on
    // purpose: distinguishing them tells an attacker which addresses have
    // accounts. ACCOUNT_INACTIVE is only ever returned after the password has
    // already been verified, so it reveals nothing to someone who does not
    // already know it.
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email address is already registered"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "This account is not active"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The access token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "The access token is not valid"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have access to this resource"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found");

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
