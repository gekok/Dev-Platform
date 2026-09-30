# task_db (task-service)

DB riêng của task-service: `task_db` (Postgres, port 8083).

## Bảng cards

| Cột | Kiểu | Constraint |
|---|---|---|
| id | bigint | PK, `IDENTITY` |
| board_id | bigint | `NOT NULL`, số tham chiếu sang board-service |
| column_id | bigint | `NOT NULL`, số tham chiếu sang board-service |
| title | varchar(500) | `NOT NULL` |
| description | TEXT | nullable |
| status | varchar(30) | `TODO` / `IN_PROGRESS` / `DONE` |
| position | int | `NOT NULL`, thứ tự thẻ trong cột từ 0 |
| assignee_id | uuid | nullable, tham chiếu sang user-service |
| due_date | timestamptz | nullable |

Index `(board_id, column_id, position)` để lấy thẻ theo cột sắp sẵn.

## Bảng card_comments

| Cột | Kiểu | Constraint |
|---|---|---|
| id | bigint | PK, `IDENTITY` |
| card_id | bigint | `NOT NULL`, FK → `cards(id)` cascade xóa |
| author_id | uuid | `NOT NULL`, tham chiếu sang user-service |
| content | TEXT | `NOT NULL` |
| created_at | timestamptz | thời điểm tạo |

## Quy ước

- Không FK chéo DB: `boardId`/`columnId` là số, `assigneeId` là UUID.
- Move thẻ dồn `position` cột cũ + cột mới trong 1 transaction (kỳ 1).
- Kỳ 1 nạp comments khi đọc card chi tiết, list theo bảng chưa nạp.
