package com.teamflow.backend.notification;

import com.teamflow.backend.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pushes a notification to its recipient's personal STOMP queue ({@code /user/queue/notifications}),
 * after the surrounding transaction commits so it is only sent for persisted notifications.
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    static final String USER_DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(String recipientEmail, NotificationResponse notification) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(recipientEmail, notification);
                }
            });
        } else {
            send(recipientEmail, notification);
        }
    }

    private void send(String recipientEmail, NotificationResponse notification) {
        messagingTemplate.convertAndSendToUser(recipientEmail, USER_DESTINATION, notification);
    }
}
