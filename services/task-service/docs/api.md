# task-service API — runtime Board-first và mục tiêu Project-first

**Vai trò:** sở hữu Card, Comment và realtime event. Contract mục tiêu ở [Kanban API](../../../docs/api/kanban-api.md); quyết định phân quyền và service ownership ở [architecture decisions](../../../docs/project/architecture-decisions.md).

## Controller hiện có

`CardController` hiện khai báo:

| Method | Route hiện tại | Ghi chú |
|---|---|---|
| POST | `/api/v1/boards/{boardId}/cards` | Tạo Card |
| GET | `/api/v1/boards/{boardId}/cards` | List, `columnId` tùy chọn |
| PATCH | `/api/v1/cards/{cardId}/move` | Chuyển Column/position |
| PATCH | `/api/v1/cards/{cardId}/assignee` | Gán User |
| POST | `/api/v1/cards/{cardId}/comments` | Tạo Comment |

WebSocket config khai báo `/ws/boards/{boardId}`. Route này hiện vẫn dựa trên Board-first API. Một số endpoint đọc `X-User-Id` không bắt buộc; Card authorization Project-based chưa được triển khai.

## Thiết kế mục tiêu đã thống nhất

- Mọi Card/Comment được truy cập trong Project membership boundary thông qua Board thuộc Project.
- Owner và Member có quyền tạo Card, chuyển trạng thái và comment. Khi Account bị gỡ khỏi Project, tự rời hoặc bị xóa, các Card được assign cho họ phải được unassign.
- Giữ Comment và attribution lịch sử; UI phân biệt Account đã bị xóa với Account còn tồn tại nhưng không còn trong Project.
- List Card dùng cursor, mặc định 5, tối đa 20, thứ tự `position DESC, id DESC`; cursor opaque mang position và id. Query parameter/response envelope/error còn mở.
- Kiểm tra quyền đồng bộ qua board-service chưa làm cho tới khi giao tiếp nội bộ có bảo mật. Không dùng header identity từ client làm bằng chứng quyền.

## Scaffold cascade hiện có

Có entity/job/checkpoint, query theo batch cho Card/Comment, bảng write-lock và `TaskWriteGuard`. `TaskDeletionHandler` mới là interface. Chưa có Rabbit listener, worker, Inbox/job transaction, Outbox completion publisher; guard chưa được nối vào create/move/assign/comment. Vì vậy cascade/unassign chưa chạy.

Chi tiết: [hướng dẫn skeleton](../../../docs/project/account-deletion-skeleton-guide.md). API, batch ordering và error contract phải theo quyết định chung; không thêm payload riêng ở đây.

## Chi tiết contract được khai báo trong source

| Method | Path | Header/query | Request | Success |
|---|---|---|---|---|
| `POST` | `/api/v1/boards/{boardId}/cards` | `X-User-Id` optional | `columnId`, `title`, `description`, `assigneeId`, `dueDate` | `201`, `CardResponse` |
| `GET` | `/api/v1/boards/{boardId}/cards` | `columnId` optional | Không có body | `200`, `CardResponse[]` |
| `PATCH` | `/api/v1/cards/{cardId}/move` | Không có identity header | `toColumnId`, `toPosition` | `200`, `CardResponse` |
| `PATCH` | `/api/v1/cards/{cardId}/assignee` | `X-User-Id` optional | `assigneeId` nullable UUID | `200`, `CardResponse` |
| `POST` | `/api/v1/cards/{cardId}/comments` | `X-User-Id` optional | `content`, `mentionedUserIds` | `201`, `CommentResponse` |

`boardId` và `cardId` là path parameter kiểu `Long`; query `columnId` trên GET danh sách cũng là `Long` và tùy chọn.

Ví dụ tạo Card:

```json
{
  "columnId": 12,
  "title": "Prepare release",
  "description": "Draft notes",
  "assigneeId": "550e8400-e29b-41d4-a716-446655440000",
  "dueDate": "2026-11-15T10:00:00Z"
}
```

| Request field | Kiểu JSON | Bắt buộc/validation và hành vi trong source |
|---|---|---|
| `columnId` | integer/Long | Không có Bean Validation; `CardAggregate.create` yêu cầu khác null. |
| `title` | string | Không có Bean Validation; domain từ chối null/rỗng/toàn khoảng trắng và trim khi tạo. |
| `description` | string hoặc null | Tùy chọn; không có validation độ dài ở DTO/domain hiện tại. |
| `assigneeId` | UUID string hoặc null | Tùy chọn; API chưa kiểm tra user tồn tại hoặc membership Project. |
| `dueDate` | ISO-8601 offset date-time hoặc null | Tùy chọn; biểu diễn bằng `OffsetDateTime`. |

Không có `position` trong request. Cách chọn position do use case tạo Card quyết định; inbound port hiện chưa có implementation nên chưa thể xác nhận hành vi runtime.

`CardResponse` hiện gồm `id`, `boardId`, `columnId`, `title`, `status`, `position`, `assigneeId`; response chưa trả `description` hoặc `dueDate` dù request/entity có các field này. `status` hiện là `TODO`, `IN_PROGRESS`, `DONE`. `CommentResponse` gồm `id`, `cardId`, `authorId`, `content`, `createdAt`.

Ví dụ response Card và Comment:

```json
{"id":42,"boardId":7,"columnId":12,"title":"Prepare release","status":"TODO","position":0,"assigneeId":null}
```

```json
{"id":900,"cardId":42,"authorId":"550e8400-e29b-41d4-a716-446655440000","content":"Ready for review","createdAt":"2026-10-28T09:00:00Z"}
```

Các request DTO không gắn Jakarta Bean Validation. `CardAggregate` chỉ có validation hiện hành cho create/move; chưa có validation nghiệp vụ cho comment. Do `AddCommentPort` chưa có implementation, không coi ràng buộc `card_comments.content NOT NULL` là validation REST đã được nối.

| Endpoint | Request fields và hành vi hiện có |
|---|---|
| `PATCH /api/v1/cards/{cardId}/move` | `toColumnId` (`Long`, domain yêu cầu khác null); `toPosition` (`integer`, domain yêu cầu không âm). |
| `PATCH /api/v1/cards/{cardId}/assignee` | `assigneeId` (UUID hoặc null; null biểu thị bỏ gán). Chưa kiểm tra Account tồn tại/membership. |
| `POST /api/v1/cards/{cardId}/comments` | `content` (string) và `mentionedUserIds` (array UUID, có thể vắng/null); DTO/aggregate không đặt validation cho các field này. Mention IDs chưa có xử lý/API response. |

`CardResponse` có `id`, `boardId`, `columnId` (`Long`), `title`/`status` (string), `position` (integer), `assigneeId` (UUID hoặc null); `CommentResponse` có `id`, `cardId` (`Long`), `authorId` (UUID hoặc null tại DTO edge hiện tại), `content` (string), `createdAt` (ISO-8601 offset date-time). Controller nhận `X-User-Id` tùy chọn và `CommentValue` không ép `authorId` khác null; persistence column `card_comments.author_id` lại not-null. Use case chưa được nối nên phải chốt/enforce author khi implement. `mentionedUserIds` không có trong response. List trả toàn bộ kết quả theo Board hoặc Column, repository sort `position ASC`; chưa nhận `limit`/`cursor`, chưa phải cursor API mục tiêu `position DESC, id DESC`.

## Identity, errors, realtime và readiness

Global handler ánh xạ `IllegalArgumentException` thành `400` với `{ "code": "BAD_REQUEST", "message": "...", "timestamp": "...", "path": "..." }`. Handler chưa định nghĩa 403/404, validation-body chuẩn hoặc lỗi access denial.

`X-User-Id` không bắt buộc ở create/assign/comment và các endpoint move/list không nhận actor; hiện không có Project-membership authorization tại API boundary. Đây là stub, không phải contract bảo mật.

Controller cần `CreateCardPort`, `GetCardsPort`, `MoveCardPort`, `AssignCardPort`, `AddCommentPort`; source hiện có repository adapter nhưng không có bean triển khai các inbound port. Các mapping không chứng minh endpoint đã được nối và khởi chạy được.

WebSocket được khai báo ở `BoardWebSocketConfig`, nhưng `registerWebSocketHandlers` chưa đăng ký handler. `CardEventAdapter` mới log `card.created`, `card.moved`, `comment.added`; chưa broadcast payload. Path `/ws/boards/{boardId}` trong task sheet là mục tiêu, chưa phải endpoint đang phục vụ.

## API mục tiêu thuộc task-service

Task-service sở hữu Card/Comment; mọi request phải đi qua Gateway và quyền bắt nguồn từ Membership Project qua Board. Đây là API mục tiêu, chưa được ánh xạ vào controller Board-first:

| Method | Route | Request/Query | Success | Ghi chú |
|---|---|---|---|---|
| `GET` | `/api/v1/boards/{boardId}/cards?columnId={id}&limit=5&cursor={opaque}` | Per-Column pagination proposal: `columnId` bắt buộc; limit mặc định 5, max 20 | `200 CardPage` | `position DESC, id DESC`; cursor bind với Board + Column; envelope còn đề xuất |
| `POST` | `/api/v1/boards/{boardId}/cards` | `columnId,title,description?,assigneeId?,dueDate?` | `201 Card` | Column thuộc Board; assignee là Project Member |
| `GET` | `/api/v1/cards/{cardId}` | Path ID | `200 CardDetail` | Member/Owner |
| `PATCH` | `/api/v1/cards/{cardId}/move` | `{toColumnId,toPosition}` | `200 Card` | Column cùng Board; Member/Owner |
| `PATCH` | `/api/v1/cards/{cardId}/assignee` | `{assigneeId: UUID|null}` | `200 Card` | `null` bỏ gán; assignee thuộc Project |
| `GET`, `POST` | `/api/v1/cards/{cardId}/comments` | Create `{content,mentionedUserIds?}` | `200 Comment[]` / `201 Comment` | Mention phải thuộc cùng Project |

Thứ tự cursor `(position DESC,id DESC)`, default 5, max 20 đã chốt. `id` unique tạo tie-breaker toàn phần kể cả khi position trùng giữa các Column. Vì UI cuộn riêng từng Column, wire proposal yêu cầu `columnId` và cursor phải chỉ dùng lại trong cùng `boardId`, `columnId` và sort; cursor sai scope trả `400 INVALID_CURSOR` (đề xuất). Query names, page envelope `{items,nextCursor,hasMore}`, lỗi và consistency khi dữ liệu bị move vẫn cần duyệt. `columnId` là API scope đề xuất, không phải điều kiện toán học để cursor xác định thứ tự. Client dedupe theo `cardId`, dùng realtime nếu có và hiện banner yêu cầu reload khi phát hiện bất nhất. Không có snapshot isolation giữa các trang.

Shape Card tối thiểu đề xuất: `{id, boardId, columnId, title, position, assigneeId}`; detail thêm `description`, `dueDate`, timestamps nếu được duyệt. Card create nhận `columnId`, `title`, `description?`, `assigneeId?`, `dueDate?`; move nhận `{toColumnId,toPosition}`; assign nhận `{assigneeId: UUID|null}`. Comment create nhận `{content,mentionedUserIds?}`; Comment response hiện chưa thống nhất có trả `mentionedUserIds` hay profile embed. `Card.status` cần chốt bỏ hay derive từ Column vì demo hiện giữ `TODO/IN_PROGRESS/DONE` riêng và không đổi theo move.

Error mapping đề xuất: `400 INVALID_CURSOR`/validation; `401 UNAUTHENTICATED`; `403 PROJECT_ACCESS_DENIED`; `404 BOARD_NOT_FOUND`/`CARD_NOT_FOUND`/`COLUMN_NOT_FOUND`; `409 PROJECT_NOT_ACTIVE`, `COLUMN_NOT_IN_BOARD`, `ASSIGNEE_NOT_PROJECT_MEMBER`. Mã/status cụ thể chưa duyệt. Gateway identity/context là nguồn actor; không tin `X-User-Id` do client tự gửi. Cần chốt lớp nào đọc Redis authorization snapshot để chặn request Card sau khi Membership revoke, vì Internal REST giữa Task và Board đang hoãn.

Khi Member bị gỡ/tự rời hoặc Account bị xóa, board-service thu hồi quyền hiệu lực trước rồi phát cleanup event; Task unassign chạy nền, idempotent và giữ Comment/attribution. Không dùng worker cleanup làm điều kiện authorization. Nếu cleanup cũ còn pending, API phải ngăn lần tham gia/assignment mới hoặc phân biệt membership generation để event cũ không xóa assignment mới; policy còn mở. Profile author được compose bằng User bulk profile + Membership; response embed hay client composition chưa chốt. WebSocket protocol cũng còn mở. Xem [contract chung](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế).
