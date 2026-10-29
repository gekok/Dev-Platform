# 03 — task-service: Card, Comment, realtime và cleanup

**Sở hữu mục tiêu:** Card và Comment trong **task_db**; task-service không sở hữu Project, Board, Column, Account hay Notification.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/task-service/docs/api.md), [ghi chú Account deletion Kỳ 2](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **CardController** khai báo Project-scoped create/list Card, move, assign và tạo Comment; detail GET và cursor chưa có.
- WebSocket config khai báo **/ws/boards/{boardId}**; nhiều handler còn nhận **X-User-Id** hoặc chưa yêu cầu identity.
- Card mapping có **project_id**/**board_id**/**column_id**; assignment target là `task_assignees` với `membership_version`. Redis authorization guard/reader parse cả `membershipVersion` lẫn `authorizationVersion`; cleanup fence/job/worker dùng generation cho assignment và giữ source aggregate version cho completion.
- Inbound use-case implementations, Rabbit listener/ACK wiring, DB migrations, Board/Column-to-Project validation và cursor API chưa có; cleanup scheduler mặc định tắt. Account/Project cascade scaffold đã được gỡ.

## Việc cần làm

- [x] Chuyển Card controller routes sang Project scope; lưu `cards.project_id` scalar và scope query theo Project. Inbound application ports chưa được nối.
- [x] Thêm Redis authorization guard/reader và lấy assignee `membershipVersion` từ snapshot; `X-User-Id` vẫn cần Gateway identity hardening.
- [x] Thay assignment mapping bằng `TaskAssignees`, fence và job có generation. Cần migration có version.
- [x] Thêm recorder/worker seam ghi Inbox + fence + job trước ACK boundary tương lai; listener/ACK wiring còn TODO. Worker chỉ clear đúng `(project_id,user_id,membership_version)`, giữ request `aggregateVersion` và phát completion qua Outbox.
- [ ] Giữ Comment/content/attribution khi Member rời; Account deletion/cascade và Board write-lock phục vụ cascade không thuộc Kỳ 1.
- [ ] Implement cursor list API page size mặc định 5/tối đa 20, sort **position DESC, id DESC**; cursor bind `projectId`, `boardId`, `columnId` và sort contract.
- [ ] Giữ Board/Column→Project validation như seam tích hợp; không tin `projectId` do client gửi làm bằng chứng quan hệ khi internal REST chưa bảo mật.
- [ ] Khi nối các consumer domain-event delta, dùng eventId/Inbox để chống redelivery; aggregateVersion chỉ giúp nhận biết phiên bản event cũ/mới hoặc dấu hiệu thiếu phiên bản. Quy tắc stale/gap theo từng event còn chờ chốt; version không thay Inbox. Redis snapshot CAS đã có quy tắc riêng theo authorizationVersion.
- [ ] Hoàn thiện realtime/reload behavior; khi pagination phát hiện dữ liệu bất nhất, client hiện banner đầu trang yêu cầu reload theo quyết định đã thống nhất.
- [ ] Cập nhật API/database docs riêng sau khi contract/schema thực sự được chốt.

## Tiêu chí nghiệm thu khi triển khai

- Owner và Member được thao tác Card/Comment trong Project; tài khoản không có Membership bị từ chối.
- `SYSTEM_ADMIN` tự nó không tạo Membership và không cấp quyền đọc/ghi Card; quyền Task bắt nguồn từ Account/Project snapshots trong Redis.
- List Card có cursor ổn định theo sort đã thống nhất; client dedupe theo cardId và hiển thị reload banner khi cần.
- Project-scoped route không thể truy cập Card có `project_id` khác với path.
- Membership cleanup chỉ xóa đúng version; rejoin version mới không mất assignment và ACK chỉ sau khi durable Inbox/fence/job commit.
- Member revoke hoàn tất theo Redis apply; cleanup tiếp tục riêng ở nền và không khôi phục quyền.
- Kỳ 1 không chứa Project cascade, cascade batch/delete write barrier hay `DELETING` flow.
