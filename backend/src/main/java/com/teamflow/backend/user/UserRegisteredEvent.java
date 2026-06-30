package com.teamflow.backend.user;

/**
 * Published when a new {@link User} account is created (via email/password or first Google sign-in),
 * after the row is persisted. Listeners run within the registering transaction.
 */
public record UserRegisteredEvent(User user) {
}
