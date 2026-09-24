package com.joblens.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/register}.
 *
 * <p>The password bound here is the only place a raw password exists in the
 * application. It is hashed in the service and never stored, never returned and
 * never logged.
 *
 * <p>There is no {@code role} and no {@code active} field: a client must not be
 * able to register itself as an administrator, and the safest way to guarantee
 * that is to give it nowhere to try.
 */
public record RegisterRequest(

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        /*
         * Length is the rule that actually matters, per current NIST guidance:
         * a long passphrase beats a short string with a symbol bolted on, and
         * composition rules mostly teach people to write "Password1!".
         *
         * The 72-character ceiling is not arbitrary. BCrypt silently ignores
         * everything past 72 *bytes*, so a longer password would be accepted
         * while part of it did nothing -- a user could type 100 characters and
         * authenticate with the first 72. Rejecting it is honest; the service
         * also checks the UTF-8 byte length, since non-ASCII characters cost
         * more than one byte each.
         */
        @NotBlank(message = "must not be blank")
        @Size(min = 10, max = 72, message = "must be between 10 and 72 characters")
        String password,

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        String firstName,

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        String lastName
) {
}
