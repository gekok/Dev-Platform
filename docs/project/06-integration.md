# Tích hợp Kỳ 1 theo Project-first

**Trạng thái:** Checklist mục tiêu, chưa phải báo cáo luồng runtime. Task sheets 01–05 và API/database docs service đã được đồng bộ về Project-first; backend runtime vẫn còn Board-first và cascade skeleton chưa chạy end-to-end.

**Nguồn chuẩn:** [architecture decisions](architecture-decisions.md), [API Kỳ 1](../api/kanban-api.md), [chỉ mục tài liệu](../README.md).

## Topology và ownership mục tiêu

- Web/Mobile gọi Gateway.
- user-service sở hữu Account; board-service sở hữu Project, Membership, Invitation, Board, Column trong board_db.
- task-service sở hữu Card/Comment trong task_db; notification-service sở hữu Notification trong notification_db.
- Kỳ 1 có Gateway và bốn service; không tách deployable project-service.
- Mỗi service có database riêng; Project và Board cùng board_db. ID tham chiếu liên service là value, không có JPA FK xuyên DB.
- RabbitMQ là kênh event mục tiêu; Outbox/Inbox lưu trong DB cục bộ. Internal REST để task-service xác minh Project access chưa thuộc đường tích hợp cho tới khi có bảo mật nội bộ.

## Điều kiện trước khi chạy tích hợp

- [ ] Chốt route/payload Admin tạo Account, xóa Account và status operation.
- [ ] Chốt payload/error Membership và Invitation, invitation read/reject/cancel/expiry.
- [ ] Chốt Project/Board status transitions, cascade operation status/response/event contract, Notification cleanup scope, retry/DLQ và write barrier.
- [x] Cập nhật task sheets và API/database docs riêng để cùng phân biệt thiết kế mục tiêu với runtime hiện tại.
- [ ] Implement Project/Membership/Invitation persistence, Board→Project và authorization trong board-service.
- [ ] Gateway có JWT auth/identity forwarding an toàn; không tin X-User-Id do client gửi.
- [ ] Task write guard được nối mọi mutation và write barrier bảo đảm trước cascade.
- [ ] Consumer, worker, Outbox completion, Inbox transaction, retry/DLQ và operation tracking được triển khai.

## Kịch bản tích hợp

### 1. Project, Board và membership

- [ ] Sau khi chốt contract quản trị Account, Admin tạo Account; Account Admin không tự có Project quyền.
- [ ] Tạo Project; người tạo trở thành Owner duy nhất.
- [ ] Owner tạo Board trong Project và Columns.
- [ ] Owner mời Account đã tồn tại; Invitation PENDING chưa cấp quyền.
- [ ] Người nhận Accept; Membership tạo đúng một lần trong board-service transaction.
- [ ] Owner/Member tạo Column/Card, chuyển Card và comment. Chỉ Owner tạo Board, quản lý member và đổi lifecycle.
- [ ] Owner gỡ Member hoặc Member tự rời: unassign người đó khỏi Cards trước khi hoàn tất membership removal; giữ Comment/history attribution.
- [ ] Account bị xóa: hủy Invitation PENDING của Account. Project mà Account chỉ là Member được giữ; Project do Account sở hữu được cascade xóa.

### 2. Card, Comment, realtime và Notification

- [ ] Card nằm trong Board thuộc Project; kiểm tra Column thuộc đúng Board và assignee thuộc cùng Project.
- [ ] Move Card cập nhật thứ tự; realtime event đồng bộ client.
- [ ] Comment/mention giữ đúng author và phạm vi Project.
- [ ] Cursor Card mặc định 5, tối đa 20, sort position DESC, id DESC. Khi có bất nhất trong lúc tải, client dedupe theo cardId, hiện banner đầu trang yêu cầu reload.
- [ ] Notification list/read chỉ trả dữ liệu của Account tương ứng.
- [ ] Board preference mặc định theo user + Project: route tham khảo có trong demo; service owner/storage/access validation vẫn cần chốt trước khi thành backend contract.

### 3. Account deletion và Project cascade

- [ ] System Admin khởi tạo xóa; API trả 202 Accepted + operationId sau khi request và Outbox commit.
- [ ] Account thành DELETING; user-service điều phối, board-service xử lý Project owned và membership ở Project khác.
- [ ] Mỗi Project owned có projectDeletionId; Task/Notification xử lý dữ liệu của service mình theo batch và trả completion.
- [ ] Task-service xóa Card/Comment thuộc Project; Notification scope phải theo contract đã chốt.
- [ ] Board-service chỉ xóa Project/Board/Column/Membership sau khi đủ downstream confirmations.
- [ ] Account chỉ hoàn tất sau khi mọi Project owned và cleanup membership thành công; tombstone/attribution theo policy.
- [ ] Client theo dõi operation; COMPLETED chỉ sau xác nhận nghiệp vụ, không phải chỉ sau khi Rabbit nhận event.

### 4. Độ tin cậy event

- [ ] Producer commit business state và Outbox cùng transaction.
- [ ] Relay đánh dấu SENT khi có Publisher Confirm và không có Publisher Return; khi chưa xác định publish outcome, retry cùng eventId, aggregateVersion, payload.
- [ ] Consumer commit Inbox (consumerName, eventId) cùng business state hoặc durable deletion job/checkpoint trước ACK.
- [ ] ACK mất gây redelivery cùng event; Inbox chống side effect lần hai. Worker/job key còn bảo vệ thao tác nghiệp vụ lặp khi producer phát event mới cho cùng operation.
- [ ] Thử broker không nhận, confirm mất, consumer rollback/commit, ACK mất/redelivery, retry hết hạn và DLQ theo policy sau khi chốt.

## Tiêu chí tích hợp

- [ ] Chỉ Gateway là đường client; quyền mọi Project resource dựa trên membership Project.
- [ ] Invitation chưa Accept không cấp membership.
- [ ] Mỗi service chỉ sửa dữ liệu do mình sở hữu.
- [ ] Deletion không báo hoàn tất trước downstream confirmation; retry có thể tiếp tục từ operation/job checkpoint.
- [ ] Đối chiếu HTTP/event/error/status với tài liệu chung và docs service.
- [ ] Chạy end-to-end với DB/RabbitMQ, lưu response/log/event/DB evidence cho happy path và lỗi.

## Chưa thuộc lần tích hợp Kỳ 1

- Git core/Workflow và service/database topology Kỳ 2.
- Snapshot/Redis schema và TTL chưa chốt; khi cache không xác minh được, policy hiện hành là fail closed và trả lỗi.
