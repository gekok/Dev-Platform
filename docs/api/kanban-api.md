## Kanban API (Phase 1)

Base URL (qua gateway):

```text
http://localhost:8080/api/v1
```

Mọi API xác thực dùng `userId` lấy từ JWT `sub`. Client (React/Flutter)
không được tự truyền hoặc sửa `userId` trong body.

Ngôn ngữ chung (ubiquitous language): `user`, `board`, `column`,
`card` (còn gọi là task), `comment`, `notification`.

Mỗi service giữ kho riêng (database per service). ID liên service chỉ giữ
dạng số (`Long`/`UUID`), không tạo FK JPA chéo database.

| Service | Port | DB |
|---|---|---|
| gateway | 8080 | — |
| user-service | 8081 | user_db |
| board-service | 8082 | board_db |
| task-service | 8083 | task_db |
| notification-service | 8084 | notification_db |

## 1. Auth / User

Thuộc `user-service`. Cấp vé JWT cho các service khác tin.

### 1.1 Đăng ký

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/auth/register` |

Request:

```json
{
  "email": "sv@fpt.edu.vn",
  "password": "123456",
  "fullName": "Nguyen Van A"
}
```

Response `201 Created`:

```json
{
  "id": "c0a8b1e2-0000-4000-8000-000000000001",
  "email": "sv@fpt.edu.vn",
  "fullName": "Nguyen Van A"
}
```

### 1.2 Đăng nhập

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/auth/login` |

Request:

```json
{
  "email": "sv@fpt.edu.vn",
  "password": "123456"
}
```

Response `200 OK`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer"
}
```

### 1.3 Lấy hồ sơ hiện tại

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/users/me` |

Header: `Authorization: Bearer <jwt>`.

Response `200 OK`: trả về `id`, `email`, `fullName` của `sub` trong token.

## 2. Board

Thuộc `board-service`. Giữ bảng và cột. Không giữ thẻ chi tiết.

### 2.1 Tạo bảng

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/boards` |

Request:

```json
{
  "name": "MSS301 Project",
  "key": "MSS",
  "columns": ["To Do", "In Progress", "Done"]
}
```

Nếu không gửi `columns`, server tự tạo 3 cột mặc định trên.

Response `201 Created`:

```json
{
  "id": 1,
  "name": "MSS301 Project",
  "key": "MSS",
  "ownerId": "c0a8b1e2-0000-4000-8000-000000000001",
  "columns": [
    { "id": 10, "name": "To Do", "position": 0 },
    { "id": 11, "name": "In Progress", "position": 1 },
    { "id": 12, "name": "Done", "position": 2 }
  ]
}
```

### 2.2 Lấy danh sách bảng của tôi

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards` |

Response `200 OK`: mảng bảng mà `sub` là owner hoặc member.

### 2.3 Lấy chi tiết bảng kèm cột

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards/{boardId}` |

Response `200 OK`: như 2.1. Không trả thẻ ở đây; thẻ lấy ở Task API
để Board không phình to.

### 2.4 Thêm cột

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/boards/{boardId}/columns` |

Request:

```json
{ "name": "Review" }
```

Response `201 Created`:

```json
{ "id": 13, "name": "Review", "position": 3 }
```

### 2.5 Đổi thứ tự cột

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/boards/{boardId}/columns/order` |

Request:

```json
{ "orderedColumnIds": [10, 13, 11, 12] }
```

Response `200 OK`: danh sách cột sau khi xếp.

## 3. Card (Task)

Thuộc `task-service`. Giữ thẻ, người được gán, bình luận.
`boardId` và `columnId` chỉ là số tham chiếu sang board-service.
`assigneeId` chỉ là UUID tham chiếu sang user-service.

### 3.1 Tạo thẻ

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/boards/{boardId}/cards` |

Request:

```json
{
  "columnId": 10,
  "title": "Dựng skeleton Dev-Platform",
  "description": "pom + gateway + api.md",
  "assigneeId": "c0a8b1e2-0000-4000-8000-000000000001",
  "dueDate": "2026-10-15T17:00:00Z"
}
```

Response `201 Created`:

```json
{
  "id": 100,
  "boardId": 1,
  "columnId": 10,
  "title": "Dựng skeleton Dev-Platform",
  "status": "TODO",
  "position": 0,
  "assigneeId": "c0a8b1e2-0000-4000-8000-000000000001"
}
```

`position` là thứ tự thẻ trong cột, bắt đầu từ 0.

### 3.2 Lấy thẻ theo bảng

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards/{boardId}/cards?columnId=10` |

`columnId` là query tùy chọn để lọc. Response `200 OK`: mảng thẻ
sắp theo `position`.

### 3.3 Kéo thẻ qua cột (move)

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/cards/{cardId}/move` |

Request:

```json
{
  "toColumnId": 11,
  "toPosition": 0
}
```

Response `200 OK`: thẻ sau khi chuyển, kèm `columnId` và `position` mới.

Server phải dồn `position` của các thẻ còn lại trong cột cũ và cột mới
trong cùng một transaction. Client lạc quan (optimistic) vẽ trước, server
là chuẩn cuối cùng.

### 3.4 Gán người làm

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/cards/{cardId}/assignee` |

Request:

```json
{
  "assigneeId": "c0a8b1e2-0000-4000-8000-000000000001"
}
```

Khi gán mới, task-service bắn event cho notification-service để báo
người được gán. Không báo chính người thao tác.

### 3.5 Bình luận

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/cards/{cardId}/comments` |

Request:

```json
{
  "content": "@an review giúp em đoạn move",
  "mentionedUserIds": ["c0a8b1e2-0000-4000-8000-000000000002"]
}
```

Response `201 Created`: bình luận vừa tạo. Người trong
`mentionedUserIds` nhận thông báo mention.

## 4. Notification

Thuộc `notification-service`. Chỉ đọc và đánh dấu đã đọc.
Không cho client tự tạo thông báo.

### 4.1 Lấy thông báo của tôi

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/notifications/me?unreadOnly=true` |

Response `200 OK`:

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

Các `type` hợp lệ: `ASSIGNED`, `MENTIONED`, `MOVED`.

### 4.2 Đánh dấu đã đọc

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/notifications/{id}/read` |

Response `200 OK`: thông báo sau khi đổi `read` thành `true`.

## 5. Real-time

Web React giữ một kênh sống (WebSocket) theo bảng:

```text
WS /ws/boards/{boardId}
```

Header: `Authorization: Bearer <jwt>`.

Event server bắn xuống:

```json
{ "event": "card.moved", "cardId": 100, "toColumnId": 11, "toPosition": 0 }
```

```json
{ "event": "card.created", "cardId": 101, "columnId": 10 }
```

```json
{ "event": "comment.added", "cardId": 100, "commentId": 900 }
```

Mobile Flutter không giữ socket suốt. Nhận việc gán và mention qua đẩy
(push FCM). Bấm vào tin mới mở app và gọi API lấy chi tiết.

Khi mất mạng rồi nối lại, client phải gọi lại `GET` bảng và thẻ.
Server là chuẩn cuối cùng, client chỉ vẽ lại.

## 6. Quy ước lỗi

Format thống nhất:

```json
{
  "code": "CARD_NOT_FOUND",
  "message": "Card does not belong to this board",
  "timestamp": "2026-09-29T10:30:00Z",
  "path": "/api/v1/cards/100/move"
}
```

| Status | Trường hợp |
|---|---|
| `200 OK` | Lấy hoặc cập nhật thành công |
| `201 Created` | Tạo bảng, thẻ, bình luận thành công |
| `204 No Content` | Xóa thành công |
| `400 Bad Request` | Thiếu field, `position` âm, cột không thuộc bảng |
| `401 Unauthorized` | Chưa xác thực |
| `403 Forbidden` | Không phải member của bảng |
| `404 Not Found` | Không thấy bảng, thẻ, thông báo |
| `409 Conflict` | Trùng key bảng, trùng email đăng ký |

## 7. Ghi chú cho kỳ 2 (Dev Platform)

Kỳ 1 chỉ làm Kanban trên. Kỳ 2 tái dùng trực tiếp:

- `Board` thành nền cho `Issues` (mỗi issue là một card đặc biệt).
- `Task` thành nền cho `Branch/Commit/PR` (mỗi PR gắn một `cardId`).
- `Notification` giữ nguyên, thêm `type` mới như `PR_REVIEWED`.
- Gateway thêm route `/api/v1/repos/**`, `/api/v1/workflows/**`.
- Giữ tiền tố `/api/v1` để mobile cũ còn chạy khi API lên `v2`.
