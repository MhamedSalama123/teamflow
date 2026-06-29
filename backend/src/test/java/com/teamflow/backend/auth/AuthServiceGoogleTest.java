package com.teamflow.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamflow.backend.auth.dto.AuthResponse;
import com.teamflow.backend.security.GoogleOAuthClient;
import com.teamflow.backend.security.GoogleUserInfo;
import com.teamflow.backend.security.JwtService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    @Mock
    private EmailVerificationService emailVerificationService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService("test-secret-key-that-is-at-least-32-bytes-long!!", 900_000L, 604_800_000L);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, jwtService, googleOAuthClient, emailVerificationService);
    }

    @Test
    void linksToExistingUserByEmailWithoutCreatingAnother() {
        when(googleOAuthClient.exchangeCodeForUser("code"))
                .thenReturn(new GoogleUserInfo("alice@example.com", "Alice"));
        User existing = User.builder()
                .id(1L).email("alice@example.com").username("alice").password("hash")
                .emailVerified(false).build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.loginWithGoogle("code");

        assertThat(jwtService.isValidAccessToken(response.accessToken())).isTrue();
        assertThat(jwtService.extractSubject(response.accessToken())).isEqualTo("alice@example.com");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        // The existing account is reused (and marked verified by Google), never duplicated.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.isEmailVerified()).isTrue();
    }

    @Test
    void createsNewUserFromGoogleProfileOnFirstSignIn() {
        when(googleOAuthClient.exchangeCodeForUser("code"))
                .thenReturn(new GoogleUserInfo("bob@example.com", "Bob Builder"));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.loginWithGoogle("code");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("bob@example.com");
        assertThat(saved.getUsername()).isEqualTo("Bob Builder");
        assertThat(saved.getPassword()).isNotBlank().isNotEqualTo("bob@example.com");
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(jwtService.isValidAccessToken(response.accessToken())).isTrue();
    }

    @Test
    void fallsBackToEmailLocalPartWhenGoogleNameIsMissing() {
        when(googleOAuthClient.exchangeCodeForUser("code"))
                .thenReturn(new GoogleUserInfo("carol@example.com", null));
        when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.loginWithGoogle("code");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("carol");
    }
}
