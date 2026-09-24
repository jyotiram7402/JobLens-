package com.joblens.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/login}.
 *
 * <p>Validation here is deliberately thin -- present and within a sane length,
 * nothing more. Applying the registration password rules to a login attempt
 * would reject an old password that no longer meets current policy with a
 * validation error instead of an authentication failure, and would tell an
 * attacker what the policy is.
 */
public record LoginRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String password
) {
}
