package dev.platform.task.application.port.out;

import dev.platform.task.domain.repository.CardRepository;

// Cổng ra: lưu Card (adapter JPA cài đặt).
public interface SaveCardPort extends CardRepository {
}
