# 02 — board-service: Project, Membership, Invitation, Board và Column

**Sở hữu mục tiêu:** toàn bộ ranh giới workspace Kỳ 1 trong **board_db**. Kỳ 1 không tách deployable **project-service**.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/board-service/docs/api.md), [ghi chú Account deletion Kỳ 2](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **BoardController** hiện khai báo Board-first API: tạo/list/đọc Board, tạo và reorder Column.
- **boards** hiện có **owner_id** trực tiếp, không có **project_id**; **board_key** đang unique toàn bảng và code cũ viết hoa.
- Project, Membership và Invitation entity/adapter chưa có. Source có bảng giữ `membership_version` + `authorization_version`, snapshot-sync job/Redis worker seam có liên kết `operationId`, và assignment-cleanup completion boundary; worker mặc định tắt, chưa được gọi bởi mutation API, status endpoint chưa có.
- Account/Project cascade không thuộc runtime Kỳ 1; membership assignment cleanup vẫn là trách nhiệm nền liên service khi Member rời Project.

## Việc cần làm

- [ ] Thiết kế/migrate Project và Membership; một Project có đúng một Owner, Member chỉ được tạo sau khi Invitation được Accept.
- [ ] Tạo Project chỉ từ Account nghiệp vụ `ACTIVE` và identity context đáng tin cậy do Gateway chuyển tiếp; không nhận actor ID do client gửi. Ghi Project + Owner membership trong cùng transaction.
- [ ] Thêm Invitation cho tài khoản đã có; Owner dùng User directory API để chọn UUID và list lời mời của Project để theo dõi/hủy; chốt lỗi, reject/cancel/expiry contract và transaction Accept trước khi triển khai API.
- [ ] Validate invited User tồn tại/ACTIVE ở board-service khi tạo Invitation và khi Accept; do internal REST chưa bảo mật, giữ nguồn validation như integration TODO trước khi bật route.
- [ ] Thêm quan hệ Board→Project; chuẩn hóa Board key chữ thường, unique trong Project.
- [ ] Chuyển quyền truy cập từ Board owner trực tiếp sang Project membership. Owner/Member dùng quyền công việc chung; Owner có thêm Board, membership và lifecycle Project/Board.
- [ ] Implement route Project/Board theo [API chung](../api/kanban-api.md); payload/error có bản draft per-service nhưng chỉ dùng field/behavior đã chốt, các mục proposal/open cần duyệt trước khi đóng OpenAPI.
- [ ] Chốt transition ACTIVE, COMPLETED, CANCELLED trước khi thêm API đổi trạng thái.
- [x] Thêm persistence seam giữ `membership_version` generation và `authorization_version` trạng thái quyền theo `(project_id,user_id)`; cần nối tăng version vào Accept/remove/role mutation.
- [x] Thêm snapshot-sync job, Redis writer/worker dùng `authorization_version` cho CAS và dedupe; job cũ bị version mới hơn vượt qua được ghi `SUPERSEDED`; worker mặc định tắt và cần nối mutation, Outbox cùng durable job trong một transaction.
- [ ] Khi Member rời/bị gỡ, Redis apply snapshot mới là mốc revoke. Nếu chưa apply, trả `202` + `operationId`/`statusUrl`; assignment cleanup chạy độc lập và không trì hoãn revoke completion.
- [ ] Ghi cleanup request với `membershipVersion`; không xóa Invitation/Project cascade trong Kỳ 1. Không cho System Admin có quyền Project mặc định.
- [ ] Cập nhật API/database docs riêng theo schema và contract đã duyệt.

## Tiêu chí nghiệm thu khi triển khai

- Project là nơi duy nhất cấp membership boundary; Admin hệ thống không có quyền tự động vào Project.
- Project creation yêu cầu Account nghiệp vụ `ACTIVE` đã được xác thực tại Gateway; người tạo là Owner duy nhất và Project/Membership được commit cùng transaction. `SYSTEM_ADMIN`-only không có quyền Project.
- Invitation PENDING không cấp quyền; Accept và Membership creation nguyên tử trong board-service transaction.
- Gỡ Membership có hiệu lực tại Redis apply; assignment cleanup chỉ xóa đúng generation và có thể retry độc lập.
- Kỳ 1 không có Account/Project deletion operation, cascade coordinator hoặc Project graph deletion.
- Project/Membership snapshot là nguồn quyền cho Task; Redis lỗi hoặc snapshot không xác minh được thì fail closed.
