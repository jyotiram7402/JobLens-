package com.joblens.api.job.domain;

/**
 * Where the work happens.
 *
 * <p>Mirrors {@link com.joblens.api.user.domain.RemotePreference} minus its
 * {@code ANY} value: a user may have no preference, but an opening is always
 * one of these. Step 7 compares the two.
 */
public enum WorkMode {

    ONSITE,
    HYBRID,
    REMOTE
}
