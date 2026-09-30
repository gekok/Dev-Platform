package dev.platform.board.domain;

/**
 * Cột trong bảng (column trong Kanban).
 *
 * @deprecated Kỳ 1 thay bằng Aggregate + infra entity.
 * Tên cột lưu dạng String trong BoardAggregate.BoardColumnValue.
 */
@Deprecated
public enum BoardColumnStatus {
    TODO, IN_PROGRESS, DONE, REVIEW, BACKLOG
}
