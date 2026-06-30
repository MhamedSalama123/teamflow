package com.teamflow.backend.workspace;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WorkspaceMemberNotFoundException extends RuntimeException {

    public WorkspaceMemberNotFoundException() {
        super("Workspace member not found.");
    }
}
