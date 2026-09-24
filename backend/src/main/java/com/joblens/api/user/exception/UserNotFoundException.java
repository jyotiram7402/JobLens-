package com.joblens.api.user.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

import java.util.UUID;

/**
 * The authenticated principal refers to a user that no longer exists.
 *
 * <p>Reachable because tokens are verified without a database read: a token
 * issued before an account was removed stays cryptographically valid until it
 * expires. Rare, but a 404 is the honest answer rather than a 500.
 */
public class UserNotFoundException extends ApplicationException {

    public UserNotFoundException(UUID id) {
        super(ErrorCode.USER_NOT_FOUND, "No user found with id " + id);
    }
}
