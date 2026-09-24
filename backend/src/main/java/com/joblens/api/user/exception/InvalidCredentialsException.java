package com.joblens.api.user.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

/**
 * The email or the password was wrong.
 *
 * <p>Which one is never disclosed, and there is no constructor that would let a
 * caller add a more specific message. "No account with that address" and "wrong
 * password" are the same response, because distinguishing them turns the login
 * endpoint into a tool for discovering which addresses have accounts.
 */
public class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.defaultMessage());
    }
}
