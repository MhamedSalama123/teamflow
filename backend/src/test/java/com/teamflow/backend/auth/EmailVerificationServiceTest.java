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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    private EmailVerificationService service() {
        return new EmailVerificationService(userRepository, emailService);
    }

    private User unverifiedUser() {
        return User.builder().id(1L).email("user@example.com").username("user").password("hash").build();
    }

    @Test
    void startVerificationIssuesSixDigitCodeAndSendsEmail() {
        User user = unverifiedUser();

        service().startVerification(user);

        assertThat(user.getVerificationCode()).matches("\\d{6}");
        assertThat(user.getVerificationAttempts()).isZero();
        assertThat(user.getVerificationLockedUntil()).isNull();
        assertThat(user.getVerificationCodeExpiresAt())
                .isBetween(Instant.now().plus(14, ChronoUnit.MINUTES), Instant.now().plus(16, ChronoUnit.MINUTES));
        verify(userRepository).save(user);
        verify(emailService).sendVerificationCode(eq("user@example.com"), eq(user.getVerificationCode()));
    }

    @Test
    void verifyMarksUserVerifiedAndClearsState() {
        User user = unverifiedUser();
        user.setVerificationCode("123456");
        user.setVerificationCodeExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service().verify("user@example.com", "123456");

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getVerificationCode()).isNull();
        assertThat(result.getVerificationCodeExpiresAt()).isNull();
    }

    @Test
    void verifyWithWrongCodeIncrementsAttempts() {
        User user = unverifiedUser();
        user.setVerificationCode("123456");
        user.setVerificationCodeExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service().verify("user@example.com", "000000"));

        assertThat(user.getVerificationAttempts()).isEqualTo(1);
        assertThat(user.getVerificationLockedUntil()).isNull();
    }

    @Test
    void verifyLocksAccountAfterThreeFailedAttempts() {
        User user = unverifiedUser();
        user.setVerificationCode("123456");
        user.setVerificationCodeExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        user.setVerificationAttempts(2);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service().verify("user@example.com", "000000"));

        assertThat(user.getVerificationAttempts()).isEqualTo(3);
        assertThat(user.getVerificationLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void verifyIsRejectedWhileLockedWithoutCheckingCode() {
        User user = unverifiedUser();
        user.setVerificationCode("123456");
        user.setVerificationCodeExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        user.setVerificationLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(VerificationLockedException.class)
                .isThrownBy(() -> service().verify("user@example.com", "123456"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyRejectsExpiredCode() {
        User user = unverifiedUser();
        user.setVerificationCode("123456");
        user.setVerificationCodeExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(InvalidVerificationCodeException.class)
                .isThrownBy(() -> service().verify("user@example.com", "123456"));

        assertThat(user.getVerificationAttempts()).isEqualTo(1);
    }

    @Test
    void resendIssuesNewCodeForUnverifiedUser() {
        User user = unverifiedUser();
        user.setVerificationCode("111111");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service().resend("user@example.com");

        assertThat(user.getVerificationCode()).matches("\\d{6}");
        verify(emailService).sendVerificationCode(eq("user@example.com"), any());
    }

    @Test
    void resendIsRejectedWhileLocked() {
        User user = unverifiedUser();
        user.setVerificationLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatExceptionOfType(VerificationLockedException.class)
                .isThrownBy(() -> service().resend("user@example.com"));

        verify(emailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    void resendIsANoOpForUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service().resend("nobody@example.com");

        verify(emailService, never()).sendVerificationCode(any(), any());
    }
}
