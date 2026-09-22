package com.joblens.api.common.exception;

/**
 * Thrown when a resource addressed by the request does not exist.
 * Rendered as HTTP 404 with the {@code RESOURCE_NOT_FOUND} code.
 */
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    /**
     * Convenience for the common case, producing messages such as
     * {@code "Company 42 was not found"}.
     */
    public static ResourceNotFoundException of(String resourceName, Object identifier) {
        return new ResourceNotFoundException("%s %s was not found".formatted(resourceName, identifier));
    }
}
