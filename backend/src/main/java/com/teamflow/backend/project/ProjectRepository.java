package com.teamflow.backend.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByWorkspaceIdOrderByCreatedAtAsc(Long workspaceId);

    Optional<Project> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
