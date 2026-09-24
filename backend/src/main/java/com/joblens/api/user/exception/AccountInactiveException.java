package com.joblens.api.user.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

/**
 * The credentials were correct but the account is disabled.
 *
 * <p>Only ever thrown <em>after</em> the password has been verified, which is
 * what makes it safe to be specific: someone who already knows the password
 * learns nothing new, and someone guessing never reaches it.
 */
public class AccountInactiveException extends ApplicationException {

    public AccountInactiveException() {
        super(ErrorCode.ACCOUNT_INACTIVE, ErrorCode.ACCOUNT_INACTIVE.defaultMessage());
    }
}
