package com.teamflow.backend.workspace;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException() {
        super("No pending invitation found for this workspace.");
    }
}
