package com.teamflow.backend.ai.dto;

import com.teamflow.backend.project.TaskPriority;
import java.time.LocalDate;

/** A single task suggested by the AI: a title, a priority, and an optional due date. */
public record GeneratedTask(String title, TaskPriority priority, LocalDate dueDate) {}
