# task-service API

**Sở hữu:** Card và Comment trong task_db. Contract mục tiêu nằm ở [Kanban API](../../../docs/api/kanban-api.md); quyết định nguồn quyền ở [architecture decisions](../../../docs/project/architecture-decisions.md).

Hướng dẫn Shared: [Shared README](../../../shared/README.md).

## Route được khai báo trong source

CardController hiện dùng Project-scoped path và gọi ProjectAuthorizationGuard trước thao tác. Tạo/list Card, đọc/tạo Comment và thuật toán move đã có use case; gán có fallback trả lỗi 503. Tạo/move mặc định trả 503 do chưa có nguồn xác minh Column thật. Gateway chưa xác thực/chuyển tiếp identity và Board/Column-to-Project validation vẫn là integration TODO.

| Method | Route | Request/query | Response |
|---|---|---|---|
| POST | /api/v1/projects/{projectId}/boards/{boardId}/cards | X-User-Id; columnId, title, description?, assigneeId?, dueDate? | 201 với nguồn xác minh Column; mặc định 503 |
| GET | /api/v1/projects/{projectId}/boards/{boardId}/cards | X-User-Id; columnId?, limit?, cursor? | 200 `{items, nextCursor}` |
| PATCH | /api/v1/projects/{projectId}/cards/{cardId}/move | X-User-Id; toColumnId, toPosition | 200 CardResponse khi thành công; 503 CARD_OPERATION_UNAVAILABLE khi chưa có nguồn xác minh Column |
| PATCH | /api/v1/projects/{projectId}/cards/{cardId}/assignee | X-User-Id; assigneeId: UUID|null | 503 CARD_OPERATION_UNAVAILABLE sau khi kiểm tra quyền với fallback hiện tại |
| POST | /api/v1/projects/{projectId}/cards/{cardId}/comments | X-User-Id; content, mentionedUserIds? | 201 CommentResponse |
| GET | /api/v1/projects/{projectId}/cards/{cardId}/comments | X-User-Id | 200 CommentResponse[] theo id tăng dần |

X-User-Id hiện là header bắt buộc ở controller, nhưng chưa phải danh tính đáng tin cậy: Gateway chưa có auth filter để ghi đè header client gửi. Mọi route được bảo vệ bằng snapshot đọc trực tiếp từ Redis; snapshot thiếu, hỏng hoặc Redis lỗi thì trả 503 AUTHORIZATION_SNAPSHOT_UNAVAILABLE, membership không hợp lệ thì trả 403 PROJECT_ACCESS_DENIED.

CardResponse gồm id, projectId, boardId, columnId, title, status, position, assigneeId. CommentResponse gồm id, cardId, authorId, content, createdAt. List dùng keyset theo position DESC, id DESC; limit mặc định 5, khoảng hợp lệ 1–20. Cursor tạm thời ràng buộc project, board, column và sort; sai scope hoặc định dạng trả 400 BAD_REQUEST. `nextCursor` null là trang cuối. Đây là wire tạm thời, chưa phải contract đã duyệt.

Comment kiểm tra Card thuộc Project và actor qua guard; POST yêu cầu content không rỗng, mỗi mention phải có Account và membership ACTIVE qua snapshot. Không lưu mention hoặc phát notification khi contract chưa được chốt. `X-User-Id` chưa là identity đáng tin cậy cho đến khi Gateway xác thực và ghi đè header. Chưa xác nhận E2E với Redis/Gateway.

### Ví dụ request/response

Các ví dụ này mô tả contract của code, không phải log nghiệm thu. Các request yêu cầu `X-User-Id` là UUID; tạo/move còn cần nguồn xác minh Column, có thể cấu hình mock local như bên dưới.

`POST /api/v1/projects/1/boards/2/cards` với body:

```json
{"columnId":3,"title":"Prepare release","description":null,"assigneeId":null,"dueDate":null}
```

Khi được phép và lưu thành công, response `201` có dạng `{"id":42,"projectId":1,"boardId":2,"columnId":3,"title":"Prepare release","status":"TODO","position":0,"assigneeId":null}`. Position thực tế phụ thuộc Card hiện có.

`GET /api/v1/projects/1/boards/2/cards?columnId=3&limit=5` trả `200` với `{ "items": [...], "nextCursor": null }`; truyền `nextCursor` ở query `cursor` để lấy trang tiếp theo khi giá trị khác null.

`PATCH /api/v1/projects/1/cards/42/move` với `{"toColumnId":4,"toPosition":0}` trả `200` và Card đã cập nhật khi nguồn xác minh Column chấp nhận cả source và destination.

`POST /api/v1/projects/1/cards/42/comments` với `{"content":"Please review","mentionedUserIds":[]}` trả `201` với `id`, `cardId`, `authorId`, `content`, `createdAt`. GET cùng path trả mảng Comment theo id tăng dần. Card không tồn tại trong Project trả `404 CARD_NOT_FOUND`; content rỗng trả `400 BAD_REQUEST`.

## Contract mục tiêu và phần chưa có trong source

Contract chung còn có GET /api/v1/projects/{projectId}/cards/{cardId} để đọc một Card. Route này chưa được khai báo trong controller hiện tại. Mọi route Card phải giữ projectId trong URL để giới hạn đọc/ghi theo cards.project_id; client không được gửi membershipVersion, Task lấy version của assignee từ Redis snapshot.

Board/Column truyền khi tạo hoặc move Card phải được xác minh thuộc Project trong path. Internal REST đang hoãn vì chưa có bảo mật service-to-service, nên không được xem riêng projectId do caller gửi là bằng chứng cho quan hệ Board–Project. Đây là integration TODO trước khi bật luồng end-to-end.

Move yêu cầu `toPosition`, dùng chỉ số hiển thị bắt đầu từ 0 theo thứ tự position DESC/id DESC, sau khi gỡ Card khỏi vị trí cũ. Tạo Card từ chối title rỗng hoặc dài quá 500 ký tự sau khi trim. Thuật toán khóa theo Project/Board bằng SQL Server application lock và cập nhật cả hai Column trong transaction; mọi đường ghi position phải dùng cùng khóa trước khi bật tích hợp. Chưa kiểm tra đồng thời trên SQL Server hoặc tích hợp adapter Column; không phát event chưa được duyệt.

### Mock Column cho local

Implementation giả lập trong package `dev.platform.task.mock` chỉ bật khi profile `mock` được chọn rõ ràng. Cấu hình `task.mock.columns` là danh sách `projectId:boardId:columnId` phân cách bằng dấu phẩy, ví dụ chạy từ thư mục task-service với `mvn spring-boot:run '-Dspring-boot.run.profiles=mock' '-Dspring-boot.run.arguments=--task.mock.columns=1:2:3,1:2:4'`. Danh sách rỗng hoặc quan hệ không được liệt kê bị từ chối. Không bật profile này trong môi trường triển khai. Mock chỉ thay xác minh quan hệ Column; guard Redis và việc ghi SQL Server vẫn hoạt động như cấu hình hiện có. Kết quả mock không chứng minh quan hệ Board/Column thật. Interface nằm trong application port của task-service để adapter thật triển khai sau này; cấu hình fail closed tạm thời nằm cùng mock.

Phân trang theo Column hỗ trợ `columnId`; dữ liệu thay đổi giữa các trang có thể cần client dedupe theo cardId và hiển thị banner yêu cầu reload. Các inbound port khác vẫn chưa hoàn chỉnh, nên chưa xác nhận service chạy end-to-end.

## Cleanup assignment của Membership

Khi board-service thu hồi Membership, request envelope mang `aggregateVersion=authorizationVersion`; payload mang projectId, userId, boardIds, operationId, membershipVersion. Task ghi Inbox, generation fence và job bền vững (gồm source aggregate version) trong một transaction trước ACK; worker chỉ gỡ assignment cùng Project/User/membershipVersion và gửi completion qua Outbox với source aggregate version. Cleanup là tác vụ nền độc lập, không giữ quyền truy cập lại và không chặn operation revoke hoàn tất.

Consumer listener/ACK wiring và migration chưa được nối; scheduler cleanup mặc định tắt. Đây là Member assignment cleanup, không phải Account deletion hoặc Project cascade. Account deletion/cascade thuộc Kỳ 2.

## Lỗi source hiện có

| Tình huống | HTTP | Code |
|---|---:|---|
| Input/domain validation | 400 | BAD_REQUEST |
| Không có quyền Project | 403 | PROJECT_ACCESS_DENIED |
| Không đọc/giải mã được snapshot | 503 | AUTHORIZATION_SNAPSHOT_UNAVAILABLE |
| Use case Card chưa triển khai | 503 | CARD_OPERATION_UNAVAILABLE |
| Card không tồn tại trong Project | 404 | CARD_NOT_FOUND |

Bảng trên mô tả code và mapping hiện có; chưa có bằng chứng các thao tác đã chạy end-to-end với Gateway, Redis và adapter Column thật.
