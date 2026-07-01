package com.teamflow.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AskRequest(@NotNull Long projectId, @NotBlank @Size(max = 2000) String question) {}
