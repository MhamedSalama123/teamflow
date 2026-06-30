package com.teamflow.backend.user.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Minimal, stable pagination envelope so clients aren't coupled to Spring's {@code Page} JSON shape. */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
