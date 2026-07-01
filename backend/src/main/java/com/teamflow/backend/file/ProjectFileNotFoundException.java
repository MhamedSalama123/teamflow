package com.teamflow.backend.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProjectFileNotFoundException extends RuntimeException {

    public ProjectFileNotFoundException() {
        super("File not found.");
    }
}
