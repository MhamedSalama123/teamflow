package com.teamflow.backend.workspace.dto;

import com.teamflow.backend.workspace.Workspace;
import com.teamflow.backend.workspace.WorkspaceRole;
import java.util.List;

/** A workspace together with its full member list and the current user's role. */
public record WorkspaceDetailResponse(
        Long id,
        String name,
        WorkspaceRole role,
        List<WorkspaceMemberResponse> members) {

    public static WorkspaceDetailResponse of(
            Workspace workspace, WorkspaceRole role, List<WorkspaceMemberResponse> members) {
        return new WorkspaceDetailResponse(workspace.getId(), workspace.getName(), role, members);
    }
}
