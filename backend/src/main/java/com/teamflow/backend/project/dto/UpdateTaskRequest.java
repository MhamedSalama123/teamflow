package com.teamflow.backend.project.dto;

import com.teamflow.backend.project.TaskPriority;
import com.teamflow.backend.project.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Full update of a task. {@code position} is the optional 0-based index within the target
 * {@code status} column; when omitted on a status change the task is appended to the end.
 */
public record UpdateTaskRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @NotNull TaskStatus status,
        @NotNull TaskPriority priority,
        LocalDate dueDate,
        Long assigneeId,
        Integer position) {
}
