package com.teamflow.backend.chat;

import com.teamflow.backend.chat.dto.ChatAttachmentResponse;
import com.teamflow.backend.chat.dto.ChatMessageResponse;
import com.teamflow.backend.chat.dto.SendMessageRequest;
import com.teamflow.backend.user.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** Paginated message history, newest first. */
    @GetMapping("/messages")
    public PagedResponse<ChatMessageResponse> history(
            Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return chatService.history(authentication.getName(), projectId, page, size);
    }

    /** REST fallback for sending a message; real-time clients receive it over the chat topic too. */
    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse send(
            Authentication authentication,
            @PathVariable Long projectId,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.send(authentication.getName(), projectId, request);
    }

    /** Uploads a file and returns its URL so it can be attached to a subsequent message. */
    @PostMapping("/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatAttachmentResponse uploadAttachment(
            Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        return chatService.uploadAttachment(authentication.getName(), projectId, file);
    }
}
