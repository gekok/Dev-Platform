# task-service API

**Sở hữu:** Card và Comment trong task_db. Contract mục tiêu nằm ở [Kanban API](../../../docs/api/kanban-api.md); quyết định nguồn quyền ở [architecture decisions](../../../docs/project/architecture-decisions.md).

Hướng dẫn Shared: [Shared README](../../../shared/README.md).

## Route được khai báo trong source

CardController hiện dùng Project-scoped path và gọi ProjectAuthorizationGuard trước thao tác. Đây là mapping của controller; các inbound port chưa có implementation hoàn chỉnh, Gateway chưa xác thực/chuyển tiếp identity và Board/Column-to-Project validation vẫn là integration TODO.

| Method | Route | Request/query | Response |
|---|---|---|---|
| POST | /api/v1/projects/{projectId}/boards/{boardId}/cards | X-User-Id; columnId, title, description?, assigneeId?, dueDate? | 201 CardResponse |
| GET | /api/v1/projects/{projectId}/boards/{boardId}/cards | X-User-Id; columnId? | 200 CardResponse[] |
| PATCH | /api/v1/projects/{projectId}/cards/{cardId}/move | X-User-Id; toColumnId, toPosition | 200 CardResponse |
| PATCH | /api/v1/projects/{projectId}/cards/{cardId}/assignee | X-User-Id; assigneeId: UUID|null | 200 CardResponse |
| POST | /api/v1/projects/{projectId}/cards/{cardId}/comments | X-User-Id; content, mentionedUserIds? | 201 CommentResponse |

X-User-Id hiện là header bắt buộc ở controller, nhưng chưa phải danh tính đáng tin cậy: Gateway chưa có auth filter để ghi đè header client gửi. Mọi route được bảo vệ bằng snapshot đọc trực tiếp từ Redis; snapshot thiếu, hỏng hoặc Redis lỗi thì trả 503 AUTHORIZATION_SNAPSHOT_UNAVAILABLE, membership không hợp lệ thì trả 403 PROJECT_ACCESS_DENIED.

CardResponse gồm id, projectId, boardId, columnId, title, status, position, assigneeId. CommentResponse gồm id, cardId, authorId, content, createdAt. List hiện trả toàn bộ kết quả; cursor chưa được nối vào controller.

## Contract mục tiêu và phần chưa có trong source

Contract chung còn có GET /api/v1/projects/{projectId}/cards/{cardId} để đọc một Card. Route này chưa được khai báo trong controller hiện tại. Mọi route Card phải giữ projectId trong URL để giới hạn đọc/ghi theo cards.project_id; client không được gửi membershipVersion, Task lấy version của assignee từ Redis snapshot.

Board/Column truyền khi tạo hoặc move Card phải được xác minh thuộc Project trong path. Internal REST đang hoãn vì chưa có bảo mật service-to-service, nên không được xem riêng projectId do caller gửi là bằng chứng cho quan hệ Board–Project. Đây là integration TODO trước khi bật luồng end-to-end.

Phân trang mục tiêu theo từng Column: cursor opaque, mặc định 5, tối đa 20, position DESC, id DESC; controller hiện chưa có limit/cursor. Dữ liệu thay đổi giữa các trang có thể cần client dedupe theo cardId và hiển thị banner yêu cầu reload.

## Cleanup assignment của Membership

Khi board-service thu hồi Membership, request envelope mang `aggregateVersion=authorizationVersion`; payload mang projectId, userId, boardIds, operationId, membershipVersion. Task ghi Inbox, generation fence và job bền vững (gồm source aggregate version) trong một transaction trước ACK; worker chỉ gỡ assignment cùng Project/User/membershipVersion và gửi completion qua Outbox với source aggregate version. Cleanup là tác vụ nền độc lập, không giữ quyền truy cập lại và không chặn operation revoke hoàn tất.

Consumer listener/ACK wiring và migration chưa được nối; scheduler cleanup mặc định tắt. Đây là Member assignment cleanup, không phải Account deletion hoặc Project cascade. Account deletion/cascade thuộc Kỳ 2.

## Lỗi source hiện có

| Tình huống | HTTP | Code |
|---|---:|---|
| Input/domain validation | 400 | BAD_REQUEST |
| Không có quyền Project | 403 | PROJECT_ACCESS_DENIED |
| Không đọc/giải mã được snapshot | 503 | AUTHORIZATION_SNAPSHOT_UNAVAILABLE |

Các use case inbound chưa có implementation, nên bảng trên mô tả mapping controller/handler, không khẳng định các thao tác CRUD đã chạy end-to-end.
