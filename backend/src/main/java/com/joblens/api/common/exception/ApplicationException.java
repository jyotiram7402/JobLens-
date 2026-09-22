package com.joblens.api.common.exception;

/**
 * Base class for exceptions that carry a deliberate API response.
 *
 * <p>Anything extending this is a known, expected failure: the handler trusts
 * its {@link ErrorCode} and message and returns them to the client as-is.
 * Everything else is treated as a bug and reported as
 * {@link ErrorCode#INTERNAL_ERROR} with no internal detail leaked.
 *
 * <p>Domain modules extend this rather than handling HTTP concerns themselves,
 * which keeps services free of {@code ResponseEntity} and status codes.
 */
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApplicationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ApplicationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
