# notification-service API — runtime hiện có và mục tiêu cascade

**Vai trò:** sở hữu Notification trong `notification_db`. Quyết định mục tiêu: [architecture decisions](../../../docs/project/architecture-decisions.md); route client mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

## API client hiện có

`NotificationController` khai báo:

| Method | Route hiện tại | Hành vi |
|---|---|---|
| GET | `/api/v1/notifications/me` | Danh sách Notification của user, có `unreadOnly` |
| PATCH | `/api/v1/notifications/{id}/read` | Đánh dấu đã đọc |

Identity hiện lấy từ `X-User-Id`; chưa có JWT parsing/authorization hoàn chỉnh trong controller.

## API nội bộ hiện có

`InternalNotificationController` khai báo `POST /internal/v1/notifications` để tạo notification. Route không thuộc Gateway. Controller không có cơ chế kiểm tra service token; comment mô tả token không phải bằng chứng endpoint đã được bảo vệ. Chưa đưa API này thành hợp đồng giao tiếp service an toàn.

`TaskEventListener` hiện là placeholder, chưa nhận event và chưa gọi `CreateNotificationPort`. Không khẳng định luồng gán/mention/move đã tạo Notification end-to-end.

## Mục tiêu và điểm còn mở

- Client chỉ list/mark-read Notification của chính Account.
- Cascade Project sẽ yêu cầu notification-service xóa dữ liệu phụ thuộc trong phạm vi Project sau khi phạm vi dữ liệu được chốt.
- Skeleton hiện truy vấn notification theo `boardIds`; notification không gắn Board và cách xử lý toàn bộ notification theo Account còn mở.
- Chưa có queue listener, Inbox/job transaction, batch worker hoặc completion publisher.
- `PR_REVIEWED` thuộc mô tả Kỳ 2 cũ; chưa phải contract Kỳ 2 đã thống nhất.

Xem [skeleton cascade](../../../docs/project/account-deletion-skeleton-guide.md) và mục còn mở trong architecture decisions.

## Chi tiết contract được khai báo trong source

| Method | Path | Header/query | Request | Success |
|---|---|---|---|---|
| `GET` | `/api/v1/notifications/me` | `unreadOnly` mặc định `false`; `X-User-Id` UUID thực tế bắt buộc qua controller check | Không có body | `200`, `NotificationResponse[]` |
| `PATCH` | `/api/v1/notifications/{id}/read` | `id: Long`, `X-User-Id` UUID thực tế bắt buộc | Không có body | `200`, `NotificationResponse` |
| `POST` | `/internal/v1/notifications` | Không có identity/service token check | `userId`, `type`, `title`, `cardId?`, `boardId?` | `201`, `NotificationResponse` |

`unreadOnly` của GET là boolean query parameter, mặc định `false`; đường dẫn `{id}` và `cardId`/`boardId` là `Long`. `X-User-Id` được parse thành UUID; header thiếu bị controller ném `IllegalArgumentException`, còn UUID sai định dạng có thể bị Spring chuyển đổi trước khi vào controller.

Request nội bộ ví dụ:

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "ASSIGNED",
  "title": "Bạn được giao một Card",
  "cardId": 42,
  "boardId": 7
}
```

`type` được chấp nhận là `ASSIGNED`, `MENTIONED`, `MOVED`; `userId` phải là UUID, `type`/`title` không rỗng. Response có `id`, `type`, `title`, `cardId`, `boardId`, `read`, `createdAt`. Danh sách hiện trả toàn bộ kết quả, không có cursor/page.

| Request field | Kiểu JSON | Bắt buộc/validation |
|---|---|---|
| `userId` | UUID string | Có; `@NotNull` |
| `type` | string | Có; `@NotBlank`, enum `ASSIGNED`, `MENTIONED`, `MOVED` |
| `title` | string | Có; `@NotBlank`; domain trim khoảng trắng đầu/cuối |
| `cardId` | integer/Long hoặc null | Tùy chọn; ID tham chiếu logic tới task-service |
| `boardId` | integer/Long hoặc null | Tùy chọn; ID tham chiếu logic tới board-service |

`NotificationResponse` có `id` (Long), `type`/`title` (string), `cardId`/`boardId` (Long hoặc null), `read` (boolean), `createdAt` (ISO-8601 offset date-time). Client GET trả mảng response, chưa có pagination.

```json
{"id":93,"type":"ASSIGNED","title":"Bạn được giao một Card","cardId":42,"boardId":7,"read":false,"createdAt":"2026-10-28T09:00:00Z"}
```

## Errors, auth và readiness

| Tình huống được handler bắt | HTTP | Code |
|---|---:|---|
| Bean Validation hoặc `IllegalArgumentException` | `400` | `BAD_REQUEST` |
| Notification không tồn tại | `404` | `NOTIFICATION_NOT_FOUND` |
| `SecurityException` (ví dụ sai owner) | `403` | `FORBIDDEN` |

Body lỗi có `code`, `message`, `timestamp`, `path`. Client API lấy `X-User-Id` làm stub; không parse JWT. Internal route không được Gateway cấu hình và không có service-token enforcement trong controller, vì vậy không coi route này là giao tiếp nội bộ đã bảo mật.

`NotificationController` cần `ListMyNotificationsPort`/`MarkReadPort`; `InternalNotificationController` cần `CreateNotificationPort`. Source hiện có adapter lưu Notification nhưng không có implementation các inbound port này. `TaskEventListener` chỉ là placeholder, chưa phát sinh notification từ event.

## API mục tiêu thuộc notification-service

Client target chỉ đọc/mark-read Notification của chính Account. Các route mục tiêu (chưa phải runtime contract):

| Method | Route | Query/body | Success | Authorization |
|---|---|---|---|---|
| `GET` | `/api/v1/notifications/me` | `unreadOnly` tùy chọn, mặc định `false` | `200 Notification[]` | Chỉ recipient hiện tại |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Không body | `200 Notification` đã đọc | Chỉ recipient |

Response field tham chiếu: `id`, `type`, `title`, `cardId`, `boardId`, `read`, `createdAt`. Demo sử dụng `ASSIGNED`, `MENTIONED`, `MOVED`; danh mục type Kỳ 1 cần đối chiếu event đã duyệt. Không cung cấp API client tạo Notification. Internal API runtime hiện tại không có service-token check và không được expose qua Gateway.

Ví dụ response tối thiểu:

```json
{
  "id": 93,
  "type": "ASSIGNED",
  "title": "Bạn được giao một Card",
  "cardId": 42,
  "boardId": 7,
  "read": false,
  "createdAt": "2026-10-28T09:00:00Z"
}
```

`unreadOnly` chỉ nhận boolean; giá trị mặc định `false`. Query paging/order của client list chưa được chốt; không ngầm hiểu endpoint mảng hiện tại đã có pagination. Target errors đề xuất: `400` cho query sai, `401` chưa xác thực, `403` recipient mismatch, `404 NOTIFICATION_NOT_FOUND`; mã lỗi/error envelope cuối cùng theo contract chung và chưa duyệt.

Cascade Project được xử lý bằng event/consumer nội bộ, không phải client HTTP route. Notification chưa lưu `project_id`; phạm vi cần xóa là các Notification gắn Board thuộc Project hay thêm notification không gắn Board vẫn cần chốt. Trước khi bật consumer cần có durable deletion fence để event tạo Notification đến muộn không insert lại row sau cascade; Inbox chống trùng cùng eventId không xử lý được event mới khác ID. Bảng/fence/version và xử lý event cạnh tranh worker được ghi trong [database design](database.md#schema-mục-tiêu-kỳ-1--thiết-kế-để-hoàn-thiện) nhưng chưa được duyệt. API/schema mục tiêu đầy đủ hơn nằm ở [contract chung](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế) và [architecture decisions](../../../docs/project/architecture-decisions.md).
