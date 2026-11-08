package dev.platform.task.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class CardCursor {
    private CardCursor() {}

    public record Anchor(int position, long id) {}

    public static String encode(Long project, Long board, Long column, int position, long id) {
        if (project == null || project < 1 || board == null || board < 1 || column == null || column < 1
                || position < 0 || id < 1) {
            throw new IllegalArgumentException("Invalid card cursor scope or anchor");
        }
        String raw = "1:" + project + ":" + board + ":" + column
                + ":position_desc_id_desc:" + position + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Anchor decode(String cursor, Long project, Long board, Long column) {
        try {
            if (cursor == null || project == null || project < 1 || board == null || board < 1
                    || column == null || column < 1
                    || cursor.length() > 256 || !cursor.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 7 || !parts[0].equals("1") || !parts[4].equals("position_desc_id_desc")
                    || !Objects.equals(Long.valueOf(parts[1]), project)
                    || !Objects.equals(Long.valueOf(parts[2]), board)
                    || !Objects.equals(Long.valueOf(parts[3]), column)) {
                throw new IllegalArgumentException();
            }
            int position = Integer.parseInt(parts[5]);
            long id = Long.parseLong(parts[6]);
            if (position < 0 || id <= 0) throw new IllegalArgumentException();
            return new Anchor(position, id);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid card cursor");
        }
    }
}
