package com.teamflow.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class VerificationLockedException extends RuntimeException {

    public VerificationLockedException() {
        super("Too many incorrect attempts. Please request a new code in a few minutes.");
    }
}
