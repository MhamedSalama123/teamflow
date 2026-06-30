package com.teamflow.backend.workspace;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateWorkspaceMemberException extends RuntimeException {

    public DuplicateWorkspaceMemberException() {
        super("That user is already a member of or invited to this workspace.");
    }
}
