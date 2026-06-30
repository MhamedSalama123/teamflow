package com.teamflow.backend.project;

/**
 * Broadcast to {@code /topic/projects/{projectId}} subscribers when a task changes. Clients use it
 * as a signal to refresh the board, so it carries only the kind of change and the affected task id.
 */
public record TaskEvent(TaskEventType type, Long taskId) {

    public enum TaskEventType {
        CREATED,
        UPDATED,
        DELETED
    }
}
