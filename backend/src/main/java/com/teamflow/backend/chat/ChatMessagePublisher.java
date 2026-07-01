package com.teamflow.backend.chat;

import com.teamflow.backend.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Broadcasts a new chat message to the project's chat topic ({@code /topic/projects/{id}/chat}),
 * after the surrounding transaction commits so subscribers only see persisted messages.
 */
@Component
@RequiredArgsConstructor
public class ChatMessagePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(Long projectId, ChatMessageResponse message) {
        String destination = "/topic/projects/" + projectId + "/chat";
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(destination, message);
                }
            });
        } else {
            messagingTemplate.convertAndSend(destination, message);
        }
    }
}
