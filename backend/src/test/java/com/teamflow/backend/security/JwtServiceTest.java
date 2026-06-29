package com.teamflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!!";

    private final JwtService jwtService = new JwtService(SECRET, 900_000L, 604_800_000L);

    @Test
    void accessTokenRoundTripsSubject() {
        String token = jwtService.generateAccessToken("user@example.com");

        assertThat(jwtService.isValidAccessToken(token)).isTrue();
        assertThat(jwtService.extractSubject(token)).isEqualTo("user@example.com");
    }

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken("user@example.com");

        assertThat(jwtService.isValidRefreshToken(accessToken)).isFalse();
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken("user@example.com");

        assertThat(jwtService.isValidAccessToken(refreshToken)).isFalse();
        assertThat(jwtService.isValidRefreshToken(refreshToken)).isTrue();
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtService shortLived = new JwtService(SECRET, -1_000L, -1_000L);
        String token = shortLived.generateAccessToken("user@example.com");

        assertThat(shortLived.isValidAccessToken(token)).isFalse();
    }

    @Test
    void tamperedTokenIsRejectedByDifferentKey() {
        String token = jwtService.generateAccessToken("user@example.com");
        JwtService otherKey =
                new JwtService("another-secret-key-that-is-also-32-bytes-min!!", 900_000L, 604_800_000L);

        assertThat(otherKey.isValidAccessToken(token)).isFalse();
    }
}
