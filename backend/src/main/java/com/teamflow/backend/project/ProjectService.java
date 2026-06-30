package com.teamflow.backend.project;

import com.teamflow.backend.project.dto.CreateProjectRequest;
import com.teamflow.backend.project.dto.ProjectResponse;
import com.teamflow.backend.workspace.WorkspaceMember;
import com.teamflow.backend.workspace.WorkspaceMembershipService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceMembershipService membershipService;

    /** Creates a project in the workspace. Any active member may create one. */
    @Transactional
    public ProjectResponse create(String actorEmail, Long workspaceId, CreateProjectRequest request) {
        WorkspaceMember membership = membershipService.requireActiveMembership(workspaceId, actorEmail);
        Project project = projectRepository.save(Project.builder()
                .workspace(membership.getWorkspace())
                .name(request.name().trim())
                .description(blankToNull(request.description()))
                .build());
        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(String actorEmail, Long workspaceId) {
        membershipService.requireActiveMembership(workspaceId, actorEmail);
        return projectRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /** Deletes a project (and, by cascade, its tasks). Only owners/admins may do this. */
    @Transactional
    public void delete(String actorEmail, Long workspaceId, Long projectId) {
        membershipService.requireManager(workspaceId, actorEmail);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(ProjectNotFoundException::new);
        projectRepository.delete(project);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
