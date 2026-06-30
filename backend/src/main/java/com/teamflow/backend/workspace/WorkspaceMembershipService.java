package com.teamflow.backend.workspace;

import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserNotFoundException;
import com.teamflow.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reusable workspace-membership authorization shared by the workspace, project and task services.
 * Resolves the authenticated email to a user and asserts the membership/role a request requires.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceMembershipService {

    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    public User requireActiveUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(UserNotFoundException::new);
    }

    /** Asserts the user is an active member of the workspace, returning their membership. */
    public WorkspaceMember requireActiveMembership(Long workspaceId, Long userId) {
        return memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(m -> m.getStatus() == WorkspaceMemberStatus.ACTIVE)
                .orElseThrow(() ->
                        new WorkspaceAccessDeniedException("You are not a member of this workspace."));
    }

    @Transactional(readOnly = true)
    public WorkspaceMember requireActiveMembership(Long workspaceId, String email) {
        return requireActiveMembership(workspaceId, requireActiveUser(email).getId());
    }

    /** Asserts the user is an active owner or admin of the workspace, returning their membership. */
    public WorkspaceMember requireManager(Long workspaceId, Long userId) {
        WorkspaceMember membership = requireActiveMembership(workspaceId, userId);
        if (membership.getRole() != WorkspaceRole.OWNER && membership.getRole() != WorkspaceRole.ADMIN) {
            throw new WorkspaceAccessDeniedException("This action requires an owner or admin role.");
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public WorkspaceMember requireManager(Long workspaceId, String email) {
        return requireManager(workspaceId, requireActiveUser(email).getId());
    }

    /** Whether the user is an active member of the workspace (no exception). */
    public boolean isActiveMember(Long workspaceId, Long userId) {
        return memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(m -> m.getStatus() == WorkspaceMemberStatus.ACTIVE)
                .isPresent();
    }
}
