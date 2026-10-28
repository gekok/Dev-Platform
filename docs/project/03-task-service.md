# 03 — task-service: Card, Comment, realtime và cleanup

**Sở hữu mục tiêu:** Card và Comment trong **task_db**; task-service không sở hữu Project, Board, Column, Account hay Notification.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/task-service/docs/api.md), [skeleton cascade](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **CardController** khai báo create/list Card theo Board, move, assign và tạo Comment.
- WebSocket config khai báo **/ws/boards/{boardId}**; nhiều handler còn nhận **X-User-Id** hoặc chưa yêu cầu identity.
- Card hiện giữ **board_id**/**column_id**, assignee và Comment; không có cursor list API Project-first.
- Đã có deletion job/checkpoint shape, batch repository query, write-lock entity/guard. Chưa có listener/worker/Inbox job transaction/completion publisher; guard chưa nối vào mutation.

## Việc cần làm

- [ ] Cập nhật route và authorization theo Project-first contract; Board thuộc Project, mọi thao tác yêu cầu identity từ Gateway đã xác thực.
- [ ] Hoàn thiện list Card cursor: mặc định 5, tối đa 20, sort **position DESC, id DESC**; wire proposal cho UI infinite-scroll theo từng Column yêu cầu `columnId` và cursor gắn với `boardId` + `columnId` + sort. Query/envelope/invalid-cursor behavior cần duyệt trước OpenAPI.
- [ ] Chặn gán Account không thuộc cùng Project theo contract; khi nhận cleanup event sau khi board-service đã thu hồi Membership, unassign Cards ở nền và giữ Comment/history.
- [ ] Giữ Comment/content/attribution khi Account bị xóa hoặc rời Project; UI phân biệt “đã bị xóa” và “không còn trong Project”.
- [ ] Implement Project cascade và assignment cleanup consumer: Inbox + durable job trước ACK, idempotent batch xóa/unassign, checkpoint và completion Outbox. Cleanup cũ chỉ được clear assignment thuộc membership generation bị revoke; hoặc board-service phải ngăn rejoin/assign đến khi job hoàn thành.
- [ ] Nối **TaskWriteGuard** vào mọi Card mutation. Chốt write-barrier giữa board-service và task-service để không có ghi mới lọt trong lúc cascade.
- [ ] Dùng eventId/Inbox để chống redelivery; aggregateVersion chỉ giúp nhận biết phiên bản event cũ/mới hoặc dấu hiệu thiếu phiên bản. Quy tắc xử lý stale/gap còn chờ chốt tại architecture decisions; version không thay Inbox.
- [ ] Hoàn thiện realtime/reload behavior; khi pagination phát hiện dữ liệu bất nhất, client hiện banner đầu trang yêu cầu reload theo quyết định đã thống nhất.
- [ ] Cập nhật API/database docs riêng sau khi contract/schema thực sự được chốt.

## Tiêu chí nghiệm thu khi triển khai

- Owner và Member được thao tác Card/Comment trong Project; tài khoản không có Membership bị từ chối.
- List Card có cursor ổn định theo sort đã thống nhất; client dedupe theo cardId và hiển thị reload banner khi cần.
- Retry cùng deletion request không xóa nhầm dữ liệu khác hoặc chạy side effect lặp; ACK chỉ sau transaction ghi Inbox/job thành công.
- Member mất quyền trước khi API xác nhận membership removal; Task assignment cleanup hoàn tất độc lập ở nền và không được trì hoãn việc thu hồi quyền.
- Project cascade không bắt đầu xóa batch trước khi write barrier được xác nhận.
