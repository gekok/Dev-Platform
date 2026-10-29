# Ghi chú quyết định kiến trúc Project-first

**Cập nhật:** 2026-10-29

**Trạng thái:** Ghi lại các quyết định đã thống nhất trong trao đổi. Những mục ở phần “Cần bàn tiếp” chưa phải hợp đồng cuối cùng.

> Đây là thiết kế mục tiêu, không có nghĩa backend đã được triển khai theo các quyết định này.

## 1. Phạm vi và kiến trúc Kỳ 1

- Kỳ 1 **không tạo service triển khai riêng `project-service`**.
- Giữ Gateway và bốn service đang có: `user-service`, `board-service`, `task-service`, `notification-service`.
- Thêm module Project và membership trong `board-service`, dùng chung `board_db` với Board và Column.
- `user-service` quản lý tài khoản và đăng nhập; `board-service` quản lý Project, membership, Board và Column; `task-service` quản lý Card và Comment; `notification-service` quản lý Notification.
- Project là ranh giới membership. Board thuộc một Project; quyền thao tác Board và Card được xác định theo vai trò thành viên trong Project.

### Sơ đồ mục tiêu Kỳ 1

```text
Web / Mobile
    │
API Gateway (xác thực và định tuyến)
    ├── user-service ───────── user_db (nguồn dữ liệu chuẩn về tài khoản)
    ├── board-service ──────── board_db (nguồn dữ liệu chuẩn về Project/membership)
    │     ├── Project / ProjectMember
    │     └── Board / Column
    ├── task-service ───────── task_db (Card / Comment)
    └── notification-service ─ notification_db

User Service / Board Service ── domain events ──> RabbitMQ ──> cập nhật snapshot dùng chung
                                                    Redis (Authentication / Authorization Snapshot)

task-service ── Internal REST API đồng bộ ──> board-service
              (triển khai sau khi có cơ chế bảo mật service nội bộ)
```

## 2. Dữ liệu và lưu trữ

- `Project`, `ProjectMember`, `Board` và `Column` thuộc cùng `board-service` và `board_db`.
- Board tham chiếu Project (`projectId`); mỗi Board thuộc một Project. Quyền trên Board và Card bắt nguồn từ membership Project.
- ID Project/Board/Column/Invitation/Card/Comment/Notification dùng số; ID user dùng UUID. Client coi ID là giá trị opaque.
- `Board.key` unique trong phạm vi Project; input theo hợp đồng dùng chữ thường để thuận tiện tham chiếu trong URL.
- `Project.status` và `Board.status` dùng các giá trị `ACTIVE`, `COMPLETED`, `CANCELLED`.
- Hiện chưa có dữ liệu legacy cần migration. Nếu sau này phát sinh dữ liệu backend runtime cần giữ, phải kiểm tra DB thực tế trước khi migration, không mặc định DB trống.

## 3. Tài khoản, membership và quyền

- `Admin` chỉ quản trị tài khoản hệ thống; Admin không có quyền nghiệp vụ trên Project, Board, membership hoặc Card. Account deletion chưa triển khai trong Kỳ 1.
- Mỗi Project có đúng một `Owner`: tài khoản tạo Project là Owner. Owner không thể tự gỡ mình khỏi Project; Member được tự rời Project.
- Tạo Project yêu cầu Account nghiệp vụ đang `ACTIVE` và danh tính đã được xác thực tại Gateway. `board-service` lấy actor từ identity context tin cậy do Gateway chuyển tiếp; không nhận actor ID từ request body hoặc header do client tự chọn. Tạo Project và Membership `OWNER` phải nằm trong cùng transaction của `board_db`.
- Quyền `SYSTEM_ADMIN` chỉ cấp nghiệp vụ quản trị Account; tự nó không cấp quyền tạo/xem/thao tác Project. Account chỉ có quyền System Admin không được tạo Project. Việc một Account có thể đồng thời mang quyền System Admin và dùng Project, hay cần hai Account riêng, vẫn cần chốt.
- Role hệ thống là dữ liệu của `user-service`, không phải Project role. Schema mục tiêu dùng `SystemRoles` và `UserSystemRoles`; bootstrap System Admin đầu tiên vẫn cần chốt. Client không được tự khai báo role khi tạo Account.
- Owner có thể gỡ Member khỏi Project. Khi không muốn tiếp tục dùng Project/Board, Owner chuyển trạng thái tương ứng sang `CANCELLED`.
- Account deletion chưa triển khai trong Kỳ 1. Định hướng Kỳ 2 là soft delete; nếu Account là Owner thì chuyển Owner cho Member đủ điều kiện có thời gian tham gia sớm nhất. Chặn xóa nếu không có Member kế nhiệm.
- Khi Member bị gỡ hoặc tự rời Project, quyền bị thu hồi khi Redis áp dụng snapshot mới. Task-service unassign Card bằng xử lý nền sau đó; việc cleanup không khôi phục quyền và không xóa Comment hay attribution lịch sử.
- Với lịch sử của Account đã soft-delete ở Kỳ 2, hiển thị “Tài khoản đã bị xóa”; với Account còn tồn tại nhưng đã rời Project, hiển thị “Tài khoản không còn trong Project”.
- `Owner` và `Member` có cùng quyền thao tác công việc trong Project: tạo task/Card, tạo Column, chuyển trạng thái task/Card và comment.
- Chỉ `Owner` có thêm quyền tạo Board và quản lý trạng thái vòng đời Project/Board. Các trạng thái được dùng là `ACTIVE`, `COMPLETED`, `CANCELLED`.
- Quản lý thành viên riêng ở cấp Board chưa thuộc phạm vi hiện tại.

| Nghiệp vụ tài khoản hệ thống | Admin |
|---|---:|
| Tạo tài khoản | Có |
| Thao tác trên Project, Board, membership hoặc Card | Không |

| Nghiệp vụ trong Project | Owner | Member |
|---|---:|---:|
| Mời tài khoản hiện có vào Project / gỡ Member | Có | Không |
| Tự rời Project | Không | Có |
| Tạo Board | Có | Không |
| Quản lý trạng thái Project/Board (`ACTIVE`, `COMPLETED`, `CANCELLED`) | Có | Không |
| Tạo task/Card, tạo Column, chuyển trạng thái task/Card, comment | Có | Có |

## 4. Giao tiếp giữa các service và snapshot phân quyền

- Hệ thống dùng mô hình giao tiếp kết hợp: Internal REST API đồng bộ, RabbitMQ truyền domain event và Redis lưu snapshot dùng chung giữa các instance.
- Internal REST API dùng khi cần đọc dữ liệu gốc đồng bộ hoặc kiểm tra quyền. API này **chưa triển khai** cho đến khi thống nhất/thiết lập bảo mật cho giao tiếp service nội bộ.
- RabbitMQ truyền các event gồm `USER_STATUS_CHANGED`, `USER_SYSTEM_ROLES_CHANGED`, `MEMBERSHIP_CHANGED`, `MEMBERSHIP_REMOVED` và yêu cầu cleanup assignment khi Membership bị thu hồi. Account/Project cascade không thuộc Kỳ 1.
- Redis lưu Authentication/Authorization Snapshot dùng chung. API Gateway xác thực và định tuyến nhưng không sở hữu Authorization Snapshot.
- `user-service` là nguồn dữ liệu chuẩn cho thông tin tài khoản/xác thực; `board-service` là nguồn dữ liệu chuẩn cho Project và phân quyền membership.
- Không duy trì bản sao membership làm nguồn dữ liệu chuẩn trong `task-service`.
- Mỗi thay đổi Account/role/Membership, Outbox và durable snapshot-sync job được commit trong transaction của service sở hữu nguồn dữ liệu. Service đó retry apply snapshot vào Redis theo version; snapshot version cũ không được ghi đè version mới.
- Gỡ Membership có hiệu lực khi Redis apply snapshot mới. Nếu Redis chưa xác nhận apply, API trả `202 Accepted` cùng `operationId` và `statusUrl`; operation thu hồi quyền chỉ hoàn tất sau khi Redis apply được xác nhận. Task unassign là job nền độc lập và không trì hoãn revoke completion.
- Nếu snapshot cùng version đã có trong Redis thì có thể xác nhận đã đồng bộ. Nếu Redis đã có `authorizationVersion` cao hơn snapshot đang retry, job cũ phải ở trạng thái `SUPERSEDED`, không được hiểu là snapshot gỡ quyền đã apply; operation tương ứng không báo `COMPLETED`, client cần tải lại trạng thái Membership mới nhất.
- `operationId` của thay đổi Membership được lưu trên snapshot-sync job để nối trạng thái bền vững với status lookup. API status route và quyền đọc operation chưa được triển khai/chốt trong skeleton.
- Task-service đọc Authentication và Membership snapshot trực tiếp từ Redis, không dùng header client làm bằng chứng quyền. Redis lỗi hoặc snapshot không xác minh được thì fail closed. Internal REST chưa triển khai do chưa có bảo mật giao tiếp service nội bộ.

### Account deletion — Kỳ 2

Kỳ 1 không có endpoint, `DELETING` status, operation, consumer hoặc cascade job cho Account/Project. Kỳ 2 sẽ thiết kế riêng soft delete, Owner succession và cách xử lý dữ liệu phụ thuộc; hướng hiện tại là chuyển Owner cho Member đủ điều kiện tham gia sớm nhất và chặn xóa nếu không có người kế nhiệm.

- Mỗi producer ghi thay đổi nghiệp vụ và bản ghi Outbox vào DB của chính service đó trong cùng `@Transactional`. Outbox Relay dùng `@Scheduled` để publish với `mandatory=true`; Publisher Confirms xác nhận broker đã nhận message, Publisher Returns phát hiện message không được route tới Queue. Chỉ đánh dấu `SENT` khi có confirm thành công và không có return; nếu chưa xác nhận được hoặc message bị return thì không đánh dấu `SENT`, giữ để retry theo backoff/chính sách lỗi đã cấu hình.
- Publisher Confirm (RabbitMQ → Relay) khác Consumer ACK (Consumer → RabbitMQ). Nếu Relay không nhận được confirm, Outbox chưa thể đánh dấu `SENT` và sẽ gửi lại đúng bản ghi với cùng `eventId`, `aggregateVersion` và payload. RabbitMQ có thể đã nhận lần gửi trước nhưng confirm bị mất, nên đây là một nguồn tạo duplicate.
- Consumer dùng Inbox để chống duplicate: lưu `eventId` đã xử lý với unique constraint theo cặp `(consumerName, eventId)`; với xử lý đồng bộ, ghi Inbox và thay đổi nghiệp vụ vào DB Consumer trong cùng transaction. Với tác vụ nền, ghi Inbox cùng durable job/checkpoint trong cùng transaction rồi ACK; worker tiếp tục công việc. Nếu transaction thất bại thì rollback và retry; nếu commit thành công nhưng ACK mất thì RabbitMQ redeliver, Inbox nhận ra duplicate và không tạo side effect lần hai.
- Inbox/idempotency được scope theo từng Consumer. Outbox retry và RabbitMQ redelivery là hai đường giao lại khác nhau; Outbox không bảo đảm Consumer đã xử lý thành công. Mô hình là at-least-once, không phải exactly-once.
- Envelope event mục tiêu có `eventId`, `eventType`, `schemaVersion`, `aggregateType`, `aggregateId`, `aggregateVersion`, `occurredAt` và `payload`. `eventId` nhận diện một event cụ thể để chống trùng; giữ nguyên qua mọi lần retry và không dùng để so thứ tự.
- Tên event viết hoa ở ghi chú này là tên nghiệp vụ/constant; wire `eventType` dùng chữ thường, phân tách bằng dấu chấm (ví dụ `user.status.changed`, `project.membership.removed`).
- Event Kỳ 1 dùng Outbox của service sở hữu dữ liệu nguồn:

| Constant | Wire `eventType` | Aggregate/version | Payload tối thiểu |
|---|---|---|---|
| `USER_STATUS_CHANGED` | `user.status.changed` | `USER_ACCOUNT`, `aggregateId=userId`, version=`authVersion` | `userId`, `status`, `authVersion` |
| `USER_SYSTEM_ROLES_CHANGED` | `user.system-roles.changed` | `USER_ACCOUNT`, `aggregateId=userId`, version=`authVersion` | `userId`, `systemRoles`, `authVersion` |
| `MEMBERSHIP_CHANGED` | `project.membership.changed` | `PROJECT_MEMBERSHIP`, `aggregateId={projectId}:{userId}`, `aggregateVersion=authorizationVersion` | `projectId`, `userId`, `status`, `role`, `membershipVersion` |
| `MEMBERSHIP_REMOVED` | `project.membership.removed` | `PROJECT_MEMBERSHIP`, `aggregateId={projectId}:{userId}`, `aggregateVersion=authorizationVersion` | `projectId`, `userId`, `membershipVersion` |
| `MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED` | `member.assignments.cleanup-requested` | `PROJECT_MEMBERSHIP`, `aggregateId={projectId}:{userId}`, source `aggregateVersion=authorizationVersion` | `operationId`, `projectId`, `userId`, `boardIds`, `membershipVersion` |

- `authVersion` là version trạng thái Account; `membershipVersion` là generation của lần Membership đang hoạt động; `authorizationVersion` là version trạng thái phân quyền của cặp Project/User. Event không thay thế snapshot-sync job.
- Với Membership, mỗi lần quyền đổi làm tăng `authorizationVersion`, và nó là `aggregateVersion`; `membershipVersion` giữ nguyên khi remove, chỉ tăng khi rejoin tạo generation mới. Snapshot Redis CAS và unique key của snapshot-sync job dùng `authorizationVersion`, tránh retry trạng thái ACTIVE cũ ghi đè REMOVED cùng generation.
- Completion cleanup có `eventId` mới nhưng giữ `operationId`, `membershipVersion` và `aggregateVersion` nguồn; worker không tạo thay đổi Membership mới.
- `aggregateVersion` tăng theo thay đổi nghiệp vụ của từng aggregate, không tăng khi retry. Dùng nó để phát hiện event cũ, sai thứ tự hoặc thiếu phiên bản; không bỏ qua mù quáng mọi version thấp hơn. Với event delta cần xử lý gap theo thứ tự/retry hoặc đồng bộ trạng thái chuẩn; snapshot thay thế trạng thái hiện tại có thể dùng quy tắc khác.
- `operationId` nhận diện một quy trình nghiệp vụ nhiều event, ví dụ membership revoke đang chờ Redis apply; nó không thay cho `eventId`. Consumer cũng cần thao tác idempotent nếu cùng operation được phát thành event mới.
- Schema chi tiết của snapshot/event chưa thiết kế; version là bắt buộc để phát hiện dữ liệu cũ/lệch và hỗ trợ xử lý idempotent.
- Hiện chưa đặt mục tiêu tối ưu độ trễ. Khi Redis lỗi hoặc snapshot cũ/không xác minh được, trả lỗi và fail closed; không cho phép dựa vào trạng thái phân quyền chưa xác minh.
- Khi RabbitMQ lỗi nhưng nghiệp vụ và Outbox đã commit thành công, API trả thành công cho phần nghiệp vụ đã commit; Outbox Relay tiếp tục retry nền. Với membership revoke, `202 Accepted` kèm `operationId`/`statusUrl` cho biết Redis snapshot đang chờ apply, không chờ Task assignment cleanup.

## 5. Hợp đồng API Kỳ 1

- Dùng cấu trúc Project-first theo hợp đồng HTTP của demo làm cơ sở cho API thật.
- Project và Board: `GET/POST /api/v1/projects`, `GET /api/v1/projects/{projectId}`, `GET /api/v1/projects/{projectId}/members`, `GET/POST /api/v1/projects/{projectId}/boards`.
- Board và Column: giữ các route Board detail, tạo Column và reorder Column của demo.
- Card và Comment: dùng `GET/POST /api/v1/projects/{projectId}/boards/{boardId}/cards`, `GET /api/v1/projects/{projectId}/cards/{cardId}`, các route `move`, `assignee` và `comments` lồng dưới Project-scoped Card. Task lưu `cards.project_id` và đối chiếu với `projectId` trong route.
- Notification và Board preference: giữ các route list/mark-read Notification và preference Board mặc định theo user + Project của demo.
- Gateway route `/api/v1/projects/**` tới `board-service`; các route Card tới `task-service` theo cấu hình route cụ thể.
- Membership API Kỳ 1:

| Method | Endpoint | Hành vi |
|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/members` | Lấy danh sách thành viên |
| `POST` | `/api/v1/projects/{projectId}/invitations` | Owner chọn tài khoản đã có trong hệ thống; tạo Invitation `PENDING` |
| `GET` | `/api/v1/projects/{projectId}/invitations?status=PENDING` | Đề xuất: Owner xem lời mời của Project để theo dõi/hủy |
| `GET` | `/api/v1/projects/invitations` | Người dùng hiện tại xem inbox Invitation; recipient lấy từ identity đã xác thực |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Người được mời chấp nhận; khi accept mới tạo membership với vai trò Member |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/reject` | Đề xuất: người nhận từ chối Invitation; route thống nhất theo nested resource, status cần chốt |
| `DELETE` | `/api/v1/projects/{projectId}/invitations/{inviteId}` | Đề xuất: Owner hủy Invitation PENDING; cần chốt route/status |
| `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Owner gỡ Member; Member có thể tự rời Project; Owner duy nhất không thể tự gỡ; trả `202` + operation handle khi Redis apply còn pending |

- Với thao tác `DELETE` membership, board-service ghi thay đổi Membership, Outbox và snapshot-sync job trong cùng transaction; quyền bị thu hồi khi Redis apply snapshot mới. Khi Redis apply còn pending, API trả `202` cùng `operationId`/`statusUrl`; completion chỉ sau Redis apply. Task unassign Card chạy nền độc lập và không chặn completion revoke.

- Kỳ 1 chỉ mời tài khoản đã tồn tại; mời người ngoài hệ thống dành cho Kỳ 2. Đổi vai trò qua `PATCH /api/v1/projects/{projectId}/members/{userId}` cũng dành cho Kỳ 2.
- Payload mời dùng `userId` UUID của tài khoản đã có; Owner cần API user-service để tìm Account ứng viên và profile API để render tên/trạng thái tác giả. Route, giới hạn/field được lộ và cách client compose là đề xuất còn mở.
- Khi dữ liệu không thể xóa riêng (ví dụ Comment), giữ nội dung và attribution; UI ghi “Tài khoản đã bị xóa” hoặc “Tài khoản không còn trong Project” theo trạng thái account.
- API list Card thật dùng **cursor pagination**: page size mặc định 5, tối đa 20, sắp xếp `position DESC, id DESC`. Cursor opaque mã hóa `position` và `id`. Đề xuất endpoint infinite-scroll riêng từng Column yêu cầu `columnId` và ràng buộc cursor với `boardId`, `columnId`, sort contract; route/wire contract còn chờ duyệt.
- Truy vấn trang tiếp theo dùng điều kiện tương ứng với thứ tự giảm dần; backend lấy `limit + 1` để xác định còn trang tiếp hay không:

```sql
SELECT *
FROM cards
WHERE project_id = :projectId
  AND board_id = :boardId
  AND column_id = :columnId
  AND (
    position < :lastPosition
    OR (position = :lastPosition AND id < :lastId)
  )
ORDER BY position DESC, id DESC
LIMIT :limitPlusOne;
```

- Chấp nhận dữ liệu thay đổi trong lúc tải trang: client loại Card trùng theo `cardId` và dùng realtime event cập nhật Card đã di chuyển. Nếu phát hiện bất nhất, hiện banner đầu trang yêu cầu người dùng reload.
- Cursor `(position DESC,id DESC)` có thứ tự toàn phần vì `id` duy nhất. `columnId` là scope để infinite-scroll riêng từng Column; server phải bind/validate cursor theo `projectId`, `boardId`, `columnId` và sort contract.

Hợp đồng tham chiếu: `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md` và phần Board preference tại `web-demo/docs/superpowers/specs/2026-10-21-project-board-navigation-design.md`.

## 6. Xác thực tại Gateway

- Trong Kỳ 1, API Gateway xác thực danh tính và định tuyến request; các service phía sau tin cậy danh tính/ngữ cảnh do Gateway chuyển tiếp, không cần triển khai lại toàn bộ xác thực. Gateway chỉ chuyển tiếp identity đã suy ra từ access token hợp lệ; phải loại bỏ/ghi đè `X-User-Id` và identity header do client gửi.
- Account phải ở trạng thái `ACTIVE` để được xác thực/cấp quyền. Kỳ 1 không có trạng thái `DELETING` hay Account deletion flow. Gateway và service không được suy ra Account còn hợp lệ chỉ từ token chưa hết hạn; nếu snapshot không xác minh được thì fail closed.
- `POST /api/v1/projects` chỉ nhận Account nghiệp vụ đang `ACTIVE`. Gateway cung cấp identity đã xác thực; `board-service` dùng identity đó làm Owner và ghi Project cùng Owner membership trong một transaction. Client không được chọn `ownerId`/`userId` trong payload để giả danh.
- Quyền `SYSTEM_ADMIN` tự nó không cho phép thao tác Project. Claim/nguồn kiểm tra role Admin và chính sách Account vừa là Admin vừa là người dùng Project còn chờ chốt; role từ request body/header không được tin cậy.
- `board-service` là nơi có dữ liệu chuẩn để quyết định quyền theo membership Project. Internal REST từ `task-service` để hỏi/kiểm tra dữ liệu này sẽ triển khai sau khi có bảo mật giao tiếp nội bộ.
- Cơ chế bảo vệ danh tính/ngữ cảnh chuyển tiếp, xử lý header do client gửi và giới hạn truy cập trực tiếp đến service cần được cụ thể hóa khi triển khai.

## 7. Các điểm cần bàn tiếp

1. **System Admin:** bootstrap System Admin đầu tiên và chính sách một Account vừa là System Admin vừa tham gia Project.
2. **Account lookup cho Invitation:** vì internal REST chưa bảo mật, cần chốt cách board-service xác minh target Account tồn tại/ACTIVE trước khi bật Invitation và Accept; API directory/bulk profile cùng field được lộ cũng còn mở.
3. **Invitation policy:** expiry, reject/cancel rules, list Invitation của Owner và các mã lỗi/status nghiệp vụ chi tiết.
4. **Project/Board lifecycle:** ba trạng thái `ACTIVE`, `COMPLETED`, `CANCELLED` đã thống nhất; transition graph và tác động tới dữ liệu con còn mở.
5. **API details:** cursor wire format/scope, Card status, migration tool và index theo query thực tế.
6. **Board preference:** service sở hữu, nơi lưu, kiểm tra Membership và xử lý khi Board/Project không còn hoạt động.
7. **Kỳ 2:** Account soft delete/Owner succession theo quyết định định hướng; service topology cho Git core/Workflow và database sẽ được thiết kế riêng.

Hiện chưa có dữ liệu legacy cần migration. Nếu sau này xuất hiện dữ liệu backend cần giữ, sẽ kiểm tra dữ liệu thực tế trước khi lập kế hoạch migration.

## 8. Tài liệu liên quan và thứ tự ưu tiên

- `docs/README.md` là chỉ mục tài liệu và ghi rõ tài liệu nào mô tả quyết định mục tiêu, API mục tiêu, runtime cũ hay mock demo.
- Ghi chú này là nguồn chuẩn cho quyết định backend Kỳ 1 đã thống nhất và các mục còn mở. Nó thay thế phần topology/permission backend trong demo spec nói Kỳ 1 có deployable `project-service`; phần React/MSW của spec vẫn là thiết kế demo.
- `README.md` tóm tắt sản phẩm, topology mục tiêu và chênh lệch giữa thiết kế với runtime; README không thay thế API contract.
- `docs/api/kanban-api.md` là bản contract Project-first mục tiêu; mỗi mục được gắn trạng thái đã chốt, đề xuất hoặc còn mở. Phần Board-first API cũ chỉ là tham khảo runtime. Không coi đề xuất là quyết định đã duyệt.
- `docs/project/06-integration.md` là checklist tích hợp mục tiêu, không phải bằng chứng các luồng đã chạy.
- `docs/project/account-deletion-skeleton-guide.md` lưu ghi chú tham khảo Kỳ 2; không mô tả runtime Kỳ 1.
- `docs/project/01-user-service.md` đến `06-integration.md` và `services/*/docs/` tách thiết kế mục tiêu khỏi code/schema runtime hiện có. API/schema đích là tài liệu thiết kế; nội dung gắn “đề xuất/còn mở” chưa phải implementation contract đã duyệt.
- React/MSW demo và các spec của nó chỉ là nguồn tham khảo cho UX/mock API. Các chi tiết backend trong demo không vượt quyền ưu tiên của ghi chú này.
