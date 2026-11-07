# Tích hợp Kỳ 1 theo Project-first

**Trạng thái:** Checklist tích hợp mục tiêu Kỳ 1, chưa phải báo cáo end-to-end. Gateway JWT filter và header forwarding đã có trong source; Task controller có Project-scoped routes, Redis guard và assignment-cleanup consumer source. Snapshot-sync worker mặc định tắt, Project/Membership mutation, broker delivery/ACK, direct-service boundary và DB migrations chưa được nghiệm thu end-to-end.

**Nguồn chuẩn:** [architecture decisions](architecture-decisions.md), [API Kỳ 1](../api/kanban-api.md), [chỉ mục tài liệu](../README.md).

## Topology và ownership mục tiêu

- Web/Mobile gọi Gateway.
- user-service sở hữu Account; board-service sở hữu Project, Membership, Invitation, Board, Column trong board_db.
- task-service sở hữu Card/Comment trong task_db; notification-service sở hữu Notification trong notification_db.
- Kỳ 1 có Gateway và bốn service; không tách deployable project-service.
- Mỗi service có database riêng; Project và Board cùng board_db. ID tham chiếu liên service là value, không có JPA FK xuyên DB.
- RabbitMQ là kênh event mục tiêu; Outbox/Inbox lưu trong DB cục bộ. Internal REST để task-service xác minh Project access chưa thuộc đường tích hợp cho tới khi có bảo mật nội bộ.

## Điều kiện trước khi chạy tích hợp

- [ ] Khởi động RabbitMQ và Redis bằng Docker Compose, kiểm tra readiness theo [README](../../README.md#6-chạy-môi-trường-local); SQL Server chạy local riêng.
- [x] Chốt theo issue #7: chỉ System Admin được tạo Account, không mở public self-registration; Account mang `SYSTEM_ADMIN` không được làm Owner/Member trong Project. Một cá nhân cần cả hai phạm vi dùng Account quản trị riêng và Account nghiệp vụ không mang `SYSTEM_ADMIN`.
- [x] Ghi nhận SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway. Cách bootstrap cho môi trường triển khai được để bàn sau Kỳ 2.
- [ ] Chốt User directory/profile API, field được lộ và cách UI compose identity, Membership, Comment author.
- [ ] Chốt nguồn xác minh Account tồn tại/ACTIVE khi tạo hoặc Accept Invitation do internal REST chưa được bảo mật; hoàn thiện directory/profile API và field được lộ.
- [ ] Chốt Invitation expiry/cancel/reject policy, mã lỗi nghiệp vụ, Project/Board transition graph, chi tiết queue/DLQ và Board preference ownership/storage.
- [x] User-service ghi Account change, Outbox và durable snapshot-sync job cùng transaction; worker có cơ chế Redis apply theo version.
- [ ] Bật `USER_AUTH_SNAPSHOT_SYNC_ENABLED` và nghiệm thu Redis apply/retry; Gateway từ chối `503` khi snapshot chưa có hoặc không xác minh được.
- [x] Cập nhật task sheets và API/database docs riêng để cùng phân biệt thiết kế mục tiêu với runtime hiện tại.
- [ ] Implement Project/Membership/Invitation persistence, Board→Project và authorization trong board-service.
- [x] Gateway có JWT validation, Account snapshot gate và ghi đè `X-User-Id`; không tin role/actor ID trong request body/header. Snapshot-sync runtime, tích hợp E2E và direct-service ingress còn mở.
- [ ] Trước khi bật #24: bảo vệ direct-service boundary, hoàn tất secured Board-to-Project lookup và WebSocket proxy; kiểm thử revoke khi socket đang mở. HTTP Gateway identity propagation đã có source.
- [ ] Task authorization guard đọc Account/Project snapshots từ Redis trên mọi Card/Comment route có bảo vệ.
- [ ] Membership cleanup worker, Inbox transaction, fence và Outbox completion được nối; cleanup không chặn revoke completion.

## Kịch bản tích hợp

### 1. Project, Board và membership

- [ ] Sau khi chốt contract quản trị Account, Admin tạo Account; Account Admin không tự có Project quyền.
- [ ] Account nghiệp vụ `ACTIVE` gọi qua Gateway tạo Project; actor ID lấy từ identity context đã xác thực, không từ body/header client. Project và Owner membership commit cùng transaction; người tạo trở thành Owner duy nhất.
- [ ] Owner tạo Board trong Project và Columns.
- [ ] Owner mời Account đã tồn tại; Invitation PENDING chưa cấp quyền.
- [ ] Người nhận Accept; Membership tạo đúng một lần trong board-service transaction.
- [ ] Owner/Member tạo Column/Card, chuyển Card và comment. Chỉ Owner tạo Board, quản lý member và đổi lifecycle.
- [ ] Owner gỡ Member hoặc Member tự rời: ghi Membership change, Outbox và snapshot-sync job cùng transaction; quyền bị thu hồi khi Redis apply. Khi apply còn pending, trả `202` + `operationId`/`statusUrl`.
- [ ] Task unassign Card chạy nền độc lập; cleanup chỉ xóa assignment đúng `membershipVersion`, giữ source `aggregateVersion` để đối chiếu completion, retry được và không trì hoãn revoke completion.

### 2. Card, Comment, realtime và Notification

- [ ] Card route mang `projectId`, Task lưu `cards.project_id` và đối chiếu project path với Card khi đọc/sửa. Kiểm tra Board/Column thuộc Project vẫn là integration TODO vì internal REST chưa được bảo mật.
- [ ] Move Card cập nhật thứ tự; #24 giữ nguyên route/payload đã duyệt nhưng runtime chưa triển khai trước khi direct-service boundary, Board lookup và WS proxy sẵn sàng.
- [ ] Comment/mention giữ đúng author và phạm vi Project.
- [ ] Cursor Card mặc định 5, tối đa 20, sort position DESC, id DESC. Wire proposal per-Column yêu cầu columnId và cursor gắn Board/Column/sort; khi có bất nhất trong lúc tải, client dedupe theo cardId, hiện banner đầu trang yêu cầu reload.
- [ ] Notification list/read chỉ trả dữ liệu của Account tương ứng.
- [ ] Notification thuộc Account nhận; Project/Board/Card là context tùy chọn, không cascade theo Project trong Kỳ 1.
- [ ] Board preference mặc định theo user + Project: route tham khảo có trong demo; service owner/storage/access validation vẫn cần chốt trước khi thành backend contract.

### 3. Account deletion — Kỳ 2, ngoài phạm vi tích hợp này

Kỳ 1 không có Account deletion, `DELETING` status hay cascade Project. Kỳ 2 sẽ thiết kế soft delete, Owner succession và cách theo dõi operation riêng.

### 4. Độ tin cậy event

Task producer #25 ghi `card.assigned` và `comment.mentioned` vào shared Outbox cùng transaction với Card/Comment; recipient rules, payload và `aggregateVersion=1` event-intent semantics đã được chốt trong architecture decisions. Không cần thêm Card version hoặc schema/migration theo contract này. Consumer Notification, broker routing/Inbox/ACK, retry/DLQ và end-to-end delivery chưa được tích hợp; consumer cleanup #22 là flow riêng. Các tiêu chí generic dưới đây còn là acceptance của task tích hợp, không phải bằng chứng producer đã giao được event qua RabbitMQ.

- [ ] Producer commit business state và Outbox cùng transaction.
- [ ] Relay đánh dấu SENT khi có Publisher Confirm và không có Publisher Return; khi chưa xác định publish outcome, retry cùng eventId, aggregateVersion, payload.
- [ ] Consumer commit Inbox `(consumerName,eventId)` cùng business state hoặc durable job/checkpoint trước ACK.
- [ ] ACK mất gây redelivery cùng event; Inbox chống side effect lần hai. Durable job và thao tác idempotent xử lý retry nền.
- [ ] Thử broker không nhận, confirm mất, consumer rollback/commit, ACK mất/redelivery; consumer retry tối đa 3 lần, cách nhau 5 giây rồi chuyển DLQ. Chính sách chưa được nối runtime.

## Tiêu chí tích hợp

- [ ] Chỉ Gateway là đường client; identity do Gateway xác thực; quyền mọi Project resource dựa trên membership Project. System Admin-only không có Project permission.
- [ ] Account không ở `ACTIVE` không được cấp quyền; authorization guard từ chối khi snapshot thiếu hoặc không xác minh được.
- [ ] Trước response thành công cho Member removal, Redis snapshot cũ không được cấp quyền; nếu không xác minh được invalidation/fencing thì fail closed.
- [ ] Invitation chưa Accept không cấp membership.
- [ ] Mỗi service chỉ sửa dữ liệu do mình sở hữu.
- [ ] Membership revoke không báo hoàn tất trước Redis apply; assignment cleanup tiếp tục retry riêng và không làm sống lại quyền.
- [ ] Đối chiếu HTTP/event/error/status với tài liệu chung và docs service.
- [ ] Chỉ bật `TASK_TRUSTED_GATEWAY_IDENTITY_ENABLED` sau khi chặn truy cập trực tiếp tới task-service; Gateway đã xác thực JWT và ghi đè `X-User-Id`, nhưng direct-service ingress chưa được bảo vệ.
- [ ] Chỉ bật `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY` sau khi queue có bounded retry/backoff và DLQ tương thích với NACK/requeue; kiểm tra poison message không quay vòng vô hạn.
- [ ] Chạy end-to-end với DB/RabbitMQ, lưu response/log/event/DB evidence cho happy path và lỗi.

## Chưa thuộc lần tích hợp Kỳ 1

- Git core/Workflow và service/database topology Kỳ 2.

## Thuộc mục tiêu Kỳ 1, chi tiết thiết kế còn mở

- Redis Authentication/Authorization Snapshot là thành phần mục tiêu Kỳ 1, không phải phạm vi Kỳ 2. User-service giữ nguồn chuẩn Account/Auth; board-service giữ nguồn chuẩn Project/Membership; Gateway xác thực danh tính nhưng không sở hữu Authorization Snapshot.
- Service sở hữu nguồn dữ liệu ghi thay đổi, Outbox và snapshot-sync job trong cùng DB transaction; worker apply Redis theo version và retry khi lỗi. Redis lỗi hoặc snapshot không xác minh được thì fail closed. Schema/key, TTL và quy trình rebuild cache vẫn cần chốt trước khi production rollout.
