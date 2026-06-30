package com.teamflow.backend.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByWorkspaceIdOrderByCreatedAtAsc(Long workspaceId);

    Optional<Project> findByIdAndWorkspaceId(Long id, Long workspaceId);

    /** The owning workspace id, fetched directly to avoid initializing the lazy association. */
    @Query("SELECT p.workspace.id FROM Project p WHERE p.id = :projectId")
    Optional<Long> findWorkspaceIdById(Long projectId);
}
