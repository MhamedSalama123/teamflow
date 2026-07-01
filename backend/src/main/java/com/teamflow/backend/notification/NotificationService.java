package com.teamflow.backend.notification;

import com.teamflow.backend.notification.dto.NotificationResponse;
import com.teamflow.backend.user.User;
import com.teamflow.backend.user.UserNotFoundException;
import com.teamflow.backend.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPublisher notificationPublisher;
    private final UserRepository userRepository;

    /** Persists an in-app notification for {@code recipient} and pushes it to them live. */
    @Transactional
    public void notify(User recipient, NotificationType type, String message, Long workspaceId) {
        Notification saved = notificationRepository.save(Notification.builder()
                .user(recipient)
                .type(type)
                .message(message)
                .workspaceId(workspaceId)
                .read(false)
                .build());
        notificationPublisher.publish(recipient.getEmail(), NotificationResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(String email) {
        Long userId = requireUserId(email);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markRead(String email, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, requireUserId(email))
                .orElseThrow(NotificationNotFoundException::new);
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private Long requireUserId(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(UserNotFoundException::new)
                .getId();
    }
}
