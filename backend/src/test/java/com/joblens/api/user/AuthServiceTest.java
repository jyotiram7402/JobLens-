package com.joblens.api.user;

import com.joblens.api.security.JwtService;
import com.joblens.api.user.domain.User;
import com.joblens.api.user.domain.UserProfile;
import com.joblens.api.user.dto.AuthResponse;
import com.joblens.api.user.dto.LoginRequest;
import com.joblens.api.user.dto.RegisterRequest;
import com.joblens.api.user.dto.RegistrationResponse;
import com.joblens.api.user.exception.AccountInactiveException;
import com.joblens.api.user.exception.EmailAlreadyRegisteredException;
import com.joblens.api.user.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registration and login rules, with the repositories mocked.
 *
 * <p>Uses a real {@link BCryptPasswordEncoder} rather than a mock: the point of
 * several of these tests is that hashing genuinely happens, and a mocked encoder
 * would prove nothing about that.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, userProfileRepository, passwordEncoder,
                jwtService);
    }

    private static RegisterRequest registerRequest(String email) {
        return new RegisterRequest(email, RAW_PASSWORD, "John", "Doe");
    }

    private User existingUser() {
        return User.register("user@example.com", passwordEncoder.encode(RAW_PASSWORD),
                "John", "Doe");
    }

    // --- registration -----------------------------------------------------

    @Test
    void registersUserWithHashedPasswordAndNeverStoresTheRawOne() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = authService.register(registerRequest("user@example.com"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());

        String storedHash = saved.getValue().getPasswordHash();
        assertThat(storedHash).isNotEqualTo(RAW_PASSWORD);
        assertThat(storedHash).doesNotContain(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedHash)).isTrue();
        assertThat(response.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void lowercasesEmailSoLoginIsCaseInsensitive() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(registerRequest("  User@Example.COM  "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void createsAnEmptyProfileAlongsideTheAccount() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(registerRequest("user@example.com"));

        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void registrationReturnsNoToken() {
        // Registration deliberately does not log the user in.
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(registerRequest("user@example.com"));

        verify(jwtService, never()).issueToken(any());
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("User@Example.com")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPasswordLongerThanBcryptCanUse() {
        // BCrypt ignores everything past 72 bytes; accepting a longer password
        // would authenticate on a prefix.
        RegisterRequest request = new RegisterRequest("user@example.com",
                "e".repeat(40) + "é".repeat(20), "John", "Doe");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthService.PasswordTooLongException.class);
    }

    // --- login ------------------------------------------------------------

    @Test
    void issuesTokenForValidCredentials() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser()));
        when(jwtService.issueToken(any())).thenReturn("a.jwt.token");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(
                new LoginRequest("User@Example.com", RAW_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("a.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void rejectsWrongPassword() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("user@example.com", "not the right password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsUnknownEmailWithTheSameErrorAsAWrongPassword() {
        // Identical exception for both cases, so the response cannot be used to
        // discover which addresses have accounts.
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("nobody@example.com", RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsInactiveAccountOnlyAfterVerifyingThePassword() {
        User inactive = existingUser();
        inactive.deactivate();
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(inactive));

        // Correct password -> the caller already owns the account, so it is safe
        // to be specific about why login failed.
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("user@example.com", RAW_PASSWORD)))
                .isInstanceOf(AccountInactiveException.class);

        // Wrong password -> indistinguishable from any other failed login.
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void noTokenIsIssuedWhenLoginFails() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("nobody@example.com", RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).issueToken(any());
    }
}
