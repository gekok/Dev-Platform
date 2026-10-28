# 01 — user-service: Account, Auth và xóa tài khoản

**Sở hữu mục tiêu:** Account, credential, trạng thái Account, account deletion operation và tombstone trong **user_db**.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/user-service/docs/api.md), [skeleton cascade](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **UserController** khai báo **POST /api/v1/auth/register**, **POST /api/v1/auth/login** và **GET /api/v1/users/me**.
- **/users/me** hiện đọc **X-User-Id**; chưa parse JWT. **LoginUserPort** chưa có implementation.
- Đã có **AccountStatus**, operation persistence/use-case biên và completion handler. Chưa có Admin controller, listener, gateway auth hoặc flow end-to-end.
- Public registration là route cũ; nghiệp vụ mục tiêu là System Admin quản lý tạo/xóa Account. Route/payload provision Account chưa chốt.

## Việc cần làm

- [ ] Chốt contract tạo Account cho System Admin và chính sách route register cũ; không mở public signup ngoài quyết định.
- [ ] Bổ sung API directory search để Owner chọn User đã tồn tại và bulk profile/tombstone để UI render Member/author; chỉ trả dữ liệu tối thiểu và chỉ tìm Account ACTIVE theo policy được duyệt.
- [ ] Implement login, token issuance/validation và current-user từ identity đáng tin cậy; bỏ identity stub do client tự gửi.
- [ ] Chốt Admin delete/status API rồi mới expose request/get-operation ports qua controller.
- [ ] Đảm bảo Account **DELETING** không đăng nhập/tạo thao tác mới; hiện **AccountAccessGuard** chưa được nối.
- [ ] Nối consumer **ACCOUNT_PROJECT_CLEANUP_COMPLETED** với Inbox và completion transaction; ACK sau commit.
- [ ] Khi hoàn tất cascade, lưu tombstone và xử lý PII theo quyết định; giữ thông tin attribution lịch sử cần thiết.
- [ ] Cập nhật [API docs riêng](../../services/user-service/docs/api.md) và [database docs riêng](../../services/user-service/docs/database.md) khi field/contract thay đổi.

## Tiêu chí nghiệm thu khi triển khai

- Admin-only account provisioning/deletion theo contract được chốt; Owner không có quyền quản trị hệ thống.
- OperationId đọc được tới trạng thái kết thúc; API chỉ xác nhận yêu cầu bền vững bằng **202 Accepted**, không nói downstream đã xóa xong.
- Event trùng không thực hiện side effect lần hai; lỗi transaction không để Inbox đánh dấu thành công khi nghiệp vụ chưa commit.
- Account xóa chỉ hoàn tất sau khi board-service xác nhận Project owned đã cascade và assignment cleanup ở Project Member-only đã hoàn tất. Membership ở các Project còn lại phải bị thu hồi trước khi Task unassign nền.
- Account `DELETING` phải bị chặn bởi authorization path trước khi `202` xác nhận yêu cầu xóa; không dựa riêng vào event RabbitMQ đến sau.
