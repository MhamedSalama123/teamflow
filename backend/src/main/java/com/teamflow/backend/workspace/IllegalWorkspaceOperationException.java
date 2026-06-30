package com.teamflow.backend.workspace;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised for operations that would leave the workspace in an invalid state (e.g. no owner). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IllegalWorkspaceOperationException extends RuntimeException {

    public IllegalWorkspaceOperationException(String message) {
        super(message);
    }
}
