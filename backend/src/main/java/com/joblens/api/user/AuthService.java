package com.joblens.api.user;

import com.joblens.api.common.exception.ErrorCode;
import com.joblens.api.security.JwtService;
import com.joblens.api.user.domain.User;
import com.joblens.api.user.domain.UserProfile;
import com.joblens.api.user.dto.AuthResponse;
import com.joblens.api.user.dto.LoginRequest;
import com.joblens.api.user.dto.RegisterRequest;
import com.joblens.api.user.dto.RegistrationResponse;
import com.joblens.api.user.dto.UserResponse;
import com.joblens.api.user.exception.AccountInactiveException;
import com.joblens.api.user.exception.EmailAlreadyRegisteredException;
import com.joblens.api.user.exception.InvalidCredentialsException;
import com.joblens.api.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Registration and login.
 *
 * <p>Every log statement in this class identifies a user by id or by nothing at
 * all. Passwords, hashes and tokens are never logged, at any level -- a DEBUG
 * line written during a bad week is exactly how credentials end up in a log
 * aggregator.
 *
 * <h2>Registration does not log the user in</h2>
 *
 * <p>It returns the created account and no token; the client then calls
 * {@code /auth/login} with the credentials it already has. Two reasons:
 *
 * <ol>
 *   <li>One way to obtain a token means one code path to audit. Issuing tokens
 *       from two endpoints doubles the surface for getting it wrong.</li>
 *   <li>Email verification is the obvious next requirement, and it sits exactly
 *       where auto-login would be. Not issuing a token now means adding
 *       verification later does not have to take one away -- which would be a
 *       breaking change for the frontend.</li>
 * </ol>
 *
 * <p>The cost is one extra request on signup. The frontend can make it
 * invisible by calling login immediately afterwards.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * BCrypt ignores everything past 72 bytes. A longer password would be
     * accepted while part of it did nothing, so it is rejected instead. The
     * request DTO limits characters; this checks bytes, because a non-ASCII
     * character costs more than one.
     */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Creates an account and an empty career profile for it.
     *
     * <p>The profile is created here rather than lazily on first read, so every
     * user always has one and no endpoint has to handle a missing profile or
     * risk creating a second.
     *
     * @throws EmailAlreadyRegisteredException if the address already has an account
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        requireUsablePassword(request.password());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = User.register(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim());

        User saved = saveHandlingDuplicateEmail(user);
        userProfileRepository.save(UserProfile.emptyFor(saved));

        log.info("Registered user {}", saved.getId());
        return RegistrationResponse.of(UserResponse.from(saved));
    }

    /**
     * Verifies credentials and issues an access token.
     *
     * <p>Note the order: the password is checked <em>before</em> the account's
     * active flag. Reporting "this account is disabled" to someone who has not
     * proved they own it would confirm the address exists.
     *
     * @throws InvalidCredentialsException if the email or password is wrong
     * @throws AccountInactiveException    if the credentials are right but the
     *                                     account is disabled
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = User.normalizeEmail(request.email());
        Optional<User> candidate = userRepository.findByEmail(email);

        if (!passwordMatches(candidate, request.password())) {
            log.info("Failed login attempt");
            throw new InvalidCredentialsException();
        }

        User user = candidate.orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) {
            log.info("Login blocked for inactive user {}", user.getId());
            throw new AccountInactiveException();
        }

        log.info("User {} logged in", user.getId());
        return AuthResponse.of(
                jwtService.issueToken(user),
                jwtService.expiresInSeconds(),
                UserResponse.from(user));
    }

    /**
     * Checks the password, and does the same amount of work whether or not the
     * account exists.
     *
     * <p>If an unknown address returned immediately, it would answer in
     * microseconds while a known one took the ~100ms bcrypt needs. That
     * difference is measurable over a network and turns login into an account
     * enumeration oracle. Encoding against a dummy hash keeps the timings
     * comparable.
     */
    private boolean passwordMatches(Optional<User> candidate, String rawPassword) {
        if (candidate.isEmpty()) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            return false;
        }
        return passwordEncoder.matches(rawPassword, candidate.get().getPasswordHash());
    }

    /**
     * A real bcrypt hash of an unguessable value, used only to spend the same
     * time verifying a password for an account that does not exist. It is not a
     * secret and it grants nothing: no account has this hash.
     */
    private static final String DUMMY_HASH =
            "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private void requireUsablePassword(String password) {
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > MAX_PASSWORD_BYTES) {
            throw new PasswordTooLongException();
        }
    }

    /**
     * The existence check above and the insert are not atomic, so two
     * simultaneous registrations for one address can both pass it. The unique
     * index settles the race; this turns the resulting violation into the same
     * 409 rather than a 500.
     */
    private User saveHandlingDuplicateEmail(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Integrity violation registering a user", ex);
            throw new EmailAlreadyRegisteredException();
        }
    }

    /**
     * Reported as a validation error rather than a 500. Bean Validation limits
     * characters; only this check knows about bytes.
     */
    static class PasswordTooLongException extends ApplicationException {
        PasswordTooLongException() {
            super(ErrorCode.VALIDATION_ERROR,
                    "Password must be at most %d bytes when UTF-8 encoded"
                            .formatted(MAX_PASSWORD_BYTES));
        }
    }
}
