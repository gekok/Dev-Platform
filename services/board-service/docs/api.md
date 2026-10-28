# board-service API — runtime Board-first và mục tiêu Project-first

**Vai trò mục tiêu:** sở hữu Project, Membership, Invitation, Board và Column trong `board_db`; Kỳ 1 không có deployable `project-service` riêng. Quyết định chung: [architecture decisions](../../../docs/project/architecture-decisions.md); API mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

## Controller hiện có

`BoardController` hiện ánh xạ các route Board-first dưới đây. Chúng mô tả runtime hiện tại, không phải API đích:

| Method | Route hiện khai báo | Hành vi |
|---|---|---|
| POST | `/api/v1/boards` | Tạo Board |
| GET | `/api/v1/boards` | Liệt kê Board theo User hiện tại |
| GET | `/api/v1/boards/{boardId}` | Lấy Board kèm Columns |
| POST | `/api/v1/boards/{boardId}/columns` | Tạo Column |
| PATCH | `/api/v1/boards/{boardId}/columns/order` | Sắp xếp Column |

Controller hiện đọc `X-User-Id` làm identity stub. Route tạo Board hiện nhận `name`, `key`, tùy chọn `columns`. Controller chưa kiểm tra Project membership vì Project/Membership chưa có model/adapter.

## API mục tiêu đã thống nhất

- Project là ranh giới truy cập; Board thuộc một Project.
- Owner tạo Project và là Owner duy nhất. Owner mời tài khoản hiện có bằng Invitation `PENDING`; Accept mới tạo Membership.
- Owner và Member thao tác Card/Column/comment theo quyền chung; chỉ Owner tạo Board, quản lý member và lifecycle Project/Board.
- Route Project-first nằm trong [kanban-api.md](../../../docs/api/kanban-api.md). Không suy ra payload/error cuối cùng từ route map.
- Board key mục tiêu dùng chữ thường và unique trong Project; không dùng ràng buộc unique toàn DB hiện tại làm contract mới.

## Cascade deletion boundary hiện có

`ProjectDeletionCoordinator` và `ProjectDeletionDataPort` là interface boundary cho việc tìm Project/Board, tạo write barrier, cleanup membership và xóa Project graph. Chưa có implementation adapter, Project entity, consumer hoặc API. Chi tiết hiện trạng: [account deletion skeleton guide](../../../docs/project/account-deletion-skeleton-guide.md).

## Còn mở

Payload và lỗi Invitation/Membership, các route list/reject/cancel/expiry Invitation, status transition của Project/Board, operation status/event response và notification cleanup scope chưa được chốt đầy đủ. Xem mục “Cần bàn tiếp” trong architecture decisions.
