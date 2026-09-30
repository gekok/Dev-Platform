package dev.platform.task.domain;

/**
 * Trạng thái thẻ trên bảng Kanban.
 *
 * @deprecated Kỳ 1 thay bằng CardAggregate.Status.
 */
@Deprecated
public enum CardStatus {
    TODO, IN_PROGRESS, DONE
}
