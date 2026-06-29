package com.teamflow.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String BASE_URL = "http://localhost:4200";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PasswordResetService service() {
        return new PasswordResetService(userRepository, passwordEncoder, emailService, BASE_URL);
    }

    private User user(String email) {
        return User.builder().id(1L).email(email).username("user")
                .password(passwordEncoder.encode("oldpassword")).emailVerified(true).build();
    }

    @Test
    void requestResetStoresTokenAndEmailsLink() {
        User user = user("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service().requestReset("user@example.com");

        assertThat(user.getResetToken()).isNotBlank();
        assertThat(user.getResetTokenExpiresAt())
                .isBetween(Instant.now().plus(29, ChronoUnit.MINUTES), Instant.now().plus(31, ChronoUnit.MINUTES));
        verify(userRepository).save(user);

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetLink(eq("user@example.com"), linkCaptor.capture());
        assertThat(linkCaptor.getValue())
                .startsWith(BASE_URL + "/reset-password?token=")
                .endsWith(user.getResetToken());
    }

    @Test
    void requestResetIsANoOpForUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service().requestReset("nobody@example.com");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetLink(any(), any());
    }

    @Test
    void resetPasswordSetsNewHashAndInvalidatesToken() {
        User user = user("user@example.com");
        user.setResetToken("valid-token");
        user.setResetTokenExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        user.setEmailVerified(false);
        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));

        service().resetPassword("valid-token", "newpassword456");

        assertThat(passwordEncoder.matches("newpassword456", user.getPassword())).isTrue();
        assertThat(user.getResetToken()).isNull();
        assertThat(user.getResetTokenExpiresAt()).isNull();
        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        when(userRepository.findByResetToken("bogus")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidResetTokenException.class)
                .isThrownBy(() -> service().resetPassword("bogus", "newpassword456"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        User user = user("user@example.com");
        user.setResetToken("expired-token");
        user.setResetTokenExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(userRepository.findByResetToken("expired-token")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(InvalidResetTokenException.class)
                .isThrownBy(() -> service().resetPassword("expired-token", "newpassword456"));

        assertThat(passwordEncoder.matches("newpassword456", user.getPassword())).isFalse();
        verify(userRepository, never()).save(any());
    }
}
