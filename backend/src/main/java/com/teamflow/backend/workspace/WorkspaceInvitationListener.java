package com.teamflow.backend.workspace;

import com.teamflow.backend.user.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bridges account registration to the workspace module: when a new user signs up, any pending email
 * invitations addressed to them are turned into invited memberships. Runs synchronously within the
 * registering transaction so the user and the new memberships commit together.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceInvitationListener {

    private final WorkspaceService workspaceService;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        workspaceService.claimPendingInvitations(event.user());
    }
}
