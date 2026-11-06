# task-service API

**Sở hữu:** Card và Comment trong task_db. Contract mục tiêu nằm ở [Kanban API](../../../docs/api/kanban-api.md); quyết định nguồn quyền ở [architecture decisions](../../../docs/project/architecture-decisions.md).

Hướng dẫn Shared: [Shared README](../../../shared/README.md).

## Route được khai báo trong source

CardController hiện dùng Project-scoped path và gọi ProjectAuthorizationGuard trước thao tác. Tạo/list/đọc chi tiết Card, move, gán và đọc/tạo Comment đã có use case. Tạo/move mặc định trả 503 do chưa có nguồn xác minh Column thật. Mọi đường gán Card (PATCH assignee hoặc POST create có `assigneeId`) mặc định fail-closed và trả 503 `CARD_OPERATION_UNAVAILABLE` trước khi đọc danh tính từ `X-User-Id`; chỉ bật khi Gateway xác thực người dùng, ghi đè header và chặn truy cập trực tiếp tới task-service. Gateway chưa xác thực/chuyển tiếp identity và Board/Column-to-Project validation vẫn là integration TODO.

| Method | Route | Request/query | Response |
|---|---|---|---|
| POST | /api/v1/projects/{projectId}/boards/{boardId}/cards | X-User-Id; columnId, title, description?, assigneeId?, dueDate? | 201 khi validator Column sẵn sàng; 503 CARD_OPERATION_UNAVAILABLE nếu chưa có validator hoặc request có assigneeId khi trusted identity chưa bật |
| GET | /api/v1/projects/{projectId}/boards/{boardId}/cards | X-User-Id; columnId required, limit?, cursor? | 200 `{items, nextCursor}` |
| GET | /api/v1/projects/{projectId}/cards/{cardId} | X-User-Id | 200 CardDetailResponse; 404 nếu Card không tồn tại trong Project |
| PATCH | /api/v1/projects/{projectId}/cards/{cardId}/move | X-User-Id; toColumnId, toPosition | 200 CardResponse khi thành công; 503 CARD_OPERATION_UNAVAILABLE khi chưa có nguồn xác minh Column |
| PATCH | /api/v1/projects/{projectId}/cards/{cardId}/assignee | X-User-Id; assigneeId: UUID|null | 503 `CARD_OPERATION_UNAVAILABLE` khi trusted Gateway identity chưa bật; sau khi tích hợp, 200 CardResponse hoặc 403 nếu assignee không hợp lệ/generation đã có fence |
| POST | /api/v1/projects/{projectId}/cards/{cardId}/comments | X-User-Id; content, mentionedUserIds? | 201 CommentResponse |
| GET | /api/v1/projects/{projectId}/cards/{cardId}/comments | X-User-Id | 200 CommentResponse[] theo id tăng dần |

### WebSocket Board (#24) — service-side runtime có readiness gate

Contract mục tiêu giữ route và payload đã duyệt:

```text
WS /ws/boards/{boardId}
```

```json
{"event":"card.moved","cardId":100,"toColumnId":11,"toPosition":0}
{"event":"card.created","cardId":101,"columnId":10}
{"event":"comment.added","cardId":100,"commentId":900}
```

Task đăng ký `WS /ws/boards/{boardId}` và handler chỉ khi `TASK_TRUSTED_GATEWAY_IDENTITY_ENABLED`, `TASK_BOARD_PROJECT_LOOKUP_READY`, `TASK_GATEWAY_WEBSOCKET_PROXY_READY` và `TASK_BOARD_WEBSOCKET_ENABLED` đều là `true`. Cả bốn mặc định là `false`; cần đăng ký adapter `BoardProjectLookupPort` dùng Board đã lưu trước khi bật. Cờ readiness là cấu hình triển khai, không phải bằng chứng Gateway/Board đã tích hợp.

Handshake yêu cầu identity `X-User-Id` do Gateway tin cậy chuyển tiếp; Task tự tra Board→Project và kiểm tra Account/membership trong snapshot. Session được kiểm tra lại định kỳ và ngay trước mỗi lần gửi; lookup hoặc snapshot lỗi đóng session. Event được phát sau transaction commit và chỉ fanout tới session cùng Board trong cùng Task instance. Đây là best-effort: không persistence/retry/replay hoặc bảo đảm nhận đủ/thứ tự; client refetch REST sau reconnect. Payload giữ nguyên event names và fields đã duyệt, không gồm Card title hay Comment body. Message #24 không phải durable RabbitMQ event của #25. Xem [kế hoạch realtime](realtime-events-proposal.md).

X-User-Id hiện là header bắt buộc ở controller, nhưng chưa phải danh tính đáng tin cậy: Gateway chưa có auth filter để ghi đè header client gửi. Mọi route được bảo vệ bằng snapshot đọc trực tiếp từ Redis; snapshot thiếu, hỏng hoặc Redis lỗi thì trả 503 AUTHORIZATION_SNAPSHOT_UNAVAILABLE, membership không hợp lệ thì trả 403 PROJECT_ACCESS_DENIED. Các đường gán (PATCH assignee và POST có `assigneeId`) kiểm tra cờ trusted Gateway identity trước guard và từ chối mặc định, không dùng UUID client gửi làm actor.

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

GET `/api/v1/projects/{projectId}/cards/{cardId}` đã được khai báo qua inbound port; persistence query đồng thời lọc theo `project_id`, nên Card sai Project trả `404 CARD_NOT_FOUND`. Response chi tiết gồm `description` và `dueDate` ngoài các field Card cơ bản. Mọi route Card giữ projectId trong URL để giới hạn đọc/ghi theo `cards.project_id`; client không được gửi membershipVersion, Task lấy version của assignee từ Redis snapshot.

Board/Column truyền khi tạo hoặc move Card phải được xác minh thuộc Project trong path. Internal REST đang hoãn vì chưa có bảo mật service-to-service, nên không được xem riêng projectId do caller gửi là bằng chứng cho quan hệ Board–Project. Đây là integration TODO trước khi bật luồng end-to-end.

Move yêu cầu `toPosition`, dùng chỉ số hiển thị bắt đầu từ 0 theo thứ tự position DESC/id DESC, sau khi gỡ Card khỏi vị trí cũ. Tạo Card từ chối title rỗng hoặc dài quá 500 ký tự sau khi trim. Thuật toán khóa theo Project/Board bằng SQL Server application lock và cập nhật cả hai Column trong transaction; mọi đường ghi position phải dùng cùng khóa trước khi bật tích hợp. Chưa kiểm tra đồng thời trên SQL Server hoặc tích hợp adapter Column; không phát durable event chưa được duyệt.

Kiểm tra local với SQL Server thật: `mvn -pl services/task-service -am '-Dtask.sqlserver.integration=true' test`. Bộ kiểm tra gồm tạo Card với Column mock rồi rollback, từ chối ghi khi không có validator và hai transaction cạnh tranh cùng khóa Board. Kiểm tra khóa không thay thế kiểm tra hai request move đồng thời hoặc tích hợp Column thật. Test SQL Server chỉ chạy khi bật property trên và dùng cấu hình datasource local; test tạo Card rollback nhưng SQL Server vẫn có thể tăng giá trị identity.

### Mock Column cho local

Profile này chỉ hỗ trợ phát triển local cho Card create/move; nó không chứng minh Board/Column thuộc Project thật và không đáp ứng trusted Board lookup cho WebSocket #24.

Implementation giả lập trong package `dev.platform.task.mock` chỉ bật khi profile `mock` được chọn rõ ràng. Cấu hình `task.mock.columns` là danh sách `projectId:boardId:columnId` phân cách bằng dấu phẩy, ví dụ chạy từ thư mục task-service với `mvn spring-boot:run '-Dspring-boot.run.profiles=mock' '-Dspring-boot.run.arguments=--task.mock.columns=1:2:3,1:2:4'`. Danh sách rỗng hoặc quan hệ không được liệt kê bị từ chối. Không bật profile này trong môi trường triển khai. Mock chỉ thay xác minh quan hệ Column; guard Redis và việc ghi SQL Server vẫn hoạt động như cấu hình hiện có. Kết quả mock không chứng minh quan hệ Board/Column thật. Interface nằm trong application port của task-service để adapter thật triển khai sau này; cấu hình fail closed tạm thời nằm cùng mock.

Phân trang theo Column hỗ trợ `columnId`; dữ liệu thay đổi giữa các trang có thể cần client dedupe theo cardId và hiển thị banner yêu cầu reload. Cursor hiện tại là wire tạm thời; contract lỗi và stale-cursor chưa được chốt. Card detail GET scope theo Project đã có trong source. Các use case Card/Comment có implementation trong source, nhưng chưa xác nhận E2E qua Gateway, Redis và adapter Board/Column thật.

## Cleanup assignment của Membership

Khi board-service thu hồi Membership, request envelope mang `aggregateVersion=authorizationVersion`; payload mang projectId, userId, boardIds, operationId, membershipVersion. Task ghi Inbox, generation fence và job bền vững (gồm source aggregate version) trong một transaction trước ACK; worker chỉ gỡ assignment cùng Project/User/membershipVersion và gửi completion qua Outbox với source aggregate version. Cleanup là tác vụ nền độc lập, không giữ quyền truy cập lại và không chặn operation revoke hoàn tất.

Assignment khóa Project/User trước khi khóa Card, lưu membership generation của assignee và từ chối generation đã có fence. Worker rollback batch khi xử lý hoặc ghi completion thất bại; lịch retry được ghi trong transaction riêng sau rollback. Source snapshot hiện không có cơ chế xác minh độ mới độc lập; stale snapshot và listener/ACK qua broker chưa được xác minh end-to-end.

Task listener cho event cleanup đã có durable queue/routing binding và ACK thủ công sau khi recorder transaction commit. Listener chỉ được đăng ký khi messaging, consumer và `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY` đều bật; cờ này mặc định false. Chỉ bật sau khi queue có bounded retry/backoff và DLQ phù hợp với hành vi NACK/requeue. Hiện chưa có broker retry/DLQ policy trong task-service và consumer phải giữ tắt; broker redelivery chưa được nghiệm thu. Versioned migration chưa có, cleanup scheduler mặc định tắt. Đây là Member assignment cleanup, không phải Account deletion hoặc Project cascade. Account deletion/cascade thuộc Kỳ 2.

## Lỗi source hiện có

| Tình huống | HTTP | Code |
|---|---:|---|
| Input/domain validation | 400 | BAD_REQUEST |
| Không có quyền Project | 403 | PROJECT_ACCESS_DENIED |
| Không đọc/giải mã được snapshot | 503 | AUTHORIZATION_SNAPSHOT_UNAVAILABLE |
| Use case Card chưa triển khai | 503 | CARD_OPERATION_UNAVAILABLE |
| Gán Card khi trusted Gateway identity chưa bật | 503 | CARD_OPERATION_UNAVAILABLE |
| Card không tồn tại trong Project | 404 | CARD_NOT_FOUND |

Bảng trên mô tả code và mapping hiện có; chưa có bằng chứng các thao tác đã chạy end-to-end với Gateway, Redis và adapter Column thật.
