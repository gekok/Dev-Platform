package dev.platform.board.domain.aggregate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cụm Board thuần, không phụ thuộc JPA.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoardAggregate {

    private Long id;
    private String name;
    private String key;
    private UUID ownerId;
    private List<BoardColumnValue> columns = new ArrayList<>();

    // Giá trị cột trong aggregate.
    public record BoardColumnValue(Long id, String name, int position) {
    }

    // Tạo bảng mới, tự sinh 3 cột mặc định nếu rỗng.
    public static BoardAggregate create(String name, String key, UUID ownerId, List<String> columns) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ten bang khong duoc trong");
        }
        if (key == null || !key.matches("[A-Z0-9]{2,20}")) {
            throw new IllegalArgumentException("Key 2-20 ky tu hoa so");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId khong duoc null");
        }
        List<String> src = columns == null ? List.of() : columns.stream()
                .filter(c -> c != null && !c.isBlank())
                .toList();
        if (src.isEmpty()) {
            src = List.of("To Do", "In Progress", "Done");
        }
        List<BoardColumnValue> values = new ArrayList<>();
        for (int i = 0; i < src.size(); i++) {
            values.add(new BoardColumnValue(null, src.get(i), i));
        }
        return new BoardAggregate(null, name.trim(), key, ownerId, values);
    }

    // Thêm cột cuối bảng.
    public BoardColumnValue addColumn(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ten cot khong duoc trong");
        }
        BoardColumnValue value = new BoardColumnValue(null, name.trim(), columns.size());
        columns.add(value);
        return value;
    }

    // Xếp lại vị trí cột, phải đủ id hiện có.
    public void reorder(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new IllegalArgumentException("orderedColumnIds khong duoc rong");
        }
        if (orderedIds.size() != columns.size()) {
            throw new IllegalArgumentException("Thieu hoac thua id cot");
        }
        if (new HashSet<>(orderedIds).size() != orderedIds.size()) {
            throw new IllegalArgumentException("orderedColumnIds bi trung");
        }
        List<Long> current = columns.stream().map(BoardColumnValue::id).toList();
        if (!new HashSet<>(current).equals(new HashSet<>(orderedIds))) {
            throw new IllegalArgumentException("Cot khong thuoc bang nay");
        }
        List<BoardColumnValue> next = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            BoardColumnValue old = columns.stream()
                    .filter(c -> id != null && id.equals(c.id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Cot khong thuoc bang nay"));
            next.add(new BoardColumnValue(old.id(), old.name(), i));
        }
        columns.clear();
        columns.addAll(next);
    }
}
