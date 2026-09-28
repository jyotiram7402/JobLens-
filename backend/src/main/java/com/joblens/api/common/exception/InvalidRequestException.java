package com.joblens.api.common.exception;

/**
 * A request that is individually well-formed but wrong taken as a whole.
 *
 * <p>Bean Validation checks one field at a time, so rules that span fields --
 * {@code experienceMin} above {@code experienceMax}, {@code postedAfter} later
 * than {@code postedBefore} -- have nowhere to live. Rather than a custom
 * class-level constraint per rule, those are checked where the values are used
 * and reported through this, so the client still sees a 400 with the familiar
 * {@code VALIDATION_ERROR} code instead of a 500.
 */
public class InvalidRequestException extends ApplicationException {

    public InvalidRequestException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
