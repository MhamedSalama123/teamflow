package com.teamflow.backend.project;

import com.teamflow.backend.project.dto.CreateProjectRequest;
import com.teamflow.backend.project.dto.ProjectResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            Authentication authentication,
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(authentication.getName(), workspaceId, request);
    }

    @GetMapping
    public List<ProjectResponse> list(Authentication authentication, @PathVariable Long workspaceId) {
        return projectService.list(authentication.getName(), workspaceId);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Authentication authentication,
            @PathVariable Long workspaceId,
            @PathVariable Long projectId) {
        projectService.delete(authentication.getName(), workspaceId, projectId);
    }
}
