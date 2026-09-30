package dev.platform.board.application.port.out;

import dev.platform.board.domain.repository.BoardRepository;

// Cổng ra: lưu Board (adapter JPA cài đặt).
public interface SaveBoardPort extends BoardRepository {
}
