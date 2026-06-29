package com.teamflow.backend.auth.dto;

/**
 * Returned by registration. Tokens are intentionally withheld until the email is verified;
 * the client uses {@code email} to drive the verification screen.
 */
public record RegistrationResponse(
        String email,
        String message) {

    public static RegistrationResponse verificationSent(String email) {
        return new RegistrationResponse(email, "A verification code has been sent to your email.");
    }
}
