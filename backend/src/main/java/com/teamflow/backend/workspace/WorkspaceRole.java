package com.teamflow.backend.workspace;

/** A member's authority within a workspace. The creator is always the initial {@link #OWNER}. */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER
}
