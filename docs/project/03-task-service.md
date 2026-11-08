# 03 — task-service: Card, Comment, realtime và cleanup

**Sở hữu mục tiêu:** Card và Comment trong **task_db**; task-service không sở hữu Project, Board, Column, Account hay Notification.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/task-service/docs/api.md), [kế hoạch realtime/event #24/#25](../../services/task-service/docs/realtime-events-proposal.md), [ghi chú Account deletion Kỳ 2](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **CardController** khai báo Project-scoped create/list/detail Card, move, assign và đọc/tạo Comment; detail GET lọc theo persisted `project_id`. List dùng keyset cursor per Column, nhưng cursor wire/error/stale contract còn tạm thời. Gateway đã xác thực JWT và ghi đè `X-User-Id` cho request đi qua Gateway; Task vẫn fail-closed khi trusted Gateway identity gate mặc định tắt. Direct-service ingress chưa bị chặn, nên chưa bật gate hoặc coi identity an toàn khi gọi thẳng Task.
- WebSocket #24 có service-side handler, authorization lifecycle và fanout best-effort sau commit. Gateway ticket cho browser, `ws://` proxy và adapter Task→Board lookup đã có source. Task runtime mặc định tắt bằng bốn readiness flags; chưa xác nhận E2E qua Task/Board/Redis thật. Ticket nằm trong query string nên ingress/access log cần che giá trị này.
- Card mapping có **project_id**/**board_id**/**column_id**; assignment target là `task_assignees` với `membership_version`. Redis authorization guard/reader parse cả `membershipVersion` lẫn `authorizationVersion`; cleanup fence/job/worker dùng generation cho assignment và giữ source aggregate version cho completion.
- Use case Card/Comment đã có implementation trong source; chưa xác nhận E2E với Gateway, Redis và Board Service thật. `ColumnRelationshipValidator` hiện nối vào `BoardWriteGuard`/secured Board lookup để kiểm tra Board, Project, Column và trạng thái; Board lookup lỗi vẫn fail-closed. Trusted Gateway identity gate vẫn mặc định tắt vì direct-service ingress chưa bị chặn. Consumer event cleanup #22 có durable queue/DLQ source và dùng shared retry factory; chỉ đăng ký khi `MESSAGING_ENABLED`, `MESSAGING_CONSUMERS_ENABLED`, `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY` cùng bật; broker redelivery/retry/DLQ vẫn chưa được kiểm chứng. Versioned DB migrations còn mở. Cleanup scheduler mặc định tắt. Account/Project cascade scaffold đã được gỡ.

## Việc cần làm

- [x] Chuyển Card controller routes sang Project scope; lưu `cards.project_id` scalar, nối các use case Card/Comment và scope query theo Project. E2E identity/authorization và Board/Column relationship validation vẫn là tích hợp mở.
- [x] Thêm Redis authorization guard/reader và lấy assignee `membershipVersion` từ snapshot; Gateway đã ghi đè `X-User-Id`, nhưng Task identity gate và direct-service boundary chưa bật/nghiệm thu.
- [x] Thay assignment mapping bằng `TaskAssignees`, fence và job có generation; local bootstrap SQL tạo schema cho DB rỗng. Versioned migration chưa được chọn; database đã tồn tại phải đối chiếu trước rollout.
- [x] Thêm recorder/worker và listener ghi Inbox + fence + job trước ACK; listener dùng AUTO ACK sau khi handler transaction commit, worker chỉ clear đúng `(project_id,user_id,membership_version)`, giữ request `aggregateVersion`, phát completion qua Outbox. Broker redelivery/retry policy chưa nghiệm thu.
- [ ] Giữ Comment/content/attribution khi Member rời; Account deletion/cascade và Board write-lock phục vụ cascade không thuộc Kỳ 1.
- [x] Implement list theo Column bằng keyset, limit mặc định 5/tối đa 20, sort **position DESC, id DESC**; cursor hiện bind `projectId`, `boardId`, `columnId` và sort.
- [ ] Chốt cursor wire/error contract và stale-cursor behavior của #20; wire hiện tại chỉ là implementation tạm thời.
- [x] Nối Board/Column→Project validation vào secured Board lookup; kiểm tra Board ID, Project ID, Column membership và trạng thái ACTIVE. `projectId` do client gửi không tự chứng minh quan hệ; E2E create/move qua Gateway vẫn cần nghiệm thu.
- [ ] Khi nối consumer domain-event, dùng `eventId`/Inbox để chống redelivery và đọc `aggregateVersion` sẵn có từ envelope. Chốt cách xử lý stale/gap theo từng contract; không thêm Card version column trừ khi event contract được duyệt yêu cầu.
- [x] Chuẩn bị handler #24 sau readiness gate: giữ route/message đã duyệt, tra Board→Project từ port, kiểm tra membership ở handshake/định kỳ/trước gửi, phát sau commit và fanout best-effort trong cùng Task instance. Runtime mặc định tắt.
- [ ] Task tích hợp xác nhận Card/Comment acceptance qua Gateway, secured Board-to-Project lookup, Gateway WebSocket proxy (#31), cấu hình readiness; direct-service ingress cần được bảo vệ trước khi bật Task identity gate. Ticket handshake cần được kiểm tra với browser và ingress logging phải che query ticket.
- [x] Thêm producer Outbox #25 cho `card.assigned` và `comment.mentioned`; ghi cùng transaction nghiệp vụ, dùng `TASK_NOTIFICATION_INTENT`/`eventId`/`aggregateVersion=1`, không thêm schema hoặc Card version. Recipient rules và payload được ghi trong architecture/API docs; Notification consumer, broker ACK/retry/DLQ và E2E thuộc task tích hợp.
- [ ] Khi pagination phát hiện dữ liệu bất nhất, client hiện banner đầu trang yêu cầu reload theo quyết định đã thống nhất.
- [x] Rà soát API/database docs: mô tả #24 có implementation phía Task nhưng runtime mặc định tắt; không đề xuất thêm Card `aggregate_version`.

## Tiêu chí nghiệm thu khi triển khai

- Owner và Member được thao tác Card/Comment trong Project; tài khoản không có Membership bị từ chối.
- `SYSTEM_ADMIN` tự nó không tạo Membership và không cấp quyền đọc/ghi Card; quyền Task bắt nguồn từ Account/Project snapshots trong Redis.
- List Card có cursor ổn định theo sort đã thống nhất; client dedupe theo cardId và hiển thị reload banner khi cần.
- Project-scoped route không thể truy cập Card có `project_id` khác với path.
- Membership cleanup chỉ xóa đúng version; rejoin version mới không mất assignment và ACK chỉ sau khi durable Inbox/fence/job commit.
- Member revoke hoàn tất theo Redis apply; cleanup tiếp tục riêng ở nền và không khôi phục quyền.
- Kỳ 1 không chứa Project cascade, cascade batch/delete write barrier hay `DELETING` flow.
