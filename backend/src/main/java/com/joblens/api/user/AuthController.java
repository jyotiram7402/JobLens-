package com.joblens.api.user;

import com.joblens.api.common.web.ApiRoutes;
import com.joblens.api.user.dto.AuthResponse;
import com.joblens.api.user.dto.LoginRequest;
import com.joblens.api.user.dto.RegisterRequest;
import com.joblens.api.user.dto.RegistrationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints.
 *
 * <p>The only two routes in the application reachable without a token. Both are
 * listed explicitly in {@code SecurityConfig}; everything else defaults to
 * requiring authentication.
 *
 * <p>Thin, as ever: no credential handling, no hashing, no token construction.
 * Those live in {@code AuthService} and {@code JwtService}.
 */
@RestController
@RequestMapping(ApiRoutes.API_V1 + "/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Creates an account. Returns the new user and no token -- registration does
     * not log you in; call {@code /login} next.
     *
     * <p>201 on success, 400 on validation failure, 409 if the email is taken.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Exchanges credentials for an access token.
     *
     * <p>200 on success, 401 if the credentials are wrong (without saying which
     * part), 403 if the account is disabled.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
