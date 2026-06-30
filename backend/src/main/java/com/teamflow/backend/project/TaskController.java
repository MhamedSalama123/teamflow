package com.teamflow.backend.project;

import com.teamflow.backend.project.dto.AssignTaskRequest;
import com.teamflow.backend.project.dto.CreateTaskRequest;
import com.teamflow.backend.project.dto.TaskResponse;
import com.teamflow.backend.project.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public List<TaskResponse> list(Authentication authentication, @PathVariable Long projectId) {
        return taskService.list(authentication.getName(), projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(
            Authentication authentication,
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(authentication.getName(), projectId, request);
    }

    @PutMapping("/{taskId}")
    public TaskResponse update(
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(authentication.getName(), projectId, taskId, request);
    }

    @PutMapping("/{taskId}/assign")
    public TaskResponse assign(
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody AssignTaskRequest request) {
        return taskService.assign(authentication.getName(), projectId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        taskService.delete(authentication.getName(), projectId, taskId);
    }
}
