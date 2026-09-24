package com.joblens.api.security;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

/**
 * The token was genuine but has lapsed. Distinguished from
 * {@link InvalidTokenException} because a client should react differently: an
 * expired token means log in again, a bad one means something is wrong with the
 * client.
 */
public class TokenExpiredException extends ApplicationException {

    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED, ErrorCode.TOKEN_EXPIRED.defaultMessage());
    }
}
