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
import com.teamflow.backend.workspace.dto.PendingInvitationResponse;
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
    private final WorkspaceInvitationRepository invitationRepository;
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
        List<PendingInvitationResponse> pending =
                invitationRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                        .map(PendingInvitationResponse::from)
                        .toList();
        return WorkspaceDetailResponse.of(
                membership.getWorkspace(), membership.getRole(), members, pending);
    }

    /**
     * Invites someone by email. If the email belongs to a registered user they become an invited
     * member with an in-app notification; otherwise a pending invitation is stored and claimed when
     * that email later registers. Either way an invitation email is sent.
     */
    @Transactional
    public WorkspaceMemberResponse invite(
            String actorEmail, Long workspaceId, InviteMemberRequest request) {
        User actor = requireActiveUser(actorEmail);
        Workspace workspace = requireWorkspace(workspaceId);
        requireManager(workspaceId, actor.getId());

        String email = request.email().trim();
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .map(invitee -> inviteRegistered(workspace, actor, invitee))
                .orElseGet(() -> inviteUnregistered(workspace, actor, email));
    }

    private WorkspaceMemberResponse inviteRegistered(Workspace workspace, User actor, User invitee) {
        if (memberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), invitee.getId())) {
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
                invitationMessage(actor, workspace),
                workspace.getId());
        return WorkspaceMemberResponse.from(invitation);
    }

    private WorkspaceMemberResponse inviteUnregistered(Workspace workspace, User actor, String email) {
        if (invitationRepository.existsByWorkspaceIdAndEmailIgnoreCase(workspace.getId(), email)) {
            throw new DuplicateWorkspaceMemberException();
        }
        WorkspaceInvitation invitation = invitationRepository.save(WorkspaceInvitation.builder()
                .workspace(workspace)
                .email(email)
                .role(WorkspaceRole.MEMBER)
                .invitedBy(actor)
                .build());
        emailService.sendWorkspaceInvitation(email, displayName(actor), workspace.getName());
        return WorkspaceMemberResponse.pending(invitation);
    }

    /** Cancels a pending email invitation. Only owners/admins may do this. */
    @Transactional
    public void cancelInvitation(String actorEmail, Long workspaceId, Long invitationId) {
        User actor = requireActiveUser(actorEmail);
        requireWorkspace(workspaceId);
        requireManager(workspaceId, actor.getId());
        WorkspaceInvitation invitation = invitationRepository
                .findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(InvitationNotFoundException::new);
        invitationRepository.delete(invitation);
    }

    /**
     * Converts any pending email invitations for a freshly registered user into invited memberships
     * (with notifications). Invoked from the registration flow via {@code UserRegisteredEvent}.
     */
    @Transactional
    public void claimPendingInvitations(User user) {
        for (WorkspaceInvitation invitation : invitationRepository.findByEmailIgnoreCase(user.getEmail())) {
            Workspace workspace = invitation.getWorkspace();
            if (!memberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), user.getId())) {
                memberRepository.save(WorkspaceMember.builder()
                        .workspace(workspace)
                        .user(user)
                        .role(invitation.getRole())
                        .status(WorkspaceMemberStatus.INVITED)
                        .build());
                notificationService.notify(
                        user,
                        NotificationType.WORKSPACE_INVITATION,
                        invitationMessage(invitation.getInvitedBy(), workspace),
                        workspace.getId());
            }
            invitationRepository.delete(invitation);
        }
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

    private static String invitationMessage(User inviter, Workspace workspace) {
        if (inviter == null) {
            return "You have been invited to join the workspace '%s'.".formatted(workspace.getName());
        }
        return "%s invited you to join the workspace '%s'."
                .formatted(displayName(inviter), workspace.getName());
    }

    private static String displayName(User user) {
        return (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : user.getUsername();
    }
}
