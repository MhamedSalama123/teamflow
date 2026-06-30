package com.teamflow.backend.project;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when a task is assigned to someone who is not an active member of the workspace. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAssigneeException extends RuntimeException {

    public InvalidAssigneeException() {
        super("The assignee must be an active member of the workspace.");
    }
}
