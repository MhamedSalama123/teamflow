package com.teamflow.backend.auth;

import com.teamflow.backend.auth.dto.AuthResponse;
import com.teamflow.backend.auth.dto.LoginRequest;
import com.teamflow.backend.auth.dto.RegisterRequest;
import com.teamflow.backend.auth.dto.RegistrationResponse;
import com.teamflow.backend.auth.dto.ResendVerificationRequest;
import com.teamflow.backend.auth.dto.VerifyEmailRequest;
import com.teamflow.backend.security.GoogleOAuthClient;
import com.teamflow.backend.security.GoogleUserInfo;
import com.teamflow.backend.security.JwtService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRegisteredEvent;
import com.teamflow.backend.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleOAuthClient googleOAuthClient;
    private final EmailVerificationService emailVerificationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates an unverified account and emails a verification code. No tokens are issued here;
     * the caller must verify the email (see {@link #verifyEmail}) before they can log in.
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }
        User user = User.builder()
                .email(request.email())
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .emailVerified(false)
                .build();
        emailVerificationService.startVerification(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(user));
        return RegistrationResponse.verificationSent(user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        return issueTokens(user);
    }

    /** Validates the emailed code and, on success, logs the user in by issuing tokens. */
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = emailVerificationService.verify(request.email(), request.code());
        return issueTokens(user);
    }

    public void resendVerification(ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
    }

    /**
     * Completes a Google sign-in: exchanges the authorization code for the user's profile and
     * links it to an existing account by email, creating one on first sign-in. Google has
     * already verified the email, so the account is marked verified either way.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String code) {
        GoogleUserInfo userInfo = googleOAuthClient.exchangeCodeForUser(code);
        User user = userRepository.findByEmail(userInfo.email())
                .orElseGet(() -> registerGoogleUser(userInfo));
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }
        return issueTokens(user);
    }

    private User registerGoogleUser(GoogleUserInfo userInfo) {
        User user = User.builder()
                .email(userInfo.email())
                .username(resolveUsername(userInfo))
                .fullName(userInfo.name())
                // Google users authenticate via the provider, so set an unusable random
                // password that satisfies the non-null column but matches nothing on login.
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .emailVerified(true)
                .build();
        User saved = userRepository.save(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(saved));
        return saved;
    }

    private String resolveUsername(GoogleUserInfo userInfo) {
        if (userInfo.name() != null && !userInfo.name().isBlank()) {
            return userInfo.name();
        }
        String email = userInfo.email();
        return email.substring(0, email.indexOf('@'));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        return AuthResponse.bearer(accessToken, refreshToken);
    }
}
