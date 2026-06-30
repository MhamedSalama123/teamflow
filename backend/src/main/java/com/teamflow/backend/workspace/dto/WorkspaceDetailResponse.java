package com.teamflow.backend.workspace.dto;

import com.teamflow.backend.workspace.Workspace;
import com.teamflow.backend.workspace.WorkspaceRole;
import java.util.List;

/**
 * A workspace together with its member list, any pending email invitations (to addresses that have
 * not registered yet), and the current user's role.
 */
public record WorkspaceDetailResponse(
        Long id,
        String name,
        WorkspaceRole role,
        List<WorkspaceMemberResponse> members,
        List<PendingInvitationResponse> pendingInvitations) {

    public static WorkspaceDetailResponse of(
            Workspace workspace,
            WorkspaceRole role,
            List<WorkspaceMemberResponse> members,
            List<PendingInvitationResponse> pendingInvitations) {
        return new WorkspaceDetailResponse(
                workspace.getId(), workspace.getName(), role, members, pendingInvitations);
    }
}
