package com.teamflow.backend.project;

import com.teamflow.backend.project.TaskEvent.TaskEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publishes task change events to the project's STOMP topic, after the transaction commits. */
@Component
@RequiredArgsConstructor
public class TaskEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(Long projectId, TaskEventType type, Long taskId) {
        String destination = "/topic/projects/" + projectId;
        TaskEvent event = new TaskEvent(type, taskId);

        // Defer until after commit so subscribers that re-fetch see the persisted state. When no
        // transaction is active (e.g. tests) send immediately.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(destination, event);
                }
            });
        } else {
            messagingTemplate.convertAndSend(destination, event);
        }
    }
}
