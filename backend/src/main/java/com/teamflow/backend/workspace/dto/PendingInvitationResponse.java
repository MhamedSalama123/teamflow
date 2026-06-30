package com.teamflow.backend.workspace.dto;

import com.teamflow.backend.workspace.WorkspaceInvitation;
import com.teamflow.backend.workspace.WorkspaceRole;

/** A pending invitation to an email address that has not registered yet. */
public record PendingInvitationResponse(
        Long id,
        String email,
        WorkspaceRole role) {

    public static PendingInvitationResponse from(WorkspaceInvitation invitation) {
        return new PendingInvitationResponse(
                invitation.getId(), invitation.getEmail(), invitation.getRole());
    }
}
