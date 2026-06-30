package com.teamflow.backend.project.dto;

import com.teamflow.backend.project.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** {@code priority} defaults to MEDIUM; {@code assigneeId} and {@code dueDate} are optional. */
public record CreateTaskRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        TaskPriority priority,
        LocalDate dueDate,
        Long assigneeId) {

    public TaskPriority priorityOrDefault() {
        return priority != null ? priority : TaskPriority.MEDIUM;
    }
}
