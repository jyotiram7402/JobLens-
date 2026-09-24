package com.joblens.api.company.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

import java.util.UUID;

/**
 * No company matches the requested identifier. Rendered as HTTP 404 with the
 * {@code COMPANY_NOT_FOUND} code.
 */
public class CompanyNotFoundException extends ApplicationException {

    public CompanyNotFoundException(String message) {
        super(ErrorCode.COMPANY_NOT_FOUND, message);
    }

    public static CompanyNotFoundException withId(UUID id) {
        return new CompanyNotFoundException("No company found with id " + id);
    }

    public static CompanyNotFoundException withSlug(String slug) {
        return new CompanyNotFoundException("No company found with slug '" + slug + "'");
    }
}
