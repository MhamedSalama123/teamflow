package com.teamflow.backend.realtime;

import com.teamflow.backend.project.ProjectRepository;
import com.teamflow.backend.security.JwtService;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserRepository;
import com.teamflow.backend.workspace.WorkspaceMembershipService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP connections and authorizes subscriptions. On CONNECT the JWT supplied in the
 * {@code Authorization} header is validated and bound to the session; on SUBSCRIBE the session user
 * must be an active member of the workspace owning the project topic.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String PROJECT_TOPIC_PREFIX = "/topic/projects/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMembershipService membershipService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            default -> {
                // No checks for other frames (SEND, DISCONNECT, heartbeats, ...).
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String email = resolveEmail(accessor.getFirstNativeHeader("Authorization"));
        if (email == null) {
            throw new MessagingException("Unauthorized WebSocket connection.");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private String resolveEmail(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!jwtService.isValidAccessToken(token)) {
            return null;
        }
        String email = jwtService.extractSubject(token);
        return userRepository.findByEmailAndDeletedAtIsNull(email).isPresent() ? email : null;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(PROJECT_TOPIC_PREFIX)) {
            // Only project topics are exposed to clients.
            throw new MessagingException("Unknown subscription destination.");
        }
        Principal user = accessor.getUser();
        Long projectId = parseProjectId(destination.substring(PROJECT_TOPIC_PREFIX.length()));
        Long workspaceId = projectId == null
                ? null
                : projectRepository.findWorkspaceIdById(projectId).orElse(null);
        Long userId = user == null
                ? null
                : userRepository.findByEmailAndDeletedAtIsNull(user.getName())
                        .map(User::getId)
                        .orElse(null);
        if (workspaceId == null || userId == null
                || !membershipService.isActiveMember(workspaceId, userId)) {
            throw new MessagingException("You cannot subscribe to this project.");
        }
    }

    private static Long parseProjectId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
