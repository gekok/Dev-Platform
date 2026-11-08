package dev.platform.board.application.query;

import java.util.List;

public record ProjectPage<T>(List<T> items, int page, int size, long totalElements) {
    public int totalPages() {
        return (int) Math.ceil((double) totalElements / size);
    }
}
