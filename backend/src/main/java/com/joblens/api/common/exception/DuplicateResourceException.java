package com.joblens.api.common.exception;

/**
 * Thrown when creating a resource would violate a uniqueness rule, for example
 * registering an email address that already exists.
 * Rendered as HTTP 409 with the {@code DUPLICATE_RESOURCE} code.
 */
public class DuplicateResourceException extends ApplicationException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
