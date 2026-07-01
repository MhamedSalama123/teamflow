package com.teamflow.backend.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when a call to the Claude API fails, so the endpoint returns 503 instead of 500. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(Throwable cause) {
        super("The AI assistant is currently unavailable.", cause);
    }
}
