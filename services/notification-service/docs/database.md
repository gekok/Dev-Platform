# Database — notification-service (`notification_db`)

- DB: `notification_db` (Postgres, port 5435 theo `application.yml`).
- Service chỉ giữ thông báo. Không giữ user, board, thẻ chi tiết.
- ID liên service chỉ giữ dạng số (`UUID`/`Long`), không FK JPA chéo DB.

## Bảng `notifications`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `BIGINT` | PK, `IDENTITY` tự tăng |
| `user_id` | `UUID` | `NOT NULL`, chủ thông báo ở `user_db`, không FK |
| `type` | `VARCHAR(30)` | `NOT NULL`, `ASSIGNED` / `MENTIONED` / `MOVED` (kỳ 2 thêm `PR_REVIEWED`) |
| `title` | `VARCHAR(500)` | `NOT NULL` |
| `card_id` | `BIGINT` | nullable, số tham chiếu sang `task_db`, không FK |
| `board_id` | `BIGINT` | nullable, số tham chiếu sang `board_db`, không FK |
| `read` | `BOOLEAN` | `NOT NULL`, mặc định `false` |
| `created_at` | `TIMESTAMPTZ` | thời điểm tạo, sắp mới nhất trước |

Index: `(user_id, read)` cho query `unreadOnly=true` (xem `NotificationEntity`).

## Quan hệ

- Không quan hệ JPA ra ngoài. `user_id`/`card_id`/`board_id` là số thuần.
- Xóa user, board, thẻ không cascade ở đây. Kỳ 1 dọn bằng job hoặc giữ lịch sử.
- Tạo mới chỉ từ event task-service qua `POST /internal/v1/notifications`.
