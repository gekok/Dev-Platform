package dev.platform.board.domain.aggregate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Board(
        Long id,
        Long projectId,
        String name,
        String key,
        Status status,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<Column> columns) {

    public enum Status {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    public record Column(Long id, Long boardId, String name, int position) {
    }

    public Board {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public static Board create(Long projectId, String name, String key, UUID creator,
            boolean createDefaultColumns, OffsetDateTime now) {
        validateName(name);
        validateKey(key);
        if (projectId == null || creator == null) {
            throw ProjectRuleViolation.validation("Project and creator are required.");
        }
        List<Column> columns = createDefaultColumns
                ? List.of(new Column(null, null, "Todo", 0),
                        new Column(null, null, "In Progress", 1),
                        new Column(null, null, "Done", 2))
                : List.of();
        return new Board(null, projectId, name.trim(), key, Status.ACTIVE, creator,
                now, now, columns);
    }

    public static String validateName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 255) {
            throw ProjectRuleViolation.validation("name must contain 1 to 255 characters.");
        }
        return name.trim();
    }

    public static String validateColumnName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) {
            throw ProjectRuleViolation.validation("name must contain 1 to 100 characters.");
        }
        return name.trim();
    }

    public static String validateKey(String key) {
        if (key == null || !key.matches("[a-z0-9][a-z0-9-]{0,18}[a-z0-9]")) {
            throw ProjectRuleViolation.validation(
                    "boardKey must be 2 to 20 lowercase letters, digits, or hyphens.");
        }
        return key;
    }

    public Board rename(String newName, OffsetDateTime now) {
        requireActive();
        String normalized = validateName(newName);
        return normalized.equals(name) ? this
                : new Board(id, projectId, normalized, key, status, createdByUserId,
                        createdAt, now, columns);
    }

    public Board changeStatus(Status newStatus, OffsetDateTime now) {
        if (newStatus == null) {
            throw ProjectRuleViolation.validation("status is required.");
        }
        if (status == newStatus) {
            return this;
        }
        if (status != Status.ACTIVE && newStatus != Status.ACTIVE) {
            throw ProjectRuleViolation.conflict("INVALID_STATUS_TRANSITION",
                    "A completed or cancelled Board must be reopened before changing status.");
        }
        return new Board(id, projectId, name, key, newStatus, createdByUserId,
                createdAt, now, columns);
    }

    public Board addColumn(String name, Integer requestedPosition, OffsetDateTime now) {
        requireActive();
        String normalized = validateColumnName(name);
        if (columns.size() >= 20) {
            throw ProjectRuleViolation.unprocessable("COLUMN_LIMIT_EXCEEDED",
                    "A Board can contain at most 20 columns.");
        }
        int position = requestedPosition == null ? columns.size() : requestedPosition;
        if (position < 0 || position > columns.size()) {
            throw ProjectRuleViolation.validation("position must be between 0 and the current column count.");
        }
        List<Column> updated = new ArrayList<>(columns.size() + 1);
        for (Column column : columns) {
            updated.add(column.position() >= position
                    ? new Column(column.id(), column.boardId(), column.name(), column.position() + 1)
                    : column);
        }
        updated.add(new Column(null, id, normalized, position));
        updated.sort(java.util.Comparator.comparingInt(Column::position));
        return withColumns(updated, now);
    }

    public Board renameColumn(Long columnId, String name, OffsetDateTime now) {
        requireActive();
        String normalized = validateColumnName(name);
        boolean found = false;
        List<Column> updated = new ArrayList<>(columns.size());
        for (Column column : columns) {
            if (Objects.equals(column.id(), columnId)) {
                found = true;
                updated.add(new Column(column.id(), column.boardId(), normalized, column.position()));
            } else {
                updated.add(column);
            }
        }
        if (!found) {
            throw ProjectRuleViolation.notFound("Column not found.");
        }
        return withColumns(updated, now);
    }

    public Board reorder(List<Long> orderedIds, OffsetDateTime now) {
        requireActive();
        if (orderedIds == null || orderedIds.isEmpty()
                || new HashSet<>(orderedIds).size() != orderedIds.size()
                || orderedIds.size() != columns.size()) {
            throw ProjectRuleViolation.validation("columnIds must contain every Board column exactly once.");
        }
        var current = columns.stream().map(Column::id).collect(java.util.stream.Collectors.toSet());
        if (!current.equals(new HashSet<>(orderedIds))) {
            throw ProjectRuleViolation.validation("columnIds must contain only columns belonging to this Board.");
        }
        List<Column> updated = new ArrayList<>(orderedIds.size());
        for (int position = 0; position < orderedIds.size(); position++) {
            Long columnId = orderedIds.get(position);
            Column column = columns.stream().filter(item -> Objects.equals(item.id(), columnId))
                    .findFirst().orElseThrow();
            updated.add(new Column(column.id(), id, column.name(), position));
        }
        return withColumns(updated, now);
    }

    public Board removeColumn(Long columnId, OffsetDateTime now) {
        requireActive();
        if (columns.stream().noneMatch(column -> Objects.equals(column.id(), columnId))) {
            throw ProjectRuleViolation.notFound("Column not found.");
        }
        List<Column> updated = new ArrayList<>(columns.size() - 1);
        for (Column column : columns) {
            if (!Objects.equals(column.id(), columnId)) {
                updated.add(new Column(column.id(), id, column.name(), updated.size()));
            }
        }
        return withColumns(updated, now);
    }

    public void requireActive() {
        if (status != Status.ACTIVE) {
            throw ProjectRuleViolation.conflict("BOARD_NOT_ACTIVE", "Board is not active.");
        }
    }

    private Board withColumns(List<Column> updated, OffsetDateTime now) {
        return new Board(id, projectId, name, key, status, createdByUserId,
                createdAt, now, updated);
    }
}
