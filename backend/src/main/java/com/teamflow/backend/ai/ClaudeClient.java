package com.teamflow.backend.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import java.time.Duration;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around the Anthropic Java SDK. Sends a single-turn request (system prompt + one user
 * message) to Claude and returns the concatenated text of the response. Failures from the SDK are
 * translated into {@link AiUnavailableException} so controllers surface a 503 rather than a 500.
 */
@Component
public class ClaudeClient {

    private final AnthropicClient client;
    private final String model;

    public ClaudeClient(
            @Value("${anthropic.api-key:anthropic-api-key}") String apiKey,
            @Value("${anthropic.model:claude-sonnet-4-6}") String model,
            @Value("${anthropic.timeout-seconds:60}") long timeoutSeconds) {
        // A bounded timeout (well below the SDK's 10-minute default) so a bad key or unreachable API
        // fails fast into a 503 instead of leaving the request — and the UI — hanging.
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.model = model;
    }

    /** Sends {@code userPrompt} under {@code systemPrompt} and returns Claude's text response. */
    public String complete(String systemPrompt, String userPrompt, long maxTokens) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .build();
        try {
            Message response = client.messages().create(params);
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining("\n"))
                    .trim();
        } catch (RuntimeException e) {
            throw new AiUnavailableException(e);
        }
    }
}
