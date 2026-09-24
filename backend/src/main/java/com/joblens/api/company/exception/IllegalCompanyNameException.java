package com.joblens.api.company.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

/**
 * The submitted name passed {@code @NotBlank} but normalizes to nothing --
 * {@code "!!!"}, {@code "---"}, a string of emoji.
 *
 * <p>Such a name cannot be stored: there is no normalized form to deduplicate
 * on and no slug to address it by. Bean Validation cannot express this, because
 * the rule depends on what the normalizer does, so the service enforces it and
 * reports it as a validation error rather than a 500.
 */
public class IllegalCompanyNameException extends ApplicationException {

    public IllegalCompanyNameException(String name) {
        super(ErrorCode.VALIDATION_ERROR,
                "Company name '%s' contains no letters or digits".formatted(name));
    }
}
