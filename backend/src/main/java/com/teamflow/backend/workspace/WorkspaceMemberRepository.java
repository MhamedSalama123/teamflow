package com.teamflow.backend.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    /** Members (and pending invitations) of a workspace, eagerly fetching the user for the listing. */
    List<WorkspaceMember> findByWorkspaceIdOrderByCreatedAtAsc(Long workspaceId);

    List<WorkspaceMember> findByUserIdAndStatusOrderByCreatedAtAsc(
            Long userId, WorkspaceMemberStatus status);

    long countByWorkspaceIdAndStatus(Long workspaceId, WorkspaceMemberStatus status);

    long countByWorkspaceIdAndRoleAndStatus(
            Long workspaceId, WorkspaceRole role, WorkspaceMemberStatus status);
}
