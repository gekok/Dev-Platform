# 04 — notification-service: Notification theo Account nhận

**Sở hữu mục tiêu:** Notification thuộc Account nhận trong **notification_db**; mỗi row có thể có tối đa một Project context và một Board/Card context.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/notification-service/docs/api.md), [ghi chú Account deletion Kỳ 2](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **NotificationController** khai báo GET list và PATCH mark-read theo **X-User-Id**.
- **InternalNotificationController** là skeleton cho **POST /internal/v1/notifications**, bị tắt mặc định bằng `INTERNAL_NOTIFICATIONS_ENABLED=false`; nếu bật thì chưa có authentication enforcement. Không coi route này là giao tiếp service-to-service an toàn.
- **TaskEventListener** là placeholder; chưa có event consumer tạo Notification end-to-end.
- `NotificationEntity` hiện có nullable `project_id` cùng recipient Account và Board/Card context. Inbound ports và producer consumer flow chưa nối hoàn chỉnh. Account/Project deletion jobs không thuộc Kỳ 1.

## Việc cần làm

- [ ] Hoàn thiện list/mark-read chỉ cho Account hiện tại theo Gateway identity, không tin header do client tự gửi.
- [ ] Chốt phương thức giao tiếp producer→notification-service; internal REST chưa đưa vào integration path khi chưa có bảo mật service-to-service.
- [ ] Nối các loại event/tạo Notification theo event contract đã duyệt; bỏ mô tả PR_REVIEWED như một tính năng Kỳ 2 đã chốt.
- [x] Thêm nullable `project_id` vào NotificationEntity/DTO; giữ recipient `user_id` và nullable Board/Card context, tất cả là scalar reference không FK chéo DB.
- [ ] Đảm bảo inbox Notification tổng hợp theo Account và không cascade xóa theo Project/Board trong Kỳ 1.
- [ ] Nối producer/consumer Notification theo event contract sau khi quyết định được chốt; internal REST chưa dùng khi chưa có bảo mật service-to-service.
- [x] Gỡ Account/Project deletion handler/job khỏi runtime; policy retention và deletion thuộc Kỳ 2.
- [ ] Đồng bộ API/database docs riêng sau khi event và cleanup contract chốt.

## Tiêu chí nghiệm thu khi triển khai

- Client chỉ đọc/đánh dấu Notification của chính mình.
- Internal route không được xem là an toàn trước khi có xác thực service-to-service.
- Mỗi Notification thuộc đúng một recipient Account; Project/Board/Card chỉ giúp điều hướng context.
- Account inbox có thể tổng hợp Notification từ nhiều Project và cả thông báo hệ thống không có Project context.
- Kỳ 1 không cascade delete Notification theo Project và không có Notification deletion job.
