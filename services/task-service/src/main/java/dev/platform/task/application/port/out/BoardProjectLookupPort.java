package dev.platform.task.application.port.out;

import java.util.Optional;

/** Resolves a persisted Board's owning Project; caller-supplied Project IDs are not accepted. */
public interface BoardProjectLookupPort {

    /** Empty means the Board does not exist; lookup failures should be raised by the adapter. */
    Optional<Long> findProjectId(Long boardId);
}
