package dev.platform.task.application.query;

import java.util.List;

public record BoardStateSnapshot(
        Long boardId,
        Long projectId,
        String boardStatus,
        String projectStatus,
        List<ColumnSnapshot> columns) {

    public record ColumnSnapshot(Long id, String name, int position) {
    }
}
