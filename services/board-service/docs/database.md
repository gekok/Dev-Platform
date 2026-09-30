# Database — board-service (`board_db`)

- DB: `board_db` (Postgres, port 5433 theo `application.yml`).
- Service chỉ giữ bảng + cột. Không giữ thẻ chi tiết (thẻ ở `task_db`).
- ID liên service chỉ giữ dạng số (`Long`/`UUID`), không FK JPA chéo DB.

## Bảng `boards`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `BIGINT` | PK, `IDENTITY` tự tăng |
| `name` | `VARCHAR(255)` | `NOT NULL` |
| `board_key` | `VARCHAR(20)` | `NOT NULL`, `UNIQUE` (key kiểu MSS-xxx, validate `[A-Z0-9]{2,20}` ở Aggregate + DTO) |
| `owner_id` | `UUID` | `NOT NULL`, tham chiếu số sang `user_db`, không FK |

Index: unique trên `board_key`; index thường trên `owner_id` (query `listMine`).

## Bảng `board_columns`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `BIGINT` | PK, `IDENTITY` tự tăng |
| `board_id` | `BIGINT` | `NOT NULL`, FK → `boards(id)`, cascade xóa theo board (JPA `cascade ALL + orphanRemoval`) |
| `name` | `VARCHAR(100)` | `NOT NULL` |
| `position` | `INT` | `NOT NULL`, thứ tự cột từ 0, sắp xếp `ORDER BY position ASC` |

## Quan hệ

- `boards 1 — N board_columns` (xóa board kéo theo cột).
- Không tạo bảng mặc định ở DB: 3 cột `To Do / In Progress / Done` do `BoardAggregate.create()` tự sinh khi request rỗng.
