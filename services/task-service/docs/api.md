# task-service API

**Sở hữu:** Card và Comment trong task_db. Contract mục tiêu nằm ở [Kanban API](../../../docs/api/kanban-api.md); quyết định nguồn quyền ở [architecture decisions](../../../docs/project/architecture-decisions.md).

Hướng dẫn Shared: [Shared README](../../../shared/README.md).

## Route được khai báo trong source

CardController hiện dùng Project-scoped path. Gateway xác thực JWT và ghi đè `X-User-Id` cho request đi qua Gateway, nhưng mọi route Card/Comment vẫn trả 503 `CARD_OPERATION_UNAVAILABLE` trước guard khi Task trusted identity gate mặc định tắt. Sau khi gate được bật an toàn, ProjectAuthorizationGuard kiểm tra Redis snapshot. Adapter Board lookup đã có; create/move vẫn trả 503 tới khi Column validator tin cậy sẵn sàng. Gateway + Task chưa được kiểm thử E2E; direct-service ingress hiện chưa bị chặn.

| Method | Route | Request/query | Response |
|---|---|---|---|
| POST | /api/v1/projects/{projectId}/boards/{boardId}/cards | X-User-Id; columnId, title, description?, assigneeId?, dueDate? | 201 khi identity tin cậy và validator Column sẵn sàng; 503 `CARD_OPERATION_UNAVAILABLE` nếu thiếu một trong hai |
| GET | /api/v1/projects/{projectId}/boards/{boardId}/cards | X-User-Id; columnId required, limit?, cursor? | 200 `{items, nextCursor}` sau khi identity tin cậy và authorization snapshot hợp lệ; nếu chưa, 503 |
| GET | /api/v1/projects/{projectId}/cards/{cardId} | X-User-Id | 200 CardDetailResponse; 404 nếu Card không tồn tại trong Project; 503 khi identity chưa tin cậy |
| PATCH | /api/v1/projects/{projectId}/cards/{cardId}/move | X-User-Id; toColumnId, toPosition | 200 CardResponse khi identity và validator Column sẵn sàng; nếu chưa, 503 `CARD_OPERATION_UNAVAILABLE` |
| PATCH | /api/v1/projects/{projectId}/cards/{cardId}/assignee | X-User-Id; assigneeId: UUID|null | 503 `CARD_OPERATION_UNAVAILABLE` khi identity chưa tin cậy; sau khi tích hợp, 200 CardResponse hoặc 403 nếu assignee không hợp lệ/generation đã có fence |
| POST | /api/v1/projects/{projectId}/cards/{cardId}/comments | X-User-Id; content, mentionedUserIds? | 201 CommentResponse khi identity tin cậy và snapshot hợp lệ; nếu chưa, 503 `CARD_OPERATION_UNAVAILABLE` |
| GET | /api/v1/projects/{projectId}/cards/{cardId}/comments | X-User-Id | 200 CommentResponse[] theo id tăng dần sau khi identity tin cậy; nếu chưa, 503 |

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

Browser trước hết gọi `POST /api/v1/realtime/boards/{boardId}/ws-ticket` qua Gateway với Bearer access token. Gateway trả `201`, `Cache-Control: no-store` và ticket sống 30 giây, gắn với Account/Board. Browser dùng ticket trong query khi kết nối `ws(s)://<gateway>/ws/boards/{boardId}?ticket=...`; không gửi access token trong WebSocket handshake. Gateway kiểm tra Origin theo allowlist, tiêu thụ ticket một lần bằng Redis `GETDEL`, xóa ticket và các credential/header bảo mật do client gửi, rồi chuyển `X-User-Id` đã xác minh tới Task.

Task đăng ký `WS /ws/boards/{boardId}` và handler chỉ khi `TASK_TRUSTED_GATEWAY_IDENTITY_ENABLED`, `TASK_BOARD_PROJECT_LOOKUP_READY`, `TASK_GATEWAY_WEBSOCKET_PROXY_READY` và `TASK_BOARD_WEBSOCKET_ENABLED` đều là `true`. Cả bốn mặc định là `false`. `BoardProjectLookupAdapter` dùng `HttpBoardStateAdapter` gọi `GET /internal/boards/{boardId}` với `X-Service-Token`; Board 404 được coi là không tồn tại, lỗi kết nối/token fail closed. Cờ readiness là cấu hình triển khai, không phải bằng chứng Gateway/Board đã tích hợp.

Handshake yêu cầu identity `X-User-Id` do Gateway tin cậy chuyển tiếp; Task tự tra Board→Project và kiểm tra Account/membership trong snapshot. Session được kiểm tra lại định kỳ và ngay trước mỗi lần gửi; lookup hoặc snapshot lỗi đóng session. Event được phát sau transaction commit và chỉ fanout tới session cùng Board trong cùng Task instance. Đây là best-effort: không persistence/retry/replay hoặc bảo đảm nhận đủ/thứ tự; client refetch REST sau reconnect. Payload giữ nguyên event names và fields đã duyệt, không gồm Card title hay Comment body. Message #24 không phải durable RabbitMQ event của #25. Xem [kế hoạch realtime](realtime-events-proposal.md).

`X-User-Id` hiện là header bắt buộc. Với request đi qua Gateway, giá trị client gửi bị xóa và thay bằng UUID `sub` đã xác minh. Task vẫn kiểm tra cờ trusted Gateway identity trước guard và từ chối mặc định; không bật cờ này khi client còn gọi trực tiếp được Task. Khi guard chạy, mỗi route kiểm tra snapshot Redis; snapshot thiếu, hỏng hoặc Redis lỗi trả 503 `AUTHORIZATION_SNAPSHOT_UNAVAILABLE`, membership không hợp lệ trả 403 `PROJECT_ACCESS_DENIED`.

CardResponse gồm id, projectId, boardId, columnId, title, status, position, assigneeId. CommentResponse gồm id, cardId, authorId, content, createdAt. List dùng keyset theo position DESC, id DESC; limit mặc định 5, khoảng hợp lệ 1–20. Cursor tạm thời ràng buộc project, board, column và sort; sai scope hoặc định dạng trả 400 BAD_REQUEST. `nextCursor` null là trang cuối. Đây là wire tạm thời, chưa phải contract đã duyệt.

Comment kiểm tra Card thuộc Project và actor qua guard; POST yêu cầu content không rỗng, mỗi mention phải có Account ACTIVE và Project Membership ACTIVE theo snapshot. `mentionedUserIds` được khử trùng lặp; tác giả bị loại khỏi recipient. Khi còn recipient, một event `comment.mentioned` được ghi cùng transaction với Comment. Chỉ lưu recipient IDs trong Outbox payload; không lưu lịch sử mention riêng hoặc gửi nội dung Comment. Cả POST và GET đều bị từ chối bằng 503 `CARD_OPERATION_UNAVAILABLE` khi trusted Gateway identity chưa bật. Chưa xác nhận E2E với Redis/Gateway.

## Durable Task notification events (#25)

Task ghi Notification intent vào `outbox_events` trong cùng SQL transaction với thay đổi Card/Comment, qua shared `OutboxEventWriter`. Lỗi ghi hoặc serialize Outbox làm rollback thay đổi nghiệp vụ. Hai event contract `schemaVersion=1`:

| Wire event | Phát khi | Recipient | Payload |
|---|---|---|---|
| `card.assigned` | Tạo Card với assignee khác actor, hoặc assignee đổi sang user khác; không phát khi unassign, tự gán, no-op hay chỉ đổi membership generation | Assignee mới | `projectId`, `boardId`, `cardId`, `recipientUserId` |
| `comment.mentioned` | Comment commit cùng Outbox sau khi mention IDs được xác thực | Mỗi recipient khác tác giả, một lần | `projectId`, `boardId`, `cardId`, `commentId`, `recipientUserIds` |

Envelope dùng `aggregateType=TASK_NOTIFICATION_INTENT`, `aggregateId=eventId`, `aggregateVersion=1`. Đây là event độc lập, không cam kết ordering theo Card; dedup dùng `(consumerName,eventId)`. Payload không có actor ID, membership version, Card title hoặc Comment body. Notification consumer ánh xạ hai event lần lượt sang `ASSIGNED` và `MENTIONED`; queue/DLQ do Notification sở hữu, còn retry policy dùng factory chung từ `messaging-support`. Consumer policy là một lần xử lý đầu cộng tối đa 3 lần retry cách cố định 5 giây; lỗi contract được gửi thẳng DLQ. Messaging/relay vẫn mặc định tắt và cần schema Outbox vật lý đã được xác minh trước khi bật.

### Ví dụ request/response

Các ví dụ này mô tả contract của code, không phải log nghiệm thu. Request qua Gateway nhận `X-User-Id` là UUID đã được xác thực và ghi đè; Task trusted identity mặc định tắt. Create/move cũng yêu cầu validator Column thật, nên các request đó trả 503 tới khi dependency được cấu hình.

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

Board/Column truyền khi tạo hoặc move Card phải được xác minh thuộc Project trong path. Secured `GET /internal/boards/{boardId}` với `X-Service-Token` hiện phục vụ WebSocket Board lookup; `ColumnRelationshipValidator` cho create/move chưa có adapter sẵn sàng. Không được xem riêng `projectId` do caller gửi là bằng chứng cho quan hệ Board–Project.

Move yêu cầu `toPosition`, dùng chỉ số hiển thị bắt đầu từ 0 theo thứ tự position DESC/id DESC, sau khi gỡ Card khỏi vị trí cũ. Tạo Card từ chối title rỗng hoặc dài quá 500 ký tự sau khi trim. Thuật toán khóa theo Project/Board bằng SQL Server application lock và cập nhật cả hai Column trong transaction; mọi đường ghi position phải dùng cùng khóa trước khi bật tích hợp. Chưa kiểm tra đồng thời trên SQL Server hoặc tích hợp adapter Column. WebSocket `card.moved` thuộc #24; chưa có durable `MOVED` Notification event trong catalog #25.

Kiểm tra SQL Server tùy chọn: `mvn -pl services/task-service -am '-Dtask.sqlserver.integration=true' test`. Test hiện xác nhận create fail-closed và không ghi Card khi chưa có validator tin cậy. Nó không xác minh Board/Column thật, Gateway, Redis hoặc E2E; các kiểm tra liên service thuộc task tích hợp. Test chỉ chạy khi bật property trên và dùng cấu hình datasource local.

### Xác minh quan hệ Board/Column

`ColumnRelationshipValidator` vẫn là application port của task-service vì create/move cần kiểm tra quan hệ. Không còn mock profile hoặc allowlist giả. Infrastructure cung cấp adapter mặc định fail-closed bằng 503; adapter thật sẽ được nối khi có nguồn Board/Column đã xác minh. `projectId` trong request không được dùng làm bằng chứng quan hệ.

Phân trang theo Column hỗ trợ `columnId`; dữ liệu thay đổi giữa các trang có thể cần client dedupe theo cardId và hiển thị banner yêu cầu reload. Cursor hiện tại là wire tạm thời; contract lỗi và stale-cursor chưa được chốt. Card detail GET scope theo Project đã có trong source. Các use case Card/Comment có implementation trong source, nhưng chưa xác nhận E2E qua Gateway, Redis và adapter Board/Column thật.

## Cleanup assignment của Membership

Khi board-service thu hồi Membership, request envelope mang `aggregateVersion=authorizationVersion`; payload mang projectId, userId, boardIds, operationId, membershipVersion. Task ghi Inbox, generation fence và job bền vững (gồm source aggregate version) trong một transaction trước ACK; worker chỉ gỡ assignment cùng Project/User/membershipVersion và gửi completion qua Outbox với source aggregate version. Cleanup là tác vụ nền độc lập, không giữ quyền truy cập lại và không chặn operation revoke hoàn tất.

Assignment khóa Project/User trước khi khóa Card, lưu membership generation của assignee và từ chối generation đã có fence. Worker rollback batch khi xử lý hoặc ghi completion thất bại; lịch retry được ghi trong transaction riêng sau rollback. Source snapshot hiện không có cơ chế xác minh độ mới độc lập; stale snapshot và listener/ACK qua broker chưa được xác minh end-to-end.

Task listener cho event cleanup có durable queue/routing binding và dùng `AUTO` ACK: container chỉ ACK khi listener trả về sau khi recorder transaction commit. Listener và topology chỉ được đăng ký khi `MESSAGING_ENABLED`, `MESSAGING_CONSUMERS_ENABLED` và `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY` đều bật; cả ba mặc định false. Poison JSON/contract sai phát `AmqpRejectAndDontRequeueException` để đi thẳng DLQ. Lỗi xử lý recorder đi qua retry policy chung: một lần xử lý đầu, tối đa 3 lần retry cách 5 giây, rồi DLQ. Unit test kiểm tra queue/DLQ binding, listener ACK mode và reject/propagate; chưa có bằng chứng broker retry hoặc DLQ routing end-to-end. Task sở hữu topology queue/DLQ riêng, không sở hữu policy/factory chung. Outbox relay và cleanup scheduler mặc định tắt. Đây là Member assignment cleanup, không phải Account deletion hoặc Project cascade. Account deletion/cascade thuộc Kỳ 2.

## Lỗi source hiện có

| Tình huống | HTTP | Code |
|---|---:|---|
| Input/domain validation | 400 | BAD_REQUEST |
| Không có quyền Project | 403 | PROJECT_ACCESS_DENIED |
| Không đọc/giải mã được snapshot | 503 | AUTHORIZATION_SNAPSHOT_UNAVAILABLE |
| Trusted Gateway identity hoặc validator Board/Column chưa sẵn sàng | 503 | CARD_OPERATION_UNAVAILABLE |
| Card không tồn tại trong Project | 404 | CARD_NOT_FOUND |

Bảng trên mô tả code và mapping hiện có; chưa có bằng chứng các thao tác đã chạy end-to-end với Gateway, Redis và adapter Column thật.
