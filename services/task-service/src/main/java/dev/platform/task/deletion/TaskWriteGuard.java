package dev.platform.task.deletion;

import dev.platform.task.deletion.persistence.repository.BoardWriteLockJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskWriteGuard {

    private final BoardWriteLockJpaRepository boardWriteLockRepository;

    public void requireBoardWritable(Long boardId) {
        if (boardWriteLockRepository.existsById(boardId)) {
            throw new IllegalStateException("Board is locked for Project deletion: " + boardId);
        }
    }
}
