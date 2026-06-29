package com.teamflow.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class GoogleAuthenticationException extends RuntimeException {

    public GoogleAuthenticationException(String message) {
        super(message);
    }

    public GoogleAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
