package dev.platform.user.application.port.out;

import dev.platform.user.domain.repository.UserRepository;

// Cổng ra: lưu user (kế thừa repo domain, theo mẫu ATS SaveCandidatePort).
public interface SaveUserPort extends UserRepository {
}
