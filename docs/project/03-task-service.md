# Issue 03 — [task-service] Ruột Card + Move + WS

Service: `task-service`
Cổng: `8083`
Kho: `task_db` (Postgres `5434`)
Hợp đồng: `docs/api/kanban-api.md` mục 3 (3.1 → 3.5) + mục 5 WS
Tài liệu riêng: `services/task-service/docs/api.md`, `docs/database.md`

Người nhận: Người 3 (task, nặng nhất)
Nhãn: `service/task-service`

## Files sở hữu

```
services/task-service/src/main/java/dev/platform/task/
├── domain/aggregate/CardAggregate.java
├── application/command/CreateCardCommand.java, MoveCardCommand.java, AssignCardCommand.java, AddCommentCommand.java
├── application/port/in/CreateCardPort.java, MoveCardPort.java, AssignCardPort.java, AddCommentPort.java, GetCardsPort.java
├── application/port/out/SaveCardPort.java, CardEventPublisherPort.java
├── infrastructure/adapter/SaveCardAdapter.java
├── infrastructure/persistence/entity/CardEntity.java, CardCommentEntity.java
├── infrastructure/websocket/BoardWebSocketConfig.java, CardEventAdapter.java
└── api/CardController.java + dto + exception
```

## Việc làm từ đầu đến cuối

- [ ] Viết `@Service` cho `CreateCardPort` (title non-blank, position nối đuôi trong cột)
- [ ] Viết `@Service` cho `GetCardsPort` (lọc `columnId`, sort `position`)
- [ ] Viết `@Service` cho `MoveCardPort` (dồn position cột cũ + mới trong 1 transaction)
- [ ] Viết `@Service` cho `AssignCardPort` (gán mới → bắn event, không báo chính mình)
- [ ] Viết `@Service` cho `AddCommentPort` (mention → bắn event từng `mentionedUserIds`)
- [ ] Hiện thực `CardEventPublisherPort` (log trước, WS sau)
- [ ] Bắn WS `card.moved` / `card.created` / `comment.added` ở `WS /ws/boards/{boardId}`
- [ ] Gọi `POST /internal/v1/notifications` sang notify-service khi gán + mention + move
- [ ] Thay stub `X-User-Id` bằng `sub` từ JWT

## Định nghĩa xong

- `mvn -pl services/task-service compile` xanh
- `POST /api/v1/boards/{boardId}/cards` → `201`
- `PATCH /api/v1/cards/{id}/move` → `200` + position 2 cột đúng
- `POST /cards/{id}/comments` → `201`
- WS nhận đủ 3 event theo mục 5

## Bàn giao tích hợp

- Chốt `cardId` số + event payload để Người 4 + Người 5 dùng
- Không đổi path `/api/v1/cards/**`, `/api/v1/boards/**/cards/**`, `/ws/**`
- Báo Người 2 khi cần check `columnId` thuộc bảng nào
