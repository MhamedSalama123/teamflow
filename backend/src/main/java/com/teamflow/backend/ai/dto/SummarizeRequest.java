package com.teamflow.backend.ai.dto;

import jakarta.validation.constraints.NotNull;

public record SummarizeRequest(@NotNull Long projectId) {}
