# Ghi chú quyết định kiến trúc Project-first

**Cập nhật:** 2026-10-28

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
- ID Project/Board/Column/Card/Comment/Notification dùng số; ID user dùng UUID. Client coi ID là giá trị opaque.
- `Board.key` unique trong phạm vi Project; input theo hợp đồng dùng chữ thường để thuận tiện tham chiếu trong URL.
- `Project.status` và `Board.status` dùng các giá trị `ACTIVE`, `COMPLETED`, `CANCELLED`.
- Hiện chưa có dữ liệu legacy cần migration. Nếu sau này phát sinh dữ liệu backend runtime cần giữ, phải kiểm tra DB thực tế trước khi migration, không mặc định DB trống.

## 3. Tài khoản, membership và quyền

- `Admin` chỉ quản trị vòng đời tài khoản hệ thống (tạo/xóa tài khoản). Admin không có quyền nghiệp vụ trên Project, Board, membership hoặc Card.
- Mỗi Project có đúng một `Owner`: tài khoản tạo Project là Owner. Owner không thể tự gỡ mình khỏi Project; Member được tự rời Project.
- System Admin không được dùng quyền Admin để tạo hoặc thao tác Project. Nếu một người cần cả quyền Admin hệ thống và quyền dùng Project, mô hình một Account đa vai trò hay hai Account riêng còn cần chốt; API tạo Project không được mặc định cấp Owner cho System Admin.
- Owner có thể gỡ Member khỏi Project. Khi không muốn tiếp tục dùng Project/Board, Owner chuyển trạng thái tương ứng sang `CANCELLED`.
- Xóa Account do Admin yêu cầu là quy trình bất đồng bộ do `user-service` điều phối. Account chuyển sang `DELETING` để chặn đăng nhập/thao tác mới; chỉ xóa thông tin cá nhân sau khi các service xác nhận đã xử lý Project sở hữu và membership liên quan. Project mà tài khoản chỉ là Member vẫn tồn tại và membership bị gỡ.
- Khi tài khoản bị xóa, Member bị gỡ, hoặc Member tự rời Project, quyền phải bị chặn hiệu lực trước khi request báo thành công; task-service unassign khỏi Card bằng xử lý nền sau đó. Comment và attribution lịch sử được giữ.
- Giữ lại nội dung lịch sử không thể gỡ riêng như Comment và các thay đổi đã ghi trên Project còn tồn tại; không xóa các thay đổi của account đã xóa.
- Với account đã xóa, hiển thị “Tài khoản đã bị xóa”; với account còn tồn tại nhưng đã rời Project, hiển thị “Tài khoản không còn trong Project”.
- `Owner` và `Member` có cùng quyền thao tác công việc trong Project: tạo task/Card, tạo Column, chuyển trạng thái task/Card và comment.
- Chỉ `Owner` có thêm quyền tạo Board và quản lý trạng thái vòng đời Project/Board. Các trạng thái được dùng là `ACTIVE`, `COMPLETED`, `CANCELLED`.
- Quản lý thành viên riêng ở cấp Board chưa thuộc phạm vi hiện tại.

| Nghiệp vụ tài khoản hệ thống | Admin |
|---|---:|
| Tạo/xóa tài khoản | Có |
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
- RabbitMQ truyền các event gồm `USER_STATUS_CHANGED`, `MEMBERSHIP_CHANGED`, `MEMBERSHIP_REMOVED` và các event điều phối xóa Account/Project.
- Redis lưu Authentication/Authorization Snapshot dùng chung. API Gateway xác thực và định tuyến nhưng không sở hữu Authorization Snapshot.
- `user-service` là nguồn dữ liệu chuẩn cho thông tin tài khoản/xác thực; `board-service` là nguồn dữ liệu chuẩn cho Project và phân quyền membership.
- Không duy trì bản sao membership làm nguồn dữ liệu chuẩn trong `task-service`.
- Gỡ Membership phải chặn quyền hiệu lực trước khi API trả thành công; không chờ task-service unassign xong. Thay đổi membership và Outbox event được commit cùng transaction; assignment cleanup là công việc nền có thể retry.
- Vì request đọc quyền có thể dùng Redis Authentication/Authorization Snapshot, chỉ commit DB rồi chờ RabbitMQ event bất đồng bộ không đủ để thu hồi tức thời. Trước khi trả thành công, mọi authorization check mới phải từ chối snapshot cũ; nếu không xác minh được quyền hiện hành thì fail closed. Cơ chế Redis invalidation/fencing và điểm đồng bộ chính xác vẫn cần chốt.

### Xóa Account và cascade Project

- `user-service` điều phối operation xóa Account ở cấp hệ thống. Trong cùng transaction, service đặt Account thành `DELETING`, ghi operation bền vững với `operationId`, và ghi Outbox event `ACCOUNT_DELETION_REQUESTED`. Account-wide authentication phải bị chặn hiệu lực trước khi API trả `202`; không chờ board/task cleanup. Trạng thái này chưa xóa Account vật lý.
- API khởi tạo chỉ trả `202 Accepted` cùng `operationId` và URL để đọc tiến độ sau khi request/Outbox bền vững và Account-wide authorization đã bị chặn hiệu lực. `202` không đợi downstream cleanup. `operationId` theo dõi toàn bộ yêu cầu xóa Account; mỗi Project sở hữu cần xóa có một `projectDeletionId`; mỗi event có `eventId` riêng, giữ nguyên khi Outbox retry.
- `board-service` điều phối dữ liệu Project. Khi nhận yêu cầu, service hủy Invitation `PENDING` của Account và thu hồi Membership trong Project mà Account chỉ là Member; cùng transaction ghi operation cleanup và Outbox yêu cầu `task-service` unassign Account khỏi Card thuộc các Board liên quan. Task unassign chạy nền, không trì hoãn thu hồi quyền. Account `DELETING` đã bị chặn đăng nhập toàn cục trong lúc các Membership và assignment được dọn.
- Với từng Project do Account sở hữu, `board-service` tạo operation con, chặn thao tác Project/Board mới trong service, rồi ghi Outbox event `PROJECT_DELETION_REQUESTED` có `operationId`, `projectDeletionId`, `projectId`, `boardIds` và `reason`. `task-service` phải khóa thao tác Card trên các `boardIds` trước khi bắt đầu xóa. Không thêm `DELETING` vào `Project.status`; tiến độ xóa thuộc operation riêng để giữ bộ status Project/Board đã thống nhất.
- `task-service` xóa Card và Comment theo `boardIds`; `notification-service` xóa Notification thuộc các Board đó. Hai service này không xóa Column: `board-service` sở hữu Project, Board, Membership và Column, và chỉ xóa các dữ liệu đó sau khi nhận đủ xác nhận downstream.
- Cascade theo batch dùng deletion job/checkpoint bền vững để worker tiếp tục hoặc retry từ vị trí đã lưu; chỉ phát event hoàn tất sau khi toàn bộ dữ liệu thuộc service đó đã được xử lý. Thao tác xóa phải idempotent.
- Với Project mà Account chỉ là Member, quyền bị thu hồi trước; `task-service` xử lý unassign bất đồng bộ và gửi xác nhận. Chỉ sau khi Project owned đã cascade và assignment cleanup ở các Project còn lại hoàn tất, `board-service` mới báo hoàn tất cho `user-service`. `user-service` sau đó xóa thông tin cá nhân và đánh dấu Account Deletion Operation `COMPLETED`; giữ định danh tối thiểu cần thiết để lịch sử hiển thị “Tài khoản đã bị xóa”.
- Nếu cùng user được mời/accept lại trong khi assignment cleanup cũ còn pending, event cũ có thể bỏ assignment mới. Trước khi bật flow cần chọn một invariant: chặn re-invite/Accept/assign cho `(projectId,userId)` đến khi cleanup hoàn tất, hoặc phân biệt membership/assignment bằng generation/cutoff. Chưa chốt phương án.
- Nếu Notification create event còn chờ trong broker khi cascade xóa xong, consumer có thể tạo lại Notification sau deletion. Trước khi bật consumer cần durable fence/tombstone hoặc quy tắc version/order cho Board bị xóa; Inbox chỉ chống lặp cùng `eventId`, không giải quyết event khác ID đến muộn. Schema và policy còn mở.
- Cách khóa đồng bộ thao tác ghi giữa `board-service` và `task-service`, tên/payload event phản hồi, trạng thái chi tiết của operation, và phạm vi Notification cần xóa sẽ được chốt trong API/event contract.

- Mỗi producer ghi thay đổi nghiệp vụ và bản ghi Outbox vào DB của chính service đó trong cùng `@Transactional`. Outbox Relay dùng `@Scheduled` để publish với `mandatory=true`; Publisher Confirms xác nhận broker đã nhận message, Publisher Returns phát hiện message không được route tới Queue. Chỉ đánh dấu `SENT` khi có confirm thành công và không có return; nếu chưa xác nhận được hoặc message bị return thì không đánh dấu `SENT`, giữ để retry theo backoff/chính sách lỗi đã cấu hình.
- Publisher Confirm (RabbitMQ → Relay) khác Consumer ACK (Consumer → RabbitMQ). Nếu Relay không nhận được confirm, Outbox chưa thể đánh dấu `SENT` và sẽ gửi lại đúng bản ghi với cùng `eventId`, `aggregateVersion` và payload. RabbitMQ có thể đã nhận lần gửi trước nhưng confirm bị mất, nên đây là một nguồn tạo duplicate.
- Consumer dùng Inbox để chống duplicate: lưu `eventId` đã xử lý với unique constraint theo cặp `(consumerName, eventId)`; với xử lý đồng bộ, ghi Inbox và thay đổi nghiệp vụ vào DB Consumer trong cùng transaction. Với cascade theo batch, ghi Inbox cùng deletion job/checkpoint bền vững trong cùng transaction rồi ACK; worker tiếp tục công việc và chỉ phát event hoàn tất sau khi xử lý hết. Nếu transaction ghi nhận thất bại thì rollback và retry; nếu commit thành công nhưng ACK mất thì RabbitMQ redeliver, Inbox nhận ra duplicate và không tạo thêm side effect.
- Inbox/idempotency được scope theo từng Consumer. Outbox retry và RabbitMQ redelivery là hai đường giao lại khác nhau; Outbox không bảo đảm Consumer đã xử lý thành công. Mô hình là at-least-once, không phải exactly-once.
- Envelope event mục tiêu có `eventId`, `eventType`, `schemaVersion`, `aggregateId`, `aggregateVersion`, `occurredAt` và `payload`. `eventId` nhận diện một event cụ thể để chống trùng; giữ nguyên qua mọi lần retry và không dùng để so thứ tự.
- Tên event viết hoa ở ghi chú này là tên nghiệp vụ/constant; `shared/event-contracts` hiện ánh xạ sang wire `eventType` chữ thường, phân tách bằng dấu chấm (ví dụ `account.deletion.requested`). Mapping hiện tại là provisional, cần được chốt trong event contract.
- `aggregateVersion` tăng theo thay đổi nghiệp vụ của từng aggregate, không tăng khi retry. Dùng nó để phát hiện event cũ, sai thứ tự hoặc thiếu phiên bản; không bỏ qua mù quáng mọi version thấp hơn. Với event delta cần xử lý gap theo thứ tự/retry hoặc đồng bộ trạng thái chuẩn; snapshot thay thế trạng thái hiện tại có thể dùng quy tắc khác.
- `operationId` nhận diện một quy trình nghiệp vụ nhiều event (ví dụ cascade xóa Project); nó không thay cho `eventId`. Consumer cũng cần thao tác idempotent nếu cùng operation được phát thành event mới.
- Schema chi tiết của snapshot/event chưa thiết kế; version là bắt buộc để phát hiện dữ liệu cũ/lệch và hỗ trợ xử lý idempotent.
- Hiện chưa đặt mục tiêu tối ưu độ trễ. Khi Redis lỗi hoặc snapshot cũ/không xác minh được, trả lỗi và fail closed; không cho phép dựa vào trạng thái phân quyền chưa xác minh.
- Khi RabbitMQ lỗi nhưng nghiệp vụ và Outbox đã commit thành công, API trả thành công; Outbox Relay tiếp tục retry nền. Với cascade xóa Project bất đồng bộ, API trả `202 Accepted` kèm `operationId`; response xác nhận yêu cầu đã được ghi bền, không xác nhận Consumer đã xử lý xong.

## 5. Hợp đồng API Kỳ 1

- Dùng cấu trúc Project-first theo hợp đồng HTTP của demo làm cơ sở cho API thật.
- Project và Board: `GET/POST /api/v1/projects`, `GET /api/v1/projects/{projectId}`, `GET /api/v1/projects/{projectId}/members`, `GET/POST /api/v1/projects/{projectId}/boards`.
- Board và Column: giữ các route Board detail, tạo Column và reorder Column của demo.
- Card và Comment: giữ các route list/create Card, Card detail, move, assign, list/create Comment của demo.
- Notification và Board preference: giữ các route list/mark-read Notification và preference Board mặc định theo user + Project của demo.
- Gateway route `/api/v1/projects/**` tới `board-service`; các route Card tới `task-service` theo cấu hình route cụ thể.
- Membership API Kỳ 1:

| Method | Endpoint | Hành vi |
|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/members` | Lấy danh sách thành viên |
| `POST` | `/api/v1/projects/{projectId}/invitations` | Owner chọn tài khoản đã có trong hệ thống; tạo Invitation `PENDING` |
| `GET` | `/api/v1/projects/{projectId}/invitations?status=PENDING` | Đề xuất: Owner xem lời mời của Project để theo dõi/hủy |
| `GET` | `/api/v1/users/me/project-invitations` | Đề xuất: người dùng đọc Invitation gửi tới mình; route/service ownership cần chốt |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Người được mời chấp nhận; khi accept mới tạo membership với vai trò Member |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/reject` | Đề xuất: người nhận từ chối Invitation; route thống nhất theo nested resource, status cần chốt |
| `DELETE` | `/api/v1/projects/{projectId}/invitations/{inviteId}` | Đề xuất: Owner hủy Invitation PENDING; cần chốt route/status |
| `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Owner gỡ Member; Member có thể tự rời Project; không thể tự gỡ Owner duy nhất |

- Với thao tác `DELETE` membership, board-service thu hồi Membership và ghi Outbox trong cùng transaction; quyền phải bị chặn hiệu lực trước khi response `204` thành công. Sau đó task-service unassign Card liên quan ở nền. Response không chờ assignment cleanup. Redis invalidation/fencing và cách phản hồi khi chưa xác nhận được revoke còn mở; fail closed.

- Kỳ 1 chỉ mời tài khoản đã tồn tại; mời người ngoài hệ thống dành cho Kỳ 2. Đổi vai trò qua `PATCH /api/v1/projects/{projectId}/members/{userId}` cũng dành cho Kỳ 2.
- Payload mời dùng `userId` UUID của tài khoản đã có; Owner cần API user-service để tìm Account ứng viên và profile API để render tên/trạng thái tác giả. Route, giới hạn/field được lộ và cách client compose là đề xuất còn mở.
- Khi dữ liệu không thể xóa riêng (ví dụ Comment), giữ nội dung và attribution; UI ghi “Tài khoản đã bị xóa” hoặc “Tài khoản không còn trong Project” theo trạng thái account.
- API list Card thật dùng **cursor pagination**: page size mặc định 5, tối đa 20, sắp xếp `position DESC, id DESC`. Cursor opaque mã hóa `position` và `id`. Đề xuất endpoint infinite-scroll riêng từng Column yêu cầu `columnId` và ràng buộc cursor với `boardId`, `columnId`, sort contract; route/wire contract còn chờ duyệt.
- Truy vấn trang tiếp theo dùng điều kiện tương ứng với thứ tự giảm dần; backend lấy `limit + 1` để xác định còn trang tiếp hay không:

```sql
SELECT *
FROM cards
WHERE board_id = :boardId
  AND column_id = :columnId
  AND (
    position < :lastPosition
    OR (position = :lastPosition AND id < :lastId)
  )
ORDER BY position DESC, id DESC
LIMIT :limitPlusOne;
```

- Chấp nhận dữ liệu thay đổi trong lúc tải trang: client loại Card trùng theo `cardId` và dùng realtime event cập nhật Card đã di chuyển. Nếu phát hiện bất nhất, hiện banner đầu trang yêu cầu người dùng reload.
- Cursor `(position DESC,id DESC)` có thứ tự toàn phần vì `id` duy nhất. `columnId` là scope đề xuất để infinite-scroll riêng từng Column theo UI, không phải điều kiện toán học của thứ tự. Nếu duyệt scope này, server phải bind/validate cursor theo `boardId`, `columnId` và sort contract; muốn infinite-scroll toàn Board thì cần scope/index/contract khác.

Hợp đồng tham chiếu: `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md` và phần Board preference tại `web-demo/docs/superpowers/specs/2026-10-21-project-board-navigation-design.md`.

## 6. Xác thực tại Gateway

- Trong Kỳ 1, API Gateway xác thực danh tính và định tuyến request; các service phía sau tin cậy danh tính/ngữ cảnh do Gateway chuyển tiếp, không cần triển khai lại toàn bộ xác thực.
- `board-service` là nơi có dữ liệu chuẩn để quyết định quyền theo membership Project. Internal REST từ `task-service` để hỏi/kiểm tra dữ liệu này sẽ triển khai sau khi có bảo mật giao tiếp nội bộ.
- Cơ chế bảo vệ danh tính/ngữ cảnh chuyển tiếp, xử lý header do client gửi và giới hạn truy cập trực tiếp đến service cần được cụ thể hóa khi triển khai.

## 7. Các điểm cần bàn tiếp

1. **Cascade xóa Account/Project:** đã thống nhất điều phối hai tầng, `operationId`/`projectDeletionId`, `202 Accepted`, ranh giới dữ liệu, revoke trước rồi Task unassign nền. Account operation chỉ hoàn tất sau Project cascade và Member assignment cleanup. Còn chốt route Admin/status response, event phản hồi/payload, phạm vi Notification, lỗi/retry, write barrier, durable fence chống Notification đến muộn và theo dõi cleanup. Scaffold nằm tại [`account-deletion-skeleton-guide.md`](account-deletion-skeleton-guide.md); chưa bật end-to-end.
2. **Membership API và User directory:** `userId` UUID là payload mục tiêu để mời tài khoản đã tồn tại. Cần đóng error/status, list/reject/cancel/expiry Invitation, API tìm Account/profile và quyền/field được lộ. Board-service phải kiểm tra target Account tồn tại/ACTIVE; kết quả tìm kiếm do client gửi không thay thế validation phía server. Do Internal REST chưa bảo mật, cần chốt nguồn validation (snapshot có version hay cơ chế khác) trước khi bật Invitation/Accept. Khi cleanup assignment cũ còn pending, cần chọn chặn rejoin/assign hoặc generation/cutoff để không unassign nhầm dữ liệu mới.
3. **Outbox và Consumer:** retry/backoff cụ thể, Dead Letter handling và cách quan sát khi Consumer chưa xử lý được event. API trả thành công sau khi nghiệp vụ và Outbox commit; cascade xóa Project trả `202 Accepted` kèm `operationId`, chưa khẳng định xử lý downstream đã hoàn tất.
4. **Snapshot/event:** schema envelope, TTL/invalidation/fencing và cách xử lý event gap/sai thứ tự theo `aggregateVersion`. `eventId` + Inbox xử lý duplicate; version là bắt buộc nhưng không thay thế Inbox. Đã chốt invariant: snapshot cũ không được cấp quyền sau revoke và API chỉ báo thành công sau khi revoke có hiệu lực; cơ chế Redis đồng bộ/fence cụ thể vẫn cần chốt.
5. **Status Project/Board:** status áp dụng cho cả hai là `ACTIVE`, `COMPLETED`, `CANCELLED`; cần chốt transition graph và tác động khi Project bị hủy/hoàn thành.
6. **Kỳ 2:** thiết kế service đích, ranh giới Git core/Workflow và database được hoãn đến khi bắt đầu Kỳ 2.
7. **Board preference:** giữ trải nghiệm Board mặc định theo user + Project từ demo; route tham khảo có trong API map. Service sở hữu preference, nơi lưu, kiểm tra membership và behavior khi Board/Project bị xóa chưa chốt.
8. **Chi tiết API/schema:** tài liệu service đã có logical target schema và target routes, tách khỏi runtime. Chưa duyệt DDL/OpenAPI cuối: field/length, error mapping, cursor query/envelope/scope, role reorder Column, Card status, Admin role storage/provisioning, Invitation expiry, migration tool và index cần theo query thật. API directory/profile và client composition cũng còn mở. Mỗi đề xuất được gắn nhãn, không phải quyết định đã duyệt.

Hiện chưa có dữ liệu legacy cần migration. Nếu sau này xuất hiện dữ liệu backend cần giữ, sẽ kiểm tra dữ liệu thực tế trước khi lập kế hoạch migration.

## 8. Tài liệu liên quan và thứ tự ưu tiên

- `docs/README.md` là chỉ mục tài liệu và ghi rõ tài liệu nào mô tả quyết định mục tiêu, API mục tiêu, runtime cũ hay mock demo.
- Ghi chú này là nguồn chuẩn cho quyết định backend Kỳ 1 đã thống nhất và các mục còn mở. Nó thay thế phần topology/permission backend trong demo spec nói Kỳ 1 có deployable `project-service`; phần React/MSW của spec vẫn là thiết kế demo.
- `README.md` tóm tắt sản phẩm, topology mục tiêu và chênh lệch giữa thiết kế với runtime; README không thay thế API contract.
- `docs/api/kanban-api.md` là bản contract Project-first mục tiêu; mỗi mục được gắn trạng thái đã chốt, đề xuất hoặc còn mở. Phần Board-first API cũ chỉ là tham khảo runtime. Không coi đề xuất là quyết định đã duyệt.
- `docs/project/06-integration.md` là checklist tích hợp mục tiêu, không phải bằng chứng các luồng đã chạy.
- `docs/project/account-deletion-skeleton-guide.md` ghi hiện trạng scaffold và cách tiếp tục triển khai cascade theo từng service.
- `docs/project/01-user-service.md` đến `06-integration.md` và `services/*/docs/` tách thiết kế mục tiêu khỏi code/schema runtime hiện có. API/schema đích là tài liệu thiết kế; nội dung gắn “đề xuất/còn mở” chưa phải implementation contract đã duyệt.
- React/MSW demo và các spec của nó chỉ là nguồn tham khảo cho UX/mock API. Các chi tiết backend trong demo không vượt quyền ưu tiên của ghi chú này.
