package com.teamflow.backend.chat;

import com.teamflow.backend.chat.dto.ChatAttachmentResponse;
import com.teamflow.backend.chat.dto.ChatMessageResponse;
import com.teamflow.backend.chat.dto.SendMessageRequest;
import com.teamflow.backend.notification.NotificationService;
import com.teamflow.backend.notification.NotificationType;
import com.teamflow.backend.project.Project;
import com.teamflow.backend.project.ProjectNotFoundException;
import com.teamflow.backend.project.ProjectRepository;
import com.teamflow.backend.user.FileStorageService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import com.teamflow.backend.user.dto.PagedResponse;
import com.teamflow.backend.workspace.WorkspaceMember;
import com.teamflow.backend.workspace.WorkspaceMembershipService;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    /** Matches {@code @username} tokens; usernames are word characters (letters, digits, underscore). */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceMembershipService membershipService;
    private final ChatMessagePublisher chatMessagePublisher;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;

    /** Persists a message, broadcasts it live, and notifies any mentioned workspace members. */
    @Transactional
    public ChatMessageResponse send(String actorEmail, Long projectId, SendMessageRequest request) {
        ProjectContext context = requireProjectContext(actorEmail, projectId);
        String content = blankToNull(request.content());
        String attachmentUrl = blankToNull(request.attachmentUrl());
        if (content == null && attachmentUrl == null) {
            throw new EmptyMessageException();
        }
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .project(context.project())
                .sender(context.actor())
                .content(content)
                .attachmentUrl(attachmentUrl)
                .attachmentName(attachmentUrl == null ? null : blankToNull(request.attachmentName()))
                .build());
        ChatMessageResponse response = ChatMessageResponse.from(message);
        chatMessagePublisher.publish(projectId, response);
        notifyMentions(context, content);
        return response;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ChatMessageResponse> history(
            String actorEmail, Long projectId, int page, int size) {
        requireProjectContext(actorEmail, projectId);
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<ChatMessageResponse> messages = chatMessageRepository
                .findByProjectIdOrderByCreatedAtDescIdDesc(projectId, pageable)
                .map(ChatMessageResponse::from);
        return PagedResponse.from(messages);
    }

    /** Stores an uploaded attachment and returns its URL so it can be attached to a later message. */
    @Transactional(readOnly = true)
    public ChatAttachmentResponse uploadAttachment(
            String actorEmail, Long projectId, MultipartFile file) {
        requireProjectContext(actorEmail, projectId);
        String url = fileStorageService.storeAttachment(file);
        return new ChatAttachmentResponse(url, file.getOriginalFilename());
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

    /**
     * Notifies every distinct {@code @username} in the message who is an active member of the project's
     * workspace, other than the author.
     */
    private void notifyMentions(ProjectContext context, String content) {
        if (content == null) {
            return;
        }
        Long workspaceId = context.project().getWorkspace().getId();
        for (String username : parseMentions(content)) {
            userRepository.findByUsernameAndDeletedAtIsNull(username)
                    .filter(u -> !u.getId().equals(context.actor().getId()))
                    .filter(u -> membershipService.isActiveMember(workspaceId, u.getId()))
                    .ifPresent(mentioned -> {
                        String message = "%s mentioned you in %s."
                                .formatted(displayName(context.actor()), context.project().getName());
                        notificationService.notify(
                                mentioned, NotificationType.CHAT_MENTION, message, workspaceId);
                    });
        }
    }

    private static Set<String> parseMentions(String content) {
        Set<String> usernames = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }
        return usernames;
    }

    private static String displayName(User user) {
        return (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : user.getUsername();
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
