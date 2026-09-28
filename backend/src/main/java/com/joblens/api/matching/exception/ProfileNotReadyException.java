package com.joblens.api.matching.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

/**
 * The user has an account but nothing in their profile to match on.
 *
 * <p>A distinct error rather than an empty result, because the two mean
 * completely different things to the person reading them: "no jobs suit you" is
 * discouraging and wrong, while "we need to know something about you first" is
 * actionable. The message names what to add.
 */
public class ProfileNotReadyException extends ApplicationException {

    public ProfileNotReadyException() {
        super(ErrorCode.PROFILE_NOT_READY,
                "Add some skills, your years of experience, or your preferred roles and "
                        + "locations to your profile so JobLens can match jobs to you.");
    }
}
