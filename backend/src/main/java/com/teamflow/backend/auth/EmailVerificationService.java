package com.teamflow.backend.auth;

import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the email-verification lifecycle: issuing 6-digit codes, validating them, and enforcing
 * a temporary lockout after repeated failures. Codes live on the {@link User} row so the state
 * survives restarts and is naturally scoped to the account.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final int MAX_ATTEMPTS = 3;
    static final Duration CODE_TTL = Duration.ofMinutes(15);
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final int CODE_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    /** Issues a fresh code (resetting attempts and any lockout) and emails it to the user. */
    @Transactional
    public void startVerification(User user) {
        String code = generateCode();
        user.setVerificationCode(code);
        user.setVerificationCodeExpiresAt(Instant.now().plus(CODE_TTL));
        user.setVerificationAttempts(0);
        user.setVerificationLockedUntil(null);
        userRepository.save(user);
        emailService.sendVerificationCode(user.getEmail(), code);
    }

    /**
     * Validates {@code code} for the given email. Wrong codes count toward the lockout; the
     * account is blocked for {@link #LOCKOUT_DURATION} once {@link #MAX_ATTEMPTS} is reached.
     *
     * @return the now-verified user
     */
    @Transactional
    public User verify(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidVerificationCodeException::new);
        if (user.isEmailVerified()) {
            return user;
        }

        Instant now = Instant.now();
        if (isLocked(user, now)) {
            throw new VerificationLockedException();
        }

        if (!matchesActiveCode(user, code, now)) {
            registerFailedAttempt(user, now);
            throw new InvalidVerificationCodeException();
        }

        user.setEmailVerified(true);
        clearVerificationState(user);
        return userRepository.save(user);
    }

    /** Re-issues a code, unless the account is currently locked out. No-op for unknown/verified emails. */
    @Transactional
    public void resend(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isEmailVerified()) {
            return;
        }
        if (isLocked(user, Instant.now())) {
            throw new VerificationLockedException();
        }
        startVerification(user);
    }

    private boolean isLocked(User user, Instant now) {
        return user.getVerificationLockedUntil() != null
                && user.getVerificationLockedUntil().isAfter(now);
    }

    private boolean matchesActiveCode(User user, String code, Instant now) {
        return user.getVerificationCode() != null
                && user.getVerificationCodeExpiresAt() != null
                && user.getVerificationCodeExpiresAt().isAfter(now)
                && user.getVerificationCode().equals(code);
    }

    private void registerFailedAttempt(User user, Instant now) {
        int attempts = user.getVerificationAttempts() + 1;
        user.setVerificationAttempts(attempts);
        if (attempts >= MAX_ATTEMPTS) {
            user.setVerificationLockedUntil(now.plus(LOCKOUT_DURATION));
        }
        userRepository.save(user);
    }

    private void clearVerificationState(User user) {
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        user.setVerificationAttempts(0);
        user.setVerificationLockedUntil(null);
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(CODE_BOUND));
    }
}
