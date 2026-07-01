package com.teamflow.backend.chat.dto;

/** The stored location of an uploaded attachment plus the original filename to display. */
public record ChatAttachmentResponse(String url, String name) {}
