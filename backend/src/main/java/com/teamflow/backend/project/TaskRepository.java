package com.teamflow.backend.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectIdOrderByStatusAscPositionAscIdAsc(Long projectId);

    List<Task> findByProjectIdAndStatusOrderByPositionAscIdAsc(Long projectId, TaskStatus status);

    Optional<Task> findByIdAndProjectId(Long id, Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);
}
