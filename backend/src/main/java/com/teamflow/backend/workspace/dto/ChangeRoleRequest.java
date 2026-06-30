package com.teamflow.backend.workspace.dto;

import com.teamflow.backend.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull WorkspaceRole role) {
}
