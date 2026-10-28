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
