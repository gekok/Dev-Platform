# board-service API

**Sở hữu mục tiêu Kỳ 1:** Project, Membership, Invitation, Board và Column trong board_db; không tách project-service. Nguồn hợp đồng chung: [Kanban API](../../../docs/api/kanban-api.md), [architecture decisions](../../../docs/project/architecture-decisions.md).

Hướng dẫn Shared: [Shared README](../../../shared/README.md).

## Route đang khai báo trong source

BoardController vẫn là skeleton Board-first. Các inbound use case chưa có implementation và Project/Membership/Invitation chưa có controller runtime.

| Method | Route | Header/body | Response |
|---|---|---|---|
| POST | /api/v1/boards | X-User-Id; name, key, columns? | 201 BoardResponse |
| GET | /api/v1/boards | X-User-Id | 200 BoardResponse[] |
| GET | /api/v1/boards/{boardId} | X-User-Id | 200 BoardResponse |
| POST | /api/v1/boards/{boardId}/columns | X-User-Id; name | 201 ColumnResponse |
| PATCH | /api/v1/boards/{boardId}/columns/order | X-User-Id; orderedColumnIds | 200 ColumnResponse[] |

X-User-Id là identity stub, chưa được Gateway xác thực và ghi đè. Board mapping hiện lưu owner_id trực tiếp, chưa có project_id; không xem các route này là Project membership authorization đã hoạt động.

## API mục tiêu thuộc board-service

Các route dưới đây là hợp đồng mục tiêu, chưa có Project/Membership/Invitation controller trong source:

| Method | Route | Ý nghĩa |
|---|---|---|
| GET, POST | /api/v1/projects | Tạo/list Project; tạo Project và Owner membership cùng transaction |
| GET | /api/v1/projects/{projectId} | Chi tiết Project |
| GET | /api/v1/projects/{projectId}/members | Danh sách Member |
| POST | /api/v1/projects/{projectId}/invitations | Owner mời Account đã có |
| GET | /api/v1/projects/invitations | Inbox Invitation của identity hiện tại |
| GET | /api/v1/projects/{projectId}/invitations | Owner xem lời mời trong Project |
| POST | /api/v1/projects/{projectId}/invitations/{inviteId}/accept | Recipient Accept; tạo Membership cùng transaction |
| POST | /api/v1/projects/{projectId}/invitations/{inviteId}/reject | Recipient Reject |
| DELETE | /api/v1/projects/{projectId}/invitations/{inviteId} | Owner hủy Invitation PENDING |
| DELETE | /api/v1/projects/{projectId}/members/{userId} | Owner gỡ Member hoặc Member tự rời |

Invitation chỉ dành cho Account đã có trong hệ thống; Kỳ 1 không mời email ngoài hệ thống. Invitation PENDING chưa cấp quyền. Người nhận mới được Accept/Reject. Owner không thể tự gỡ mình; nếu không muốn tiếp tục Project thì dùng trạng thái Project đã được chốt, theo lifecycle contract.

Project access dựa trên Membership; System Admin không tự có quyền Project. Khi tạo Project, actor lấy từ identity tin cậy do Gateway xác thực, không nhận ownerId từ body. Account cần ACTIVE.

Khi Membership bị gỡ/rời, board-service ghi source change, Outbox và snapshot-sync job trong transaction. `authorizationVersion` tăng theo thay đổi quyền và là version dùng trong envelope, Redis CAS, job dedupe; `membershipVersion` giữ generation để Task cleanup đúng assignment. `operationId` được lưu trên snapshot-sync job để liên kết trạng thái xử lý. Redis apply snapshot mới là mốc revoke. Nếu sync còn pending, API trả 202 cùng operationId/statusUrl. Task assignment cleanup chạy nền riêng và không trì hoãn revoke completion.

## Lỗi và phần chưa hoàn chỉnh

Lỗi Invitation/Membership và expiry/cancel đã có một số proposal trong contract chung nhưng chưa được duyệt đầy đủ. Internal REST để board-service xác minh Account ACTIVE chưa triển khai vì chưa có bảo mật service-to-service; không bật luồng chỉ dựa trên UUID do client gửi.

Redis snapshot writer và sync worker hiện là seam, mặc định tắt; chưa có Project/Membership mutation port hoặc status endpoint để ghi job và tra cứu operation. `operationId` đã có chỗ lưu trong target sync job; route/access policy và response status cụ thể vẫn là integration TODO. Snapshot reader của Task fail closed khi thiếu/lỗi. Account deletion/Project cascade không có trong Kỳ 1; xem [ghi chú Kỳ 2](../../../docs/project/account-deletion-skeleton-guide.md).
