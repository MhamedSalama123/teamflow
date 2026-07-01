package com.teamflow.backend.file;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {

    List<ProjectFile> findByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);
}
