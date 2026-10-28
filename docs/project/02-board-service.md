# 02 — board-service: Project, Membership, Invitation, Board và Column

**Sở hữu mục tiêu:** toàn bộ ranh giới workspace Kỳ 1 trong **board_db**. Kỳ 1 không tách deployable **project-service**.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/board-service/docs/api.md), [skeleton cascade](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **BoardController** hiện khai báo Board-first API: tạo/list/đọc Board, tạo và reorder Column.
- **boards** hiện có **owner_id** trực tiếp, không có **project_id**; **board_key** đang unique toàn bảng và code cũ viết hoa.
- Project, Membership, Invitation entity/adapter chưa có.
- Cascade mới có **ProjectDeletionCoordinator**, **ProjectDeletionDataPort** và operation entity/repository; chưa có implementation.

## Việc cần làm

- [ ] Thiết kế/migrate Project và Membership; một Project có đúng một Owner, Member chỉ được tạo sau khi Invitation được Accept.
- [ ] Thêm Invitation cho tài khoản đã có; chốt payload, lỗi, reject/cancel/expiry contract và transaction Accept trước khi triển khai API.
- [ ] Thêm quan hệ Board→Project; chuẩn hóa Board key chữ thường, unique trong Project.
- [ ] Chuyển quyền truy cập từ Board owner trực tiếp sang Project membership. Owner/Member dùng quyền công việc chung; Owner có thêm Board, membership và lifecycle Project/Board.
- [ ] Implement route Project/Board theo [API chung](../api/kanban-api.md); route map hiện chỉ chốt hướng, payload/error vẫn còn mở.
- [ ] Chốt transition ACTIVE, COMPLETED, CANCELLED trước khi thêm API đổi trạng thái.
- [ ] Implement **ProjectDeletionDataPort**/coordinator: tìm Project owned/member, hủy Invitation PENDING khi account bị xóa, tạo Project operation, khóa ghi, điều phối Task/Notification, rồi xóa Project graph sau đủ xác nhận.
- [ ] Khi Member rời/bị gỡ hoặc Account bị xóa, chờ Task unassign hoàn tất trước khi gỡ Membership; giữ Project họ chỉ là Member.
- [ ] Cập nhật API/database docs riêng theo schema và contract đã duyệt.

## Tiêu chí nghiệm thu khi triển khai

- Project là nơi duy nhất cấp membership boundary; Admin hệ thống không có quyền tự động vào Project.
- Invitation PENDING không cấp quyền; Accept và Membership creation nguyên tử trong board-service transaction.
- Không xóa Project thuộc Owner trước khi cleanup dữ liệu downstream hoàn tất.
- Operation Account có một operationId; mỗi Project cascade có một projectDeletionId; retry dùng cùng operation/job identity phù hợp.
