package com.joblens.api.user.domain;

/**
 * How a user wants to work. Used by job matching in a later step.
 *
 * <p>{@code ANY} is the default rather than null, so matching never has to ask
 * whether "no preference" means the user does not care or has not answered.
 */
public enum RemotePreference {

    REMOTE,
    HYBRID,
    ONSITE,
    ANY
}
