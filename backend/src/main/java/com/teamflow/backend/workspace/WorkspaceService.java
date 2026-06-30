package com.teamflow.backend.workspace;

import com.teamflow.backend.notification.NotificationService;
import com.teamflow.backend.notification.NotificationType;
import com.teamflow.backend.security.EmailService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserNotFoundException;
import com.teamflow.backend.user.UserRepository;
import com.teamflow.backend.workspace.dto.ChangeRoleRequest;
import com.teamflow.backend.workspace.dto.CreateWorkspaceRequest;
import com.teamflow.backend.workspace.dto.InviteMemberRequest;
import com.teamflow.backend.workspace.dto.WorkspaceDetailResponse;
import com.teamflow.backend.workspace.dto.WorkspaceMemberResponse;
import com.teamflow.backend.workspace.dto.WorkspaceResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    /** Creates a workspace and enrolls the caller as its {@link WorkspaceRole#OWNER}. */
    @Transactional
    public WorkspaceResponse create(String actorEmail, CreateWorkspaceRequest request) {
        User owner = requireActiveUser(actorEmail);
        Workspace workspace = workspaceRepository.save(
                Workspace.builder().name(request.name().trim()).build());
        memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .status(WorkspaceMemberStatus.ACTIVE)
                .build());
        return WorkspaceResponse.of(workspace, WorkspaceRole.OWNER, 1);
    }

    /** The workspaces the caller has actively joined (pending invitations are excluded). */
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listForUser(String actorEmail) {
        User user = requireActiveUser(actorEmail);
        return memberRepository
                .findByUserIdAndStatusOrderByCreatedAtAsc(user.getId(), WorkspaceMemberStatus.ACTIVE)
                .stream()
                .map(member -> WorkspaceResponse.of(
                        member.getWorkspace(),
                        member.getRole(),
                        activeMemberCount(member.getWorkspace().getId())))
                .toList();
    }

    /** A workspace with its full member list. The caller must be an active member to view it. */
    @Transactional(readOnly = true)
    public WorkspaceDetailResponse getDetail(String actorEmail, Long workspaceId) {
        User user = requireActiveUser(actorEmail);
        WorkspaceMember membership = requireActiveMembership(workspaceId, user.getId());
        List<WorkspaceMemberResponse> members =
                memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                        .map(WorkspaceMemberResponse::from)
                        .toList();
        return WorkspaceDetailResponse.of(membership.getWorkspace(), membership.getRole(), members);
    }

    /** Invites an existing user by email, emailing them and raising an in-app notification. */
    @Transactional
    public WorkspaceMemberResponse invite(
            String actorEmail, Long workspaceId, InviteMemberRequest request) {
        User actor = requireActiveUser(actorEmail);
        Workspace workspace = requireWorkspace(workspaceId);
        requireManager(workspaceId, actor.getId());

        User invitee = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(UserNotFoundException::new);
        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, invitee.getId())) {
            throw new DuplicateWorkspaceMemberException();
        }

        WorkspaceMember invitation = memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(invitee)
                .role(WorkspaceRole.MEMBER)
                .status(WorkspaceMemberStatus.INVITED)
                .build());

        emailService.sendWorkspaceInvitation(invitee.getEmail(), displayName(actor), workspace.getName());
        notificationService.notify(
                invitee,
                NotificationType.WORKSPACE_INVITATION,
                "%s invited you to join the workspace '%s'."
                        .formatted(displayName(actor), workspace.getName()),
                workspace.getId());

        return WorkspaceMemberResponse.from(invitation);
    }

    /** Accepts the caller's pending invitation, turning it into an active membership. */
    @Transactional
    public WorkspaceResponse acceptInvite(String actorEmail, Long workspaceId) {
        User user = requireActiveUser(actorEmail);
        WorkspaceMember invitation = requirePendingInvitation(workspaceId, user.getId());
        invitation.setStatus(WorkspaceMemberStatus.ACTIVE);
        memberRepository.save(invitation);
        return WorkspaceResponse.of(
                invitation.getWorkspace(), invitation.getRole(), activeMemberCount(workspaceId));
    }

    /** Declines (and removes) the caller's pending invitation. */
    @Transactional
    public void declineInvite(String actorEmail, Long workspaceId) {
        User user = requireActiveUser(actorEmail);
        WorkspaceMember invitation = requirePendingInvitation(workspaceId, user.getId());
        memberRepository.delete(invitation);
    }

    /** Removes a member. Only owners/admins may do this, with guards protecting ownership. */
    @Transactional
    public void removeMember(String actorEmail, Long workspaceId, Long targetUserId) {
        User actor = requireActiveUser(actorEmail);
        requireWorkspace(workspaceId);
        WorkspaceMember actorMembership = requireManager(workspaceId, actor.getId());
        WorkspaceMember target = requireMember(workspaceId, targetUserId);

        guardCanManageTarget(actorMembership, target);
        if (target.getRole() == WorkspaceRole.OWNER && isLastActiveOwner(workspaceId)) {
            throw new IllegalWorkspaceOperationException("Cannot remove the last owner of a workspace.");
        }
        memberRepository.delete(target);
    }

    /** Changes a member's role. Only owners/admins may do this; only owners may touch ownership. */
    @Transactional
    public WorkspaceMemberResponse changeRole(
            String actorEmail, Long workspaceId, Long targetUserId, ChangeRoleRequest request) {
        User actor = requireActiveUser(actorEmail);
        requireWorkspace(workspaceId);
        WorkspaceMember actorMembership = requireManager(workspaceId, actor.getId());
        WorkspaceMember target = requireMember(workspaceId, targetUserId);
        WorkspaceRole newRole = request.role();

        // Granting or revoking ownership is reserved for owners.
        if ((newRole == WorkspaceRole.OWNER || target.getRole() == WorkspaceRole.OWNER)
                && actorMembership.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceAccessDeniedException("Only an owner can change ownership.");
        }
        // Admins cannot promote/demote other admins.
        if (target.getRole() == WorkspaceRole.ADMIN
                && actorMembership.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceAccessDeniedException("Only an owner can change an admin's role.");
        }
        if (target.getRole() == WorkspaceRole.OWNER
                && newRole != WorkspaceRole.OWNER
                && isLastActiveOwner(workspaceId)) {
            throw new IllegalWorkspaceOperationException("Cannot demote the last owner of a workspace.");
        }

        target.setRole(newRole);
        return WorkspaceMemberResponse.from(memberRepository.save(target));
    }

    private void guardCanManageTarget(WorkspaceMember actor, WorkspaceMember target) {
        // Admins may only remove plain members; touching owners or other admins requires an owner.
        if (target.getRole() != WorkspaceRole.MEMBER
                && actor.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceAccessDeniedException("Only an owner can remove an owner or admin.");
        }
    }

    private long activeMemberCount(Long workspaceId) {
        return memberRepository.countByWorkspaceIdAndStatus(workspaceId, WorkspaceMemberStatus.ACTIVE);
    }

    private boolean isLastActiveOwner(Long workspaceId) {
        return memberRepository.countByWorkspaceIdAndRoleAndStatus(
                        workspaceId, WorkspaceRole.OWNER, WorkspaceMemberStatus.ACTIVE)
                <= 1;
    }

    private WorkspaceMember requireManager(Long workspaceId, Long userId) {
        WorkspaceMember membership = requireActiveMembership(workspaceId, userId);
        if (membership.getRole() != WorkspaceRole.OWNER && membership.getRole() != WorkspaceRole.ADMIN) {
            throw new WorkspaceAccessDeniedException("This action requires an owner or admin role.");
        }
        return membership;
    }

    private WorkspaceMember requireActiveMembership(Long workspaceId, Long userId) {
        return memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(m -> m.getStatus() == WorkspaceMemberStatus.ACTIVE)
                .orElseThrow(() ->
                        new WorkspaceAccessDeniedException("You are not a member of this workspace."));
    }

    private WorkspaceMember requireMember(Long workspaceId, Long userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);
    }

    private WorkspaceMember requirePendingInvitation(Long workspaceId, Long userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(m -> m.getStatus() == WorkspaceMemberStatus.INVITED)
                .orElseThrow(InvitationNotFoundException::new);
    }

    private Workspace requireWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);
    }

    private User requireActiveUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(UserNotFoundException::new);
    }

    private static String displayName(User user) {
        return (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : user.getUsername();
    }
}
