package com.teamflow.backend.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when a chat message has neither text content nor an attachment. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmptyMessageException extends RuntimeException {

    public EmptyMessageException() {
        super("A chat message must have text content or an attachment.");
    }
}
