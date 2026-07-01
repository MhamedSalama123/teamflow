package com.teamflow.backend.ai;

import com.teamflow.backend.ai.dto.AnswerResponse;
import com.teamflow.backend.ai.dto.AskRequest;
import com.teamflow.backend.ai.dto.GenerateTasksRequest;
import com.teamflow.backend.ai.dto.GenerateTasksResponse;
import com.teamflow.backend.ai.dto.SummarizeRequest;
import com.teamflow.backend.ai.dto.SummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/summarize")
    public SummaryResponse summarize(
            Authentication authentication, @Valid @RequestBody SummarizeRequest request) {
        return aiService.summarizeChat(authentication.getName(), request.projectId());
    }

    @PostMapping("/generate-tasks")
    public GenerateTasksResponse generateTasks(
            Authentication authentication, @Valid @RequestBody GenerateTasksRequest request) {
        return aiService.generateTasks(authentication.getName(), request.description());
    }

    @PostMapping("/ask")
    public AnswerResponse ask(
            Authentication authentication, @Valid @RequestBody AskRequest request) {
        return aiService.ask(authentication.getName(), request.projectId(), request.question());
    }
}
