package com.teamflow.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.backend.ai.dto.AnswerResponse;
import com.teamflow.backend.ai.dto.GenerateTasksResponse;
import com.teamflow.backend.ai.dto.GeneratedTask;
import com.teamflow.backend.ai.dto.SummaryResponse;
import com.teamflow.backend.chat.ChatMessage;
import com.teamflow.backend.chat.ChatMessageRepository;
import com.teamflow.backend.project.Project;
import com.teamflow.backend.project.ProjectNotFoundException;
import com.teamflow.backend.project.ProjectRepository;
import com.teamflow.backend.project.Task;
import com.teamflow.backend.project.TaskPriority;
import com.teamflow.backend.project.TaskRepository;
import com.teamflow.backend.user.User;
import com.teamflow.backend.workspace.WorkspaceMembershipService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final int RECENT_MESSAGE_LIMIT = 50;

    private final ProjectRepository projectRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceMembershipService membershipService;
    private final ClaudeClient claudeClient;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Summarizes the recent chat history of a project. */
    @Transactional(readOnly = true)
    public SummaryResponse summarizeChat(String actorEmail, Long projectId) {
        Project project = requireProjectAccess(actorEmail, projectId);
        List<ChatMessage> messages = recentMessages(projectId);
        if (messages.isEmpty()) {
            return new SummaryResponse("There are no chat messages in this project yet.");
        }
        String system = "You summarize team chat conversations. Produce a concise summary (a few "
                + "short bullet points or sentences) capturing key decisions, questions, and action "
                + "items. Do not invent information that is not in the transcript.";
        String prompt = "Summarize the following chat from project \"%s\":\n\n%s"
                .formatted(project.getName(), renderTranscript(messages));
        return new SummaryResponse(claudeClient.complete(system, prompt, 1024));
    }

    /** Turns a free-text description into a structured task list. */
    public GenerateTasksResponse generateTasks(String actorEmail, String description) {
        membershipService.requireActiveUser(actorEmail);
        String system = "You break a plain-text description of work into a list of actionable tasks. "
                + "Respond with ONLY a JSON array — no prose, no markdown fences. Each element is an "
                + "object with keys: \"title\" (string), \"priority\" (one of \"LOW\", \"MEDIUM\", "
                + "\"HIGH\"), and \"dueDate\" (a \"YYYY-MM-DD\" string, or null if none is implied). "
                + "Keep titles short and imperative.";
        String raw = claudeClient.complete(system, description, 2048);
        return new GenerateTasksResponse(parseTasks(raw));
    }

    /** Answers a free-form question using the project's tasks and recent chat as context. */
    @Transactional(readOnly = true)
    public AnswerResponse ask(String actorEmail, Long projectId, String question) {
        Project project = requireProjectAccess(actorEmail, projectId);
        String system = "You answer questions about a software project using only the provided "
                + "context (its tasks and recent chat). If the answer is not in the context, say so "
                + "plainly rather than guessing.";
        String prompt = ("Project: %s\n\n=== Tasks ===\n%s\n\n=== Recent chat ===\n%s\n\n"
                + "Question: %s")
                .formatted(
                        project.getName(),
                        renderTasks(projectId),
                        renderTranscript(recentMessages(projectId)),
                        question);
        return new AnswerResponse(claudeClient.complete(system, prompt, 1024));
    }

    private Project requireProjectAccess(String actorEmail, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(ProjectNotFoundException::new);
        membershipService.requireActiveMembership(project.getWorkspace().getId(), actorEmail);
        return project;
    }

    /** The most recent messages, returned oldest-first for a natural transcript. */
    private List<ChatMessage> recentMessages(Long projectId) {
        List<ChatMessage> newestFirst = chatMessageRepository
                .findByProjectIdOrderByCreatedAtDescIdDesc(
                        projectId, PageRequest.of(0, RECENT_MESSAGE_LIMIT))
                .getContent();
        List<ChatMessage> chronological = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(chronological);
        return chronological;
    }

    private String renderTranscript(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "(no messages)";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            String content = message.getContent();
            if (content == null || content.isBlank()) {
                content = message.getAttachmentName() != null
                        ? "[shared a file: " + message.getAttachmentName() + "]"
                        : "[attachment]";
            }
            sb.append(senderName(message.getSender())).append(": ").append(content).append('\n');
        }
        return sb.toString();
    }

    private String renderTasks(Long projectId) {
        List<Task> tasks =
                taskRepository.findByProjectIdOrderByStatusAscPositionAscIdAsc(projectId);
        if (tasks.isEmpty()) {
            return "(no tasks)";
        }
        StringBuilder sb = new StringBuilder();
        for (Task task : tasks) {
            sb.append("- [").append(task.getStatus()).append("] ")
                    .append(task.getTitle())
                    .append(" (priority ").append(task.getPriority());
            if (task.getAssignee() != null) {
                sb.append(", assigned to ").append(senderName(task.getAssignee()));
            }
            if (task.getDueDate() != null) {
                sb.append(", due ").append(task.getDueDate());
            }
            sb.append(")\n");
        }
        return sb.toString();
    }

    private List<GeneratedTask> parseTasks(String raw) {
        String json = extractJsonArray(raw);
        List<GeneratedTask> tasks = new ArrayList<>();
        try {
            JsonNode array = OBJECT_MAPPER.readTree(json);
            if (!array.isArray()) {
                return tasks;
            }
            for (JsonNode node : array) {
                String title = node.path("title").asText("").trim();
                if (title.isEmpty()) {
                    continue;
                }
                tasks.add(new GeneratedTask(
                        title,
                        parsePriority(node.path("priority").asText(null)),
                        parseDueDate(node.path("dueDate").asText(null))));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AiUnavailableException(e);
        }
        return tasks;
    }

    /** Extracts the JSON array substring, tolerating stray prose or markdown fences around it. */
    private static String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : "[]";
    }

    private static TaskPriority parsePriority(String value) {
        if (value == null) {
            return TaskPriority.MEDIUM;
        }
        try {
            return TaskPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskPriority.MEDIUM;
        }
    }

    private static LocalDate parseDueDate(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String senderName(User user) {
        return (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : user.getUsername();
    }
}
