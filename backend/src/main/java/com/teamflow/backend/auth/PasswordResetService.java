package com.teamflow.backend.auth;

import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use password-reset tokens. A token is an opaque random string
 * stored on the {@link User} row with a 30-minute expiry; resetting (or requesting a new
 * token) clears the previous one, so each token works exactly once.
 */
@Service
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Emails a reset link to the account with this email. Silently does nothing when no account
     * matches, so the endpoint cannot be used to discover which emails are registered.
     */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = generateToken();
            user.setResetToken(token);
            user.setResetTokenExpiresAt(Instant.now().plus(TOKEN_TTL));
            userRepository.save(user);
            emailService.sendPasswordResetLink(user.getEmail(), buildResetLink(token));
        });
    }

    /**
     * Applies a new password if the token is valid and unexpired, then invalidates the token.
     * Completing a reset also verifies the email, since receiving the link proves ownership.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .filter(this::isTokenActive)
                .orElseThrow(InvalidResetTokenException::new);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    private boolean isTokenActive(User user) {
        return user.getResetTokenExpiresAt() != null
                && user.getResetTokenExpiresAt().isAfter(Instant.now());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildResetLink(String token) {
        return "%s/reset-password?token=%s".formatted(frontendBaseUrl, token);
    }
}
