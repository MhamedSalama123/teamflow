package com.teamflow.backend.workspace.dto;

import com.teamflow.backend.user.User;
import com.teamflow.backend.workspace.WorkspaceInvitation;
import com.teamflow.backend.workspace.WorkspaceMember;
import com.teamflow.backend.workspace.WorkspaceMemberStatus;
import com.teamflow.backend.workspace.WorkspaceRole;

/**
 * A single workspace membership, including the member's public profile fields. For an invitation to
 * an unregistered email the user-backed fields ({@code userId}, {@code username}, ...) are null.
 */
public record WorkspaceMemberResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        String photoUrl,
        WorkspaceRole role,
        WorkspaceMemberStatus status) {

    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        User user = member.getUser();
        return new WorkspaceMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhotoUrl(),
                member.getRole(),
                member.getStatus());
    }

    /** Builds a response for a pending invitation to an email that has no account yet. */
    public static WorkspaceMemberResponse pending(WorkspaceInvitation invitation) {
        return new WorkspaceMemberResponse(
                null,
                null,
                null,
                invitation.getEmail(),
                null,
                invitation.getRole(),
                WorkspaceMemberStatus.INVITED);
    }
}
