package com.teamflow.backend.project.dto;

import jakarta.validation.constraints.NotNull;

/** Assigns a task to a workspace member. */
public record AssignTaskRequest(
        @NotNull Long assigneeId) {
}
