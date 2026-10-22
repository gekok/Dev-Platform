# Issue 04 — [notification-service] Ruột Notify + Internal

Service: `notification-service`
Cổng: `8084`
Kho: `notification_db` (Postgres `5435`)
Hợp đồng: `docs/api/kanban-api.md` mục 4 (4.1 → 4.2)
Tài liệu riêng: `services/notification-service/docs/api.md`, `docs/database.md`

Người nhận: Người 4 (notify)
Nhãn: `service/notification-service`

## Files sở hữu

```
services/notification-service/src/main/java/dev/platform/notification/
├── domain/aggregate/NotificationAggregate.java
├── application/command/CreateNotificationCommand.java, MarkReadCommand.java
├── application/port/in/CreateNotificationPort.java, ListMyNotificationsPort.java, MarkReadPort.java
├── application/port/out/SaveNotificationPort.java
├── infrastructure/adapter/SaveNotificationAdapter.java
├── infrastructure/event/TaskEventListener.java
├── infrastructure/persistence/entity/NotificationEntity.java
└── api/NotificationController.java + api/internal/InternalNotificationController.java + dto
```

## Việc làm từ đầu đến cuối

- [ ] Viết `@Service` cho `CreateNotificationPort` (chỉ task-service gọi, check type hợp lệ)
- [ ] Viết `@Service` cho `ListMyNotificationsPort` (`unreadOnly=true` dùng index `user_id, read`)
- [ ] Viết `@Service` cho `MarkReadPort` (sai chủ → `403`, không thấy → `404`)
- [ ] Nối `TaskEventListener` với `POST /internal/v1/notifications`
- [ ] Nhận đủ 3 type kỳ 1: `ASSIGNED` / `MENTIONED` / `MOVED`
- [ ] Giữ chỗ `PR_REVIEWED` kỳ 2 mà không sửa API cũ
- [ ] Thay stub `X-User-Id` bằng `sub` từ JWT
- [ ] Cấm client tự tạo: không mở `POST /api/v1/notifications` cho React/Flutter

## Định nghĩa xong

- `mvn -pl services/notification-service compile` xanh
- `GET /api/v1/notifications/me?unreadOnly=true` → `200` đúng tin của `sub`
- `PATCH /api/v1/notifications/{id}/read` → `200`, sai chủ → `403`
- `POST /internal/v1/notifications` → `201`, type sai → `400`

## Bàn giao tích hợp

- Chốt service token nội bộ để Người 3 gọi sang
- Không đổi path `/api/v1/notifications/**`, `/internal/v1/notifications`
- Báo Người 5 khi thêm type mới kỳ 2
