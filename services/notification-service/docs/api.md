# API — Notification (kanban-api mục 4)

Thuộc `notification-service` (`:8084`). Chỉ đọc và đánh dấu đã đọc.
Không cho client tự tạo thông báo.

Auth kỳ 1: stub đọc `X-User-Id` (UUID). TODO parse JWT `sub` ở gateway/service.

## 4.1 Lấy thông báo của tôi

`GET /api/v1/notifications/me?unreadOnly=true` → `200 OK`.

```json
[
  {
    "id": 500,
    "type": "ASSIGNED",
    "title": "Bạn được gán thẻ MSS-12",
    "cardId": 100,
    "boardId": 1,
    "read": false,
    "createdAt": "2026-09-29T10:30:00Z"
  }
]
```

- `unreadOnly=true` chỉ trả chưa đọc (query index `user_id, read`).
- `type` hợp lệ kỳ 1: `ASSIGNED` / `MENTIONED` / `MOVED`.
- Kỳ 2 thêm `PR_REVIEWED` mà không sửa API cũ.

## 4.2 Đánh dấu đã đọc

`PATCH /api/v1/notifications/{id}/read` → `200 OK`.

Body trả về là thông báo sau khi đổi `read` thành `true`.
Đọc nhầm thông báo người khác → `403 FORBIDDEN` (check chủ ở service kỳ 1).

## Internal (task-service gọi sang)

`POST /internal/v1/notifications` → `201 Created`.

Không qua gateway. Chỉ gọi trong cụm bằng service token (kỳ 1).

```json
{
  "userId": "c0a8b1e2-0000-4000-8000-000000000001",
  "type": "ASSIGNED",
  "title": "Bạn được gán thẻ MSS-12",
  "cardId": 100,
  "boardId": 1
}
```

- `type` sai → `400 BAD_REQUEST`.
- Gán mới bắn `ASSIGNED`, mention bắn `MENTIONED`, kéo thẻ bắn `MOVED`.
- Client (React/Flutter) không được gọi đường này.

## Lỗi

Format chuẩn `{code,message,timestamp,path}` (xem kanban-api mục 6):

- `400 BAD_REQUEST` — thiếu `X-User-Id`, `type` sai, thiếu field internal.
- `401` — thiếu/sai JWT (kỳ sau, hiện thiếu `X-User-Id`).
- `403 FORBIDDEN` — đánh dấu đọc nhầm thông báo người khác.
- `404 NOTIFICATION_NOT_FOUND` — không thấy thông báo.
