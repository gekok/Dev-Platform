# user_db (user-service)

DB riêng của user-service: `user_db` (Postgres, port 8081).

## Bảng users

| Cột | Kiểu | Constraint |
|---|---|---|
| id | UUID | PK, `GenerationType.UUID` |
| email | varchar(255) | `NOT NULL`, `UNIQUE` |
| password_hash | varchar/text | `NOT NULL` |
| full_name | varchar(255) | nullable |

Index unique trên `email` để chặn trùng khi đăng ký (lỗi 409).

## Quy ước

- PK dạng UUID để gateway và service khác giữ tham chiếu mà không cần join.
- `password_hash` không bao giờ trả ra API (xem `UserResponse`).
- Không FK chéo DB: board/task/notification chỉ lưu `userId` dạng UUID.
