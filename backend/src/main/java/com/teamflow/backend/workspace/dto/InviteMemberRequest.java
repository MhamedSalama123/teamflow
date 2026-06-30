package com.teamflow.backend.workspace.dto;

import com.teamflow.backend.workspace.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** {@code role} is optional and defaults to {@link WorkspaceRole#MEMBER}; only MEMBER or ADMIN are valid. */
public record InviteMemberRequest(
        @NotBlank @Email String email,
        WorkspaceRole role) {

    public WorkspaceRole roleOrDefault() {
        return role != null ? role : WorkspaceRole.MEMBER;
    }
}
