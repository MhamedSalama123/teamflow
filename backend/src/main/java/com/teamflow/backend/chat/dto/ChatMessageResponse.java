package com.teamflow.backend.chat.dto;

import com.teamflow.backend.chat.ChatMessage;
import com.teamflow.backend.user.User;
import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        String content,
        String attachmentUrl,
        String attachmentName,
        SenderSummary sender,
        Instant createdAt) {

    /** Minimal public view of a message's sender. */
    public record SenderSummary(Long id, String username, String fullName, String photoUrl) {

        static SenderSummary from(User user) {
            return new SenderSummary(
                    user.getId(), user.getUsername(), user.getFullName(), user.getPhotoUrl());
        }
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getContent(),
                message.getAttachmentUrl(),
                message.getAttachmentName(),
                SenderSummary.from(message.getSender()),
                message.getCreatedAt());
    }
}
