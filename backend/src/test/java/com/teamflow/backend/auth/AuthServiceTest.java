package com.teamflow.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamflow.backend.auth.dto.AuthResponse;
import com.teamflow.backend.auth.dto.LoginRequest;
import com.teamflow.backend.auth.dto.RegisterRequest;
import com.teamflow.backend.auth.dto.RegistrationResponse;
import com.teamflow.backend.auth.dto.VerifyEmailRequest;
import com.teamflow.backend.security.GoogleOAuthClient;
import com.teamflow.backend.security.JwtService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService("test-secret-key-that-is-at-least-32-bytes-long!!", 900_000L, 604_800_000L);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                googleOAuthClient,
                emailVerificationService,
                eventPublisher);
    }

    private User verifiedUser(String email, String rawPassword) {
        return User.builder()
                .id(1L).email(email).username("user")
                .password(passwordEncoder.encode(rawPassword))
                .emailVerified(true)
                .build();
    }

    @Test
    void registerCreatesUnverifiedUserAndStartsVerification() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        RegistrationResponse response = authService.register(
                new RegisterRequest("new@example.com", "newbie", "password123"));

        assertThat(response.email()).isEqualTo("new@example.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(emailVerificationService).startVerification(captor.capture());
        User created = captor.getValue();
        assertThat(created.isEmailVerified()).isFalse();
        assertThat(created.getPassword()).isNotEqualTo("password123");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatExceptionOfType(EmailAlreadyUsedException.class).isThrownBy(() ->
                authService.register(new RegisterRequest("taken@example.com", "user", "password123")));
    }

    @Test
    void loginIsBlockedForUnverifiedUser() {
        User user = User.builder()
                .id(1L).email("a@example.com").username("a")
                .password(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .build();
        when(userRepository.findByEmailAndDeletedAtIsNull("a@example.com")).thenReturn(java.util.Optional.of(user));

        assertThatExceptionOfType(EmailNotVerifiedException.class).isThrownBy(() ->
                authService.login(new LoginRequest("a@example.com", "password123")));
    }

    @Test
    void loginSucceedsForVerifiedUser() {
        when(userRepository.findByEmailAndDeletedAtIsNull("a@example.com"))
                .thenReturn(java.util.Optional.of(verifiedUser("a@example.com", "password123")));

        AuthResponse response = authService.login(new LoginRequest("a@example.com", "password123"));

        assertThat(jwtService.isValidAccessToken(response.accessToken())).isTrue();
    }

    @Test
    void loginRejectsWrongPasswordBeforeCheckingVerification() {
        when(userRepository.findByEmailAndDeletedAtIsNull("a@example.com"))
                .thenReturn(java.util.Optional.of(verifiedUser("a@example.com", "password123")));

        assertThatExceptionOfType(InvalidCredentialsException.class).isThrownBy(() ->
                authService.login(new LoginRequest("a@example.com", "wrong-password")));
    }

    @Test
    void verifyEmailIssuesTokens() {
        when(emailVerificationService.verify("a@example.com", "123456"))
                .thenReturn(verifiedUser("a@example.com", "password123"));

        AuthResponse response = authService.verifyEmail(new VerifyEmailRequest("a@example.com", "123456"));

        assertThat(jwtService.isValidAccessToken(response.accessToken())).isTrue();
        assertThat(jwtService.extractSubject(response.accessToken())).isEqualTo("a@example.com");
    }
}
