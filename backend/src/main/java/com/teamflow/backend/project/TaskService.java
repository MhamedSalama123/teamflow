package com.teamflow.backend.project;

import com.teamflow.backend.notification.NotificationService;
import com.teamflow.backend.notification.NotificationType;
import com.teamflow.backend.project.TaskEvent.TaskEventType;
import com.teamflow.backend.project.dto.AssignTaskRequest;
import com.teamflow.backend.project.dto.CreateTaskRequest;
import com.teamflow.backend.project.dto.TaskResponse;
import com.teamflow.backend.project.dto.UpdateTaskRequest;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import com.teamflow.backend.workspace.WorkspaceMember;
import com.teamflow.backend.workspace.WorkspaceMembershipService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceMembershipService membershipService;
    private final TaskEventPublisher taskEventPublisher;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<TaskResponse> list(String actorEmail, Long projectId) {
        requireProjectContext(actorEmail, projectId);
        return taskRepository.findByProjectIdOrderByStatusAscPositionAscIdAsc(projectId).stream()
                .map(TaskResponse::from)
                .toList();
    }

    /** Creates a task in the TODO column, appended to the end of it. */
    @Transactional
    public TaskResponse create(String actorEmail, Long projectId, CreateTaskRequest request) {
        ProjectContext context = requireProjectContext(actorEmail, projectId);
        Project project = context.project();
        User assignee = resolveAssignee(project, request.assigneeId());
        int position = (int) taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.TODO);
        Task task = taskRepository.save(Task.builder()
                .project(project)
                .title(request.title().trim())
                .description(blankToNull(request.description()))
                .status(TaskStatus.TODO)
                .priority(request.priorityOrDefault())
                .dueDate(request.dueDate())
                .assignee(assignee)
                .position(position)
                .build());
        taskEventPublisher.publish(projectId, TaskEventType.CREATED, task.getId());
        notifyAssignment(context.actor(), project, task, null, assignee);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse update(
            String actorEmail, Long projectId, Long taskId, UpdateTaskRequest request) {
        ProjectContext context = requireProjectContext(actorEmail, projectId);
        Project project = context.project();
        Task task = requireTask(projectId, taskId);
        User previousAssignee = task.getAssignee();

        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        User newAssignee = resolveAssignee(project, request.assigneeId());
        task.setAssignee(newAssignee);

        if (task.getStatus() != request.status() || request.position() != null) {
            moveTask(task, request.status(), request.position());
        } else {
            taskRepository.save(task);
        }
        taskEventPublisher.publish(projectId, TaskEventType.UPDATED, task.getId());
        notifyAssignment(context.actor(), project, task, previousAssignee, newAssignee);
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(String actorEmail, Long projectId, Long taskId) {
        requireProjectContext(actorEmail, projectId);
        Task task = requireTask(projectId, taskId);
        taskRepository.delete(task);
        taskEventPublisher.publish(projectId, TaskEventType.DELETED, task.getId());
    }

    @Transactional
    public TaskResponse assign(
            String actorEmail, Long projectId, Long taskId, AssignTaskRequest request) {
        ProjectContext context = requireProjectContext(actorEmail, projectId);
        Project project = context.project();
        Task task = requireTask(projectId, taskId);
        User previousAssignee = task.getAssignee();
        User newAssignee = resolveAssignee(project, request.assigneeId());
        task.setAssignee(newAssignee);
        Task saved = taskRepository.save(task);
        taskEventPublisher.publish(projectId, TaskEventType.UPDATED, saved.getId());
        notifyAssignment(context.actor(), project, saved, previousAssignee, newAssignee);
        return TaskResponse.from(saved);
    }

    /**
     * Places {@code task} into {@code newStatus} at {@code desiredIndex} (or the end when null),
     * renumbering the affected column(s). All entity reads happen before the status is mutated so the
     * result does not depend on autoflush ordering.
     */
    private void moveTask(Task task, TaskStatus newStatus, Integer desiredIndex) {
        Long projectId = task.getProject().getId();
        TaskStatus oldStatus = task.getStatus();

        if (oldStatus != newStatus) {
            List<Task> oldColumn = new ArrayList<>(
                    taskRepository.findByProjectIdAndStatusOrderByPositionAscIdAsc(projectId, oldStatus));
            oldColumn.removeIf(t -> t.getId().equals(task.getId()));
            renumber(oldColumn);
            taskRepository.saveAll(oldColumn);
        }

        List<Task> targetColumn = new ArrayList<>(
                taskRepository.findByProjectIdAndStatusOrderByPositionAscIdAsc(projectId, newStatus));
        targetColumn.removeIf(t -> t.getId().equals(task.getId()));
        int index = desiredIndex == null
                ? targetColumn.size()
                : Math.max(0, Math.min(desiredIndex, targetColumn.size()));
        task.setStatus(newStatus);
        targetColumn.add(index, task);
        renumber(targetColumn);
        taskRepository.saveAll(targetColumn);
    }

    private static void renumber(List<Task> column) {
        for (int i = 0; i < column.size(); i++) {
            column.get(i).setPosition(i);
        }
    }

    /** The project plus the acting user, after asserting the caller is an active workspace member. */
    private record ProjectContext(Project project, User actor) {}

    private ProjectContext requireProjectContext(String actorEmail, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(ProjectNotFoundException::new);
        WorkspaceMember membership =
                membershipService.requireActiveMembership(project.getWorkspace().getId(), actorEmail);
        return new ProjectContext(project, membership.getUser());
    }

    /** Notifies the new assignee when a task is assigned to someone other than the actor. */
    private void notifyAssignment(User actor, Project project, Task task, User previous, User next) {
        if (next == null
                || next.getId().equals(actor.getId())
                || (previous != null && previous.getId().equals(next.getId()))) {
            return;
        }
        String message = "%s assigned you the task '%s' in %s."
                .formatted(displayName(actor), task.getTitle(), project.getName());
        notificationService.notify(
                next, NotificationType.TASK_ASSIGNED, message, project.getWorkspace().getId());
    }

    private static String displayName(User user) {
        return (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : user.getUsername();
    }

    private Task requireTask(Long projectId, Long taskId) {
        return taskRepository.findByIdAndProjectId(taskId, projectId)
                .orElseThrow(TaskNotFoundException::new);
    }

    /** Resolves an assignee id to a user, requiring active workspace membership; null clears it. */
    private User resolveAssignee(Project project, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!membershipService.isActiveMember(project.getWorkspace().getId(), assigneeId)) {
            throw new InvalidAssigneeException();
        }
        return userRepository.findById(assigneeId).orElseThrow(InvalidAssigneeException::new);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
