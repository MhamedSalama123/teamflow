package com.teamflow.backend.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, Long> {

    boolean existsByWorkspaceIdAndEmailIgnoreCase(Long workspaceId, String email);

    List<WorkspaceInvitation> findByWorkspaceIdOrderByCreatedAtAsc(Long workspaceId);

    List<WorkspaceInvitation> findByEmailIgnoreCase(String email);

    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
