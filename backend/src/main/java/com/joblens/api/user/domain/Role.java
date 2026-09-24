package com.joblens.api.user.domain;

/**
 * What a user is allowed to do.
 *
 * <p>Two values is the whole model for V1, and that is deliberate: a role
 * column beats a permissions framework until there is a second kind of user to
 * justify one. Stored as a string rather than an ordinal so that adding or
 * reordering values cannot silently reassign anyone's access.
 *
 * <p>{@code ADMIN} is declared but never granted by any code path. It exists so
 * the column, the CHECK constraint and the authority mapping already handle a
 * second role when one is needed.
 */
public enum Role {

    USER,
    ADMIN;

    /**
     * Spring Security's {@code hasRole("USER")} looks for the authority
     * {@code ROLE_USER}. Building the string here keeps that prefix convention
     * in one place.
     */
    public String authority() {
        return "ROLE_" + name();
    }
}
