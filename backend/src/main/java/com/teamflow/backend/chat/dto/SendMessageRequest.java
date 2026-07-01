package com.teamflow.backend.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * A chat message to send. Either {@code content} or an attachment (or both) must be present; that
 * cross-field rule is enforced in the service. {@code attachmentUrl}/{@code attachmentName} come from
 * a prior upload to the attachments endpoint.
 */
public record SendMessageRequest(
        @Size(max = 5000) String content,
        @Size(max = 512) String attachmentUrl,
        @Size(max = 255) String attachmentName) {}
