# Tích hợp Kỳ 1 theo Project-first

**Trạng thái:** Checklist tích hợp mục tiêu Kỳ 1, chưa phải báo cáo end-to-end. Source có Gateway JWT/header forwarding, browser WebSocket ticket và `ws://` proxy; Task có Board lookup adapter, Redis guard, Notification intent producer và assignment-cleanup consumer/topology; shared có consumer retry/DLX policy mà Notification và Task cleanup sử dụng. Task WebSocket/cleanup gates mặc định tắt. Snapshot-sync worker, Project/Membership mutation, Rabbit broker retry/ACK, direct-service boundary và DB migrations chưa được nghiệm thu end-to-end.

**Nguồn chuẩn:** [architecture decisions](architecture-decisions.md), [API Kỳ 1](../api/kanban-api.md), [chỉ mục tài liệu](../README.md).

## Topology và ownership mục tiêu

- Web/Mobile gọi Gateway.
- user-service sở hữu Account; board-service sở hữu Project, Membership, Invitation, Board, Column trong board_db.
- task-service sở hữu Card/Comment trong task_db; notification-service sở hữu Notification trong notification_db.
- Kỳ 1 có Gateway và bốn service; không tách deployable project-service.
- Mỗi service có database riêng; Project và Board cùng board_db. ID tham chiếu liên service là value, không có JPA FK xuyên DB.
- RabbitMQ là kênh event mục tiêu; Outbox/Inbox lưu trong DB cục bộ. Task có internal Board-state lookup được bảo vệ bằng `X-Service-Token`; quyền Project/Membership vẫn đọc từ Redis snapshot, không lấy từ lookup này.

## Điều kiện trước khi chạy tích hợp

- [ ] Khởi động RabbitMQ và Redis bằng Docker Compose, kiểm tra readiness theo [README](../../README.md#6-chạy-môi-trường-local); SQL Server chạy local riêng.
- [x] Chốt theo issue #7: chỉ System Admin được tạo Account, không mở public self-registration; Account mang `SYSTEM_ADMIN` không được làm Owner/Member trong Project. Một cá nhân cần cả hai phạm vi dùng Account quản trị riêng và Account nghiệp vụ không mang `SYSTEM_ADMIN`.
- [x] Ghi nhận SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway. Cách bootstrap cho môi trường triển khai được để bàn sau Kỳ 2.
- [ ] Chốt User directory/profile API, field được lộ và cách UI compose identity, Membership, Comment author.
- [ ] Chốt nguồn xác minh Account tồn tại/ACTIVE khi tạo hoặc Accept Invitation; user-service internal directory lookup chưa có runtime handler. Hoàn thiện directory/profile API và field được lộ.
- [ ] Chốt Invitation expiry/cancel/reject policy, mã lỗi nghiệp vụ, Project/Board transition graph, xử lý sau DLQ và Board preference ownership/storage. Shared retry/DLX policy cùng Notification/Task cleanup queue/DLQ đã có trong source; cần xác minh trên broker khi bật.
- [x] User-service ghi Account change, Outbox và durable snapshot-sync job cùng transaction; worker có cơ chế Redis apply theo version.
- [ ] Bật `USER_AUTH_SNAPSHOT_SYNC_ENABLED` và nghiệm thu Redis apply/retry; Gateway từ chối `503` khi snapshot chưa có hoặc không xác minh được.
- [x] Cập nhật task sheets và API/database docs riêng để cùng phân biệt thiết kế mục tiêu với runtime hiện tại.
- [ ] Implement Project/Membership/Invitation persistence, Board→Project và authorization trong board-service.
- [x] Gateway có JWT validation, Account snapshot gate và ghi đè `X-User-Id`; không tin role/actor ID trong request body/header. Snapshot-sync runtime, tích hợp E2E và direct-service ingress còn mở.
- [ ] Trước khi bật #24: kiểm tra ticket/Origin, secured Board lookup và WebSocket proxy với service thật; kiểm thử revoke khi socket đang mở. Source đã có các thành phần này; Task gates mặc định tắt. Network ingress restriction được hoãn khỏi nhánh hiện tại nhưng vẫn là điều kiện triển khai trước khi cho client dùng trực tiếp qua môi trường chia sẻ.
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

- [ ] Card route mang `projectId`, Task lưu `cards.project_id` và đối chiếu project path với Card khi đọc/sửa. Source đã nối Column validator với Board-state lookup có `X-Service-Token` cho create/move; nghiệm thu E2E qua Gateway và service thật vẫn còn.
- [ ] Move Card cập nhật thứ tự; #24 giữ nguyên route/payload đã duyệt. Browser lấy ticket qua Gateway rồi kết nối `/ws/boards/{boardId}`; source đã có ticket/route/lookup nhưng Task runtime mặc định tắt và chưa có E2E với service thật.
- [ ] Comment/mention giữ đúng author và phạm vi Project.
- [ ] Cursor Card mặc định 5, tối đa 20, sort position DESC, id DESC. Wire proposal per-Column yêu cầu columnId và cursor gắn Board/Column/sort; khi có bất nhất trong lúc tải, client dedupe theo cardId, hiện banner đầu trang yêu cầu reload.
- [x] Notification consumer source xử lý `card.assigned` và `comment.mentioned`, ghi Inbox cùng Notification trong transaction, chống duplicate và tạo một Notification cho mỗi recipient.
- [ ] Notification list/read chỉ trả dữ liệu của Account tương ứng.
- [ ] Notification thuộc Account nhận; Project/Board/Card là context tùy chọn, không cascade theo Project trong Kỳ 1.
- [ ] Board preference mặc định theo user + Project: route tham khảo có trong demo; service owner/storage/access validation vẫn cần chốt trước khi thành backend contract.

### 3. Account deletion — Kỳ 2, ngoài phạm vi tích hợp này

Kỳ 1 không có Account deletion, `DELETING` status hay cascade Project. Kỳ 2 sẽ thiết kế soft delete, Owner succession và cách theo dõi operation riêng.

### 4. Độ tin cậy event

Task producer #25 ghi `card.assigned` và `comment.mentioned` vào shared Outbox cùng transaction với Card/Comment; recipient rules, payload và `aggregateVersion=1` event-intent semantics đã được chốt trong architecture decisions. Không cần thêm Card version hoặc schema/migration theo contract này. Shared có consumer retry/DLX policy; Notification #26 và Task cleanup #22 có queue/binding/DLQ source tương ứng, Inbox transaction và duplicate handling. Notification publish trực tiếp qua RabbitMQ local đã có kiểm thử trước đây; retry/DLQ broker với policy chung, Task Outbox Relay → broker cùng ACK-loss/redelivery vẫn cần nghiệm thu.

- [ ] Producer commit business state và Outbox cùng transaction.
- [ ] Relay đánh dấu SENT khi có Publisher Confirm và không có Publisher Return; khi chưa xác định publish outcome, retry cùng eventId, aggregateVersion, payload.
- [ ] Consumer commit Inbox `(consumerName,eventId)` cùng business state hoặc durable job/checkpoint trước ACK.
- [ ] ACK mất gây redelivery cùng event; Inbox chống side effect lần hai. Durable job và thao tác idempotent xử lý retry nền.
- [ ] Thử broker không nhận, confirm mất, consumer rollback/commit, ACK mất/redelivery. Consumer policy source là tối đa 3 retry cách 5 giây rồi DLQ; poison contract đi thẳng DLQ. Broker E2E và xử lý sau DLQ vẫn chưa hoàn tất.

## Tiêu chí tích hợp

- [ ] Chỉ Gateway là đường client; identity do Gateway xác thực; quyền mọi Project resource dựa trên membership Project. System Admin-only không có Project permission.
- [ ] Account không ở `ACTIVE` không được cấp quyền; authorization guard từ chối khi snapshot thiếu hoặc không xác minh được.
- [ ] Trước response thành công cho Member removal, Redis snapshot cũ không được cấp quyền; nếu không xác minh được invalidation/fencing thì fail closed.
- [ ] Invitation chưa Accept không cấp membership.
- [ ] Mỗi service chỉ sửa dữ liệu do mình sở hữu.
- [ ] Membership revoke không báo hoàn tất trước Redis apply; assignment cleanup tiếp tục retry riêng và không làm sống lại quyền.
- [ ] Đối chiếu HTTP/event/error/status với tài liệu chung và docs service.
- [ ] Chỉ bật `TASK_TRUSTED_GATEWAY_IDENTITY_ENABLED` sau khi chặn truy cập trực tiếp tới task-service; Gateway đã xác thực JWT và ghi đè `X-User-Id`, nhưng direct-service ingress chưa được bảo vệ.
- [ ] Chỉ bật `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY` sau khi đã xác minh queue/DLX/DLQ trên broker mục tiêu và kiểm tra poison message không quay vòng vô hạn. Shared bounded retry/backoff đã có trong source.
- [ ] Chạy end-to-end với DB/RabbitMQ, lưu response/log/event/DB evidence cho happy path và lỗi.

## Chưa thuộc lần tích hợp Kỳ 1

- Git core/Workflow và service/database topology Kỳ 2.

## Thuộc mục tiêu Kỳ 1, chi tiết thiết kế còn mở

- Redis Authentication/Authorization Snapshot là thành phần mục tiêu Kỳ 1, không phải phạm vi Kỳ 2. User-service giữ nguồn chuẩn Account/Auth; board-service giữ nguồn chuẩn Project/Membership; Gateway xác thực danh tính nhưng không sở hữu Authorization Snapshot.
- Service sở hữu nguồn dữ liệu ghi thay đổi, Outbox và snapshot-sync job trong cùng DB transaction; worker apply Redis theo version và retry khi lỗi. Redis lỗi hoặc snapshot không xác minh được thì fail closed. Schema/key, TTL và quy trình rebuild cache vẫn cần chốt trước khi production rollout.
