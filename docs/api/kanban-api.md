# Kanban API Kỳ 1 — trạng thái chuyển sang Project-first

**Trạng thái:** Hướng resource Project-first và các nghiệp vụ chính đã thống nhất. Đây chưa phải OpenAPI/schema cuối cùng: payload Invitation, một số mã lỗi, status operation, query cursor và event contract còn mở trong [architecture decisions](../project/architecture-decisions.md).

**Base URL mục tiêu qua Gateway:** `http://localhost:8080/api/v1`.

**Runtime hiện tại:** backend vẫn chủ yếu dùng Board-first API; Auth/Gateway cũng chưa được nối hoàn chỉnh (login port chưa có implementation, `/users/me` còn dùng `X-User-Id`). Phần Board-first ở cuối tài liệu được giữ làm tham chiếu cho code/tài liệu cũ, không phải contract để phát triển tính năng Project-first mới.

## 1. Route map mục tiêu Project-first

Các route dưới đây ghi lại phạm vi đã thống nhất và route đang được dùng làm tham chiếu trong React/MSW demo. Payload, response, lỗi và mức độ đã implement không được suy ra chỉ từ route map này.

| Nhóm | Method | Endpoint | Hành vi mục tiêu |
|---|---|---|---|
| Auth | `POST` | `/api/v1/auth/login` | Đăng nhập theo contract hiện có |
| User | `GET` | `/api/v1/users/me` | Lấy tài khoản hiện tại |
| Project | `GET`, `POST` | `/api/v1/projects` | Liệt kê và tạo Project |
| Project | `GET` | `/api/v1/projects/{projectId}` | Xem Project |
| Membership | `GET` | `/api/v1/projects/{projectId}/members` | Liệt kê Member chính thức |
| Invitation | `POST` | `/api/v1/projects/{projectId}/invitations` | Owner mời tài khoản đã tồn tại; trạng thái ban đầu `PENDING` |
| Invitation | `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Người được mời chấp nhận; lúc này mới tạo Membership |
| Membership | `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Owner gỡ Member hoặc Member tự rời Project; Owner duy nhất không thể tự gỡ |
| Board | `GET`, `POST` | `/api/v1/projects/{projectId}/boards` | Liệt kê và tạo Board trong Project |
| Board | `GET` | `/api/v1/boards/{boardId}` | Xem Board và Columns |
| Column | `POST` | `/api/v1/boards/{boardId}/columns` | Tạo Column |
| Column | `PATCH` | `/api/v1/boards/{boardId}/columns/order` | Sắp xếp lại Columns |
| Card | `GET`, `POST` | `/api/v1/boards/{boardId}/cards` | Liệt kê và tạo Card |
| Card | `GET` | `/api/v1/cards/{cardId}` | Xem Card |
| Card | `PATCH` | `/api/v1/cards/{cardId}/move` | Chuyển Card |
| Card | `PATCH` | `/api/v1/cards/{cardId}/assignee` | Gán người xử lý |
| Comment | `GET`, `POST` | `/api/v1/cards/{cardId}/comments` | Xem và tạo Comment |
| Notification | `GET` | `/api/v1/notifications/me` | Liệt kê Notification của tài khoản hiện tại |
| Notification | `PATCH` | `/api/v1/notifications/{notificationId}/read` | Đánh dấu đã đọc |
| Board preference | `GET` | `/api/v1/users/me/preferences/default-boards` | Route tham khảo từ demo; ownership/storage/access validation backend chưa chốt |
| Board preference | `PUT`, `DELETE` | `/api/v1/users/me/preferences/default-boards/{projectId}` | Route tham khảo từ demo; service sở hữu và behavior khi Project/Board bị xóa chưa chốt |

### Quy tắc đã thống nhất và phần còn mở

- Project là ranh giới membership; Board thuộc một Project. `Project` và `Board` dùng ID số; User dùng UUID.
- Owner và Member có quyền thao tác công việc như nhau. Owner có thêm quyền tạo Board, quản lý trạng thái Project/Board và membership.
- Kỳ 1 chỉ thêm tài khoản đã có trong hệ thống bằng Invitation; người nhận chấp nhận mới trở thành Member. Mời người ngoài hệ thống và đổi vai trò để Kỳ 2.
- Project và Board dùng `ACTIVE`, `COMPLETED`, `CANCELLED`; transition cụ thể và route cập nhật trạng thái chưa chốt.
- API list Card dùng cursor: mặc định 5, tối đa 20, `position DESC, id DESC`, cursor opaque gồm `position` và `id`. Tên query parameter, response envelope và lỗi cursor chưa chốt.
- Gateway là ranh giới xác thực mục tiêu. Internal REST từ `task-service` sang `board-service` để kiểm tra quyền chưa triển khai cho tới khi bảo mật giao tiếp nội bộ được giải quyết.
- Route/payload Admin tạo Account, Admin xóa Account và status operation chưa được đặc tả ở đây. Mã lỗi Membership, payload Invitation, API đọc/từ chối/hủy Invitation, phạm vi Notification cascade, retry/DLQ và service/storage/access validation của Board preference cũng còn mở.

Các route preference và nhiều route Kanban bắt nguồn từ MSW demo; Invitation/Accept và quyền Project-first là quyết định backend sau đó, không phải endpoint đã có trong demo. Xem [chỉ mục tài liệu](../README.md) và [architecture decisions](../project/architecture-decisions.md) để biết thứ tự ưu tiên.

## 2. Hợp đồng Board-first cũ — chỉ tham khảo runtime

Phần dưới mô tả API Board-first được viết trước quyết định Project-first. Một số controller đang dùng các route này, nhưng nội dung cũ không đồng nghĩa mọi endpoint đã hoạt động hoàn chỉnh. Không dùng phần này làm thiết kế đích cho API mới.

Base URL cũ qua Gateway:

```text
http://localhost:8080/api/v1
```

Contract cũ mô tả JWT `sub` làm danh tính và database riêng cho từng service. Thực tế một số controller hiện còn đọc `X-User-Id`; xem ghi chú runtime ở [README](../../README.md).

| Service | Port | DB |
|---|---|---|
| gateway | 8080 | — |
| user-service | 8081 | user_db |
| board-service | 8082 | board_db |
| task-service | 8083 | task_db |
| notification-service | 8084 | notification_db |

### Auth / User (contract cũ)

Thuộc `user-service`. Cấp vé JWT cho các service khác tin.

#### Đăng ký

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

#### Đăng nhập

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

#### Lấy hồ sơ hiện tại

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/users/me` |

Header: `Authorization: Bearer <jwt>`.

Response `200 OK`: trả về `id`, `email`, `fullName` của `sub` trong token.

### Board

Thuộc `board-service`. Giữ bảng và cột. Không giữ thẻ chi tiết.

#### Tạo bảng

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

#### Lấy danh sách bảng của tôi

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards` |

Response `200 OK`: mảng bảng mà `sub` là owner hoặc member.

#### Lấy chi tiết bảng kèm cột

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards/{boardId}` |

Response `200 OK`: gồm thông tin Board và Columns như response của
`POST /api/v1/boards`; không trả Card ở đây. Card lấy ở Task API để Board
không phình to.

#### Thêm cột

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

#### Đổi thứ tự cột

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/boards/{boardId}/columns/order` |

Request:

```json
{ "orderedColumnIds": [10, 13, 11, 12] }
```

Response `200 OK`: danh sách cột sau khi xếp.

### Card (Task)

Thuộc `task-service`. Giữ thẻ, người được gán, bình luận.
`boardId` và `columnId` chỉ là số tham chiếu sang board-service.
`assigneeId` chỉ là UUID tham chiếu sang user-service.

#### Tạo thẻ

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

#### Lấy thẻ theo bảng

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards/{boardId}/cards?columnId=10` |

`columnId` là query tùy chọn để lọc. Response `200 OK`: mảng thẻ
sắp theo `position`.

#### Kéo thẻ qua cột (move)

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

#### Gán người làm

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

#### Bình luận

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

### Notification

Thuộc `notification-service`. Chỉ đọc và đánh dấu đã đọc.
Không cho client tự tạo thông báo.

#### Lấy thông báo của tôi

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

#### Đánh dấu đã đọc

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/notifications/{id}/read` |

Response `200 OK`: thông báo sau khi đổi `read` thành `true`.

### Real-time

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

### Quy ước lỗi cũ

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

### Đề xuất Kỳ 2 cũ — không còn là quyết định hiện hành

Các gạch đầu dòng sau được giữ để tra cứu lịch sử; không dùng làm kiến trúc Kỳ 2 hiện hành. Phạm vi service/API Kỳ 2 được hoãn tới khi có thiết kế riêng trong [architecture decisions](../project/architecture-decisions.md).

- `Board` thành nền cho `Issues` (mỗi issue là một card đặc biệt).
- `Task` thành nền cho `Branch/Commit/PR` (mỗi PR gắn một `cardId`).
- `Notification` giữ nguyên, thêm `type` mới như `PR_REVIEWED`.
- Gateway thêm route `/api/v1/repos/**`, `/api/v1/workflows/**`.
- Giữ tiền tố `/api/v1` để mobile cũ còn chạy khi API lên `v2`.
