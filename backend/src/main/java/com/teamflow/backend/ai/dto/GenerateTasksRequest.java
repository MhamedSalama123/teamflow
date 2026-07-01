package com.teamflow.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateTasksRequest(@NotBlank @Size(max = 5000) String description) {}
