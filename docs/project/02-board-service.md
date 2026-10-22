# Issue 02 — [board-service] Ruột Board + Column

Service: `board-service`
Cổng: `8082`
Kho: `board_db` (Postgres `5433`)
Hợp đồng: `docs/api/kanban-api.md` mục 2 (2.1 → 2.5)
Tài liệu riêng: `services/board-service/docs/api.md`, `docs/database.md`

Người nhận: Người 2 (board)
Nhãn: `service/board-service`

## Files sở hữu

```
services/board-service/src/main/java/dev/platform/board/
├── domain/aggregate/BoardAggregate.java
├── application/command/CreateBoardCommand.java, AddColumnCommand.java, ReorderColumnsCommand.java
├── application/port/in/CreateBoardPort.java, AddColumnPort.java, ReorderColumnsPort.java, GetBoardPort.java
├── application/port/out/SaveBoardPort.java
├── infrastructure/adapter/SaveBoardAdapter.java
├── infrastructure/persistence/entity/BoardEntity.java, BoardColumnEntity.java
└── api/BoardController.java + dto + exception
```

## Việc làm từ đầu đến cuối

- [ ] Viết `@Service` cho `CreateBoardPort` (key `[A-Z0-9]{2,20}`, thiếu columns → tạo 3 mặc định)
- [ ] Viết `@Service` cho `GetBoardPort` (`getById` + list theo owner, chưa có 403 thì ghi TODO)
- [ ] Viết `@Service` cho `AddColumnPort` (position nối đuôi)
- [ ] Viết `@Service` cho `ReorderColumnsPort` (check đủ id + đúng member + thứ tự mới)
- [ ] Thay stub `X-User-Id` bằng `sub` từ JWT (giống Người 1 chốt)
- [ ] Trùng `board_key` → `409 BOARD_KEY_CONFLICT`
- [ ] Không trả thẻ ở `GET /{boardId}` (thẻ thuộc task-service)

## Định nghĩa xong

- `mvn -pl services/board-service compile` xanh
- `POST /api/v1/boards` → `201` đúng `BoardResponse`
- `GET /api/v1/boards` → `200` đúng bảng của `sub`
- `POST /{boardId}/columns` → `201`
- `PATCH /{boardId}/columns/order` → `200` đúng thứ tự mới

## Bàn giao tích hợp

- Chốt `boardId` + `columnId` số để Người 3 dùng
- Không đổi path `/api/v1/boards/**`
- Báo Người 5 khi đổi field `BoardResponse`
