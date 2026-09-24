package com.joblens.api.company.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

/**
 * A company with the same normalized name already exists. Rendered as HTTP 409
 * with the {@code COMPANY_ALREADY_EXISTS} code.
 *
 * <p>The message names the existing company's slug so the caller can go and look
 * at it rather than guessing what it collided with.
 */
public class CompanyAlreadyExistsException extends ApplicationException {

    public CompanyAlreadyExistsException(String message) {
        super(ErrorCode.COMPANY_ALREADY_EXISTS, message);
    }

    public static CompanyAlreadyExistsException forName(String name, String existingSlug) {
        return new CompanyAlreadyExistsException(
                "A company matching '%s' already exists: '%s'".formatted(name, existingSlug));
    }

    /**
     * Used when the unique index rejected the insert and the existing row was
     * not read back, so there is no slug to point at.
     */
    public static CompanyAlreadyExistsException forName(String name) {
        return new CompanyAlreadyExistsException(
                "A company matching '%s' already exists".formatted(name));
    }
}
