package dev.platform.board.api.dto;

import dev.platform.board.application.query.ProjectPage;
import dev.platform.board.application.query.BoardPage;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <A, T> PageResponse<T> from(ProjectPage<A> page, Function<A, T> mapper) {
        return new PageResponse<>(page.items().stream().map(mapper).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    public static <A, T> PageResponse<T> from(BoardPage<A> page, Function<A, T> mapper) {
        return new PageResponse<>(page.items().stream().map(mapper).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
