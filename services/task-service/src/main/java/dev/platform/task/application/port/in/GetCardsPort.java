package dev.platform.task.application.port.in;

import java.util.List;

import dev.platform.task.domain.aggregate.CardAggregate;

// Cổng vào: đọc thẻ theo bảng (lọc cột tùy chọn).
public interface GetCardsPort {

    List<CardAggregate> getByBoard(Long boardId, Long columnId);
}
