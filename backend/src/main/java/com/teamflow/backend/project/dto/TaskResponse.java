package com.teamflow.backend.project.dto;

import com.teamflow.backend.project.Task;
import com.teamflow.backend.project.TaskPriority;
import com.teamflow.backend.project.TaskStatus;
import com.teamflow.backend.user.User;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        AssigneeSummary assignee,
        int position) {

    /** Minimal public view of the assignee, or null when the task is unassigned. */
    public record AssigneeSummary(Long id, String username, String fullName, String photoUrl) {

        static AssigneeSummary from(User user) {
            return new AssigneeSummary(
                    user.getId(), user.getUsername(), user.getFullName(), user.getPhotoUrl());
        }
    }

    public static TaskResponse from(Task task) {
        User assignee = task.getAssignee();
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                assignee == null ? null : AssigneeSummary.from(assignee),
                task.getPosition());
    }
}
