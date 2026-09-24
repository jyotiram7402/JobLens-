package com.joblens.api.user.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

/**
 * Registration was attempted with an address that already has an account.
 *
 * <p>This does leak that the address is registered, which is account
 * enumeration. It is an accepted trade-off: the alternative -- pretending to
 * succeed and sending an email instead -- needs email delivery we do not have
 * yet, and silently failing would leave a user unable to explain why their
 * account does not work. The message deliberately does not echo the address
 * back.
 *
 * <p>Login makes no such concession: it never reveals whether the email or the
 * password was wrong.
 */
public class EmailAlreadyRegisteredException extends ApplicationException {

    public EmailAlreadyRegisteredException() {
        super(ErrorCode.EMAIL_ALREADY_REGISTERED,
                ErrorCode.EMAIL_ALREADY_REGISTERED.defaultMessage());
    }
}
