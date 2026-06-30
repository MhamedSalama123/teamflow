package com.teamflow.backend.workspace;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when the current user lacks the role required for a workspace operation. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class WorkspaceAccessDeniedException extends RuntimeException {

    public WorkspaceAccessDeniedException(String message) {
        super(message);
    }
}
