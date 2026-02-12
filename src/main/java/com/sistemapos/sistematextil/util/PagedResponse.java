package com.sistemapos.sistematextil.util;

import java.util.List;

import org.springframework.data.domain.Page;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        int totalPages,
        long totalElements,
        int numberOfElements,
        boolean first,
        boolean last,
        boolean empty
) {
    public static <T> PagedResponse<T> fromPage(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumberOfElements(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }
}
