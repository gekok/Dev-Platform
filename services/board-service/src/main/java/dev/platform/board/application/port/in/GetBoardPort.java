package dev.platform.board.application.port.in;

import dev.platform.board.domain.aggregate.BoardAggregate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Cổng vào: đọc bảng (chi tiết + danh sách của tôi).
public interface GetBoardPort {

    Optional<BoardAggregate> getById(Long id, UUID requesterId);

    List<BoardAggregate> listMine(UUID ownerId);
}
