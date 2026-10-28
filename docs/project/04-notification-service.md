# 04 — notification-service: Notification và cleanup

**Sở hữu mục tiêu:** Notification trong **notification_db**.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/notification-service/docs/api.md), [skeleton cascade](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **NotificationController** khai báo GET list và PATCH mark-read theo **X-User-Id**.
- **InternalNotificationController** khai báo **POST /internal/v1/notifications**; không thấy authentication enforcement trong controller. Không coi comment “service token” là bảo vệ đã hoạt động.
- **TaskEventListener** là placeholder; chưa có event consumer tạo Notification end-to-end.
- Đã có Notification deletion job/entity/repository shape; chưa có listener, worker hoặc completion publisher.

## Việc cần làm

- [ ] Hoàn thiện list/mark-read chỉ cho Account hiện tại theo Gateway identity, không tin header do client tự gửi.
- [ ] Chốt phương thức giao tiếp producer→notification-service; internal REST chưa đưa vào integration path khi chưa có bảo mật service-to-service.
- [ ] Nối các loại event/tạo Notification theo event contract đã duyệt; bỏ mô tả PR_REVIEWED như một tính năng Kỳ 2 đã chốt.
- [ ] Chốt phạm vi cascade Notification: các notification có Board/Project reference và dữ liệu không gắn Board/Project.
- [ ] Chốt và lưu deletion fence/tombstone cho Board/Project để event Notification khác ID đến muộn không tạo lại dữ liệu sau cascade.
- [ ] Implement Inbox + deletion job + batch worker + completion Outbox; redelivery không tạo side effect lặp. Mọi notification-create handler phải kiểm tra fence trước insert.
- [ ] Xác định retention/audit khi notification bị xóa cùng Project/Account theo policy đã duyệt.
- [ ] Đồng bộ API/database docs riêng sau khi event và cleanup contract chốt.

## Tiêu chí nghiệm thu khi triển khai

- Client chỉ đọc/đánh dấu Notification của chính mình.
- Internal route không được xem là an toàn trước khi có xác thực service-to-service.
- Operation Project chỉ nhận completion sau khi notification cleanup trong phạm vi đã chốt hoàn tất.
- Consumer không insert Notification mới cho Board đã bị fence bởi Project cascade, kể cả khi event create chưa từng được xử lý trước đó.
