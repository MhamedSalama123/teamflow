package com.teamflow.backend.auth;

import com.teamflow.backend.auth.dto.AuthResponse;
import com.teamflow.backend.auth.dto.LoginRequest;
import com.teamflow.backend.auth.dto.RegisterRequest;
import com.teamflow.backend.security.GoogleOAuthClient;
import com.teamflow.backend.security.GoogleUserInfo;
import com.teamflow.backend.security.JwtService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }
        User user = User.builder()
                .email(request.email())
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .build();
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /**
     * Completes a Google sign-in: exchanges the authorization code for the user's profile and
     * links it to an existing account by email, creating one on first sign-in. Either way the
     * caller receives the same JWT pair as a username/password login.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String code) {
        GoogleUserInfo userInfo = googleOAuthClient.exchangeCodeForUser(code);
        User user = userRepository.findByEmail(userInfo.email())
                .orElseGet(() -> registerGoogleUser(userInfo));
        return issueTokens(user);
    }

    private User registerGoogleUser(GoogleUserInfo userInfo) {
        User user = User.builder()
                .email(userInfo.email())
                .username(resolveUsername(userInfo))
                // Google users authenticate via the provider, so set an unusable random
                // password that satisfies the non-null column but matches nothing on login.
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .build();
        return userRepository.save(user);
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
