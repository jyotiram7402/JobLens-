package com.joblens.api.security;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

/**
 * The token could not be trusted: bad signature, wrong issuer, malformed, or
 * claims that do not parse.
 *
 * <p>The message is deliberately uniform. Telling a caller which check failed
 * helps them forge a better attempt and helps nobody else.
 */
public class InvalidTokenException extends ApplicationException {

    public InvalidTokenException() {
        super(ErrorCode.TOKEN_INVALID, ErrorCode.TOKEN_INVALID.defaultMessage());
    }
}
