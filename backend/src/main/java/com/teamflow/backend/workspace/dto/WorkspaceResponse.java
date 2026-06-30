package com.teamflow.backend.workspace.dto;

import com.teamflow.backend.workspace.Workspace;
import com.teamflow.backend.workspace.WorkspaceRole;
import java.time.Instant;

/** Summary of a workspace from the perspective of the current user (their {@code role} in it). */
public record WorkspaceResponse(
        Long id,
        String name,
        WorkspaceRole role,
        long memberCount,
        Instant createdAt) {

    public static WorkspaceResponse of(Workspace workspace, WorkspaceRole role, long memberCount) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                role,
                memberCount,
                workspace.getCreatedAt());
    }
}
