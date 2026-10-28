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
- Owner có thể gỡ Member khỏi Project. Khi không muốn tiếp tục dùng Project/Board, Owner chuyển trạng thái tương ứng sang `CANCELLED`.
- Xóa Account do Admin yêu cầu là quy trình bất đồng bộ do `user-service` điều phối. Account chuyển sang `DELETING` để chặn đăng nhập/thao tác mới; chỉ xóa thông tin cá nhân sau khi các service xác nhận đã xử lý Project sở hữu và membership liên quan. Project mà tài khoản chỉ là Member vẫn tồn tại và membership bị gỡ.
- Khi tài khoản bị xóa, Member bị gỡ, hoặc Member tự rời Project, người đó phải được tự động unassign khỏi toàn bộ Card trong Project tương ứng.
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

### Xóa Account và cascade Project

- `user-service` điều phối operation xóa Account ở cấp hệ thống. Trong cùng transaction, service đặt Account thành `DELETING`, ghi operation bền vững với `operationId`, và ghi Outbox event `ACCOUNT_DELETION_REQUESTED`. Trạng thái này chặn đăng nhập/thao tác mới; chưa xóa Account vật lý.
- API khởi tạo trả `202 Accepted` cùng `operationId` và URL để đọc tiến độ. `operationId` theo dõi toàn bộ yêu cầu xóa Account; mỗi Project sở hữu cần xóa có một `projectDeletionId`; mỗi event có `eventId` riêng, giữ nguyên khi Outbox retry.
- `board-service` điều phối dữ liệu Project. Khi nhận yêu cầu, service hủy Invitation `PENDING` của Account và xử lý membership trong Project mà Account chỉ là Member; việc gỡ membership phát yêu cầu để `task-service` unassign Account khỏi Card thuộc các Board liên quan.
- Với từng Project do Account sở hữu, `board-service` tạo operation con, chặn thao tác Project/Board mới trong service, rồi ghi Outbox event `PROJECT_DELETION_REQUESTED` có `operationId`, `projectDeletionId`, `projectId`, `boardIds` và `reason`. `task-service` phải khóa thao tác Card trên các `boardIds` trước khi bắt đầu xóa. Không thêm `DELETING` vào `Project.status`; tiến độ xóa thuộc operation riêng để giữ bộ status Project/Board đã thống nhất.
- `task-service` xóa Card và Comment theo `boardIds`; `notification-service` xóa Notification thuộc các Board đó. Hai service này không xóa Column: `board-service` sở hữu Project, Board, Membership và Column, và chỉ xóa các dữ liệu đó sau khi nhận đủ xác nhận downstream.
- Cascade theo batch dùng deletion job/checkpoint bền vững để worker tiếp tục hoặc retry từ vị trí đã lưu; chỉ phát event hoàn tất sau khi toàn bộ dữ liệu thuộc service đó đã được xử lý. Thao tác xóa phải idempotent.
- Khi các Project sở hữu đã cascade xong, membership liên quan đã được gỡ, và `task-service` xác nhận đã unassign Account khỏi Card trong các Project còn tồn tại, `board-service` mới báo hoàn tất cho `user-service`. `user-service` sau đó xóa thông tin cá nhân và đánh dấu Account Deletion Operation `COMPLETED`; giữ định danh tối thiểu cần thiết để lịch sử hiển thị “Tài khoản đã bị xóa”.
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
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Người được mời chấp nhận; khi accept mới tạo membership với vai trò Member |
| `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Owner gỡ Member; Member có thể tự rời Project; không thể tự gỡ Owner duy nhất |

- Kỳ 1 chỉ mời tài khoản đã tồn tại; mời người ngoài hệ thống dành cho Kỳ 2. Đổi vai trò qua `PATCH /api/v1/projects/{projectId}/members/{userId}` cũng dành cho Kỳ 2.
- Payload mời dùng định danh User đã chọn; tên field/payload cụ thể cần chốt với API schema.
- Khi dữ liệu không thể xóa riêng (ví dụ Comment), giữ nội dung và attribution; UI ghi “Tài khoản đã bị xóa” hoặc “Tài khoản không còn trong Project” theo trạng thái account.
- API list Card thật dùng **cursor pagination**: page size mặc định 5, tối đa 20, sắp xếp `position DESC, id DESC`. Cursor opaque mã hóa `position` và `id`.
- Truy vấn trang tiếp theo dùng điều kiện tương ứng với thứ tự giảm dần; backend lấy `limit + 1` để xác định còn trang tiếp hay không:

```sql
SELECT *
FROM cards
WHERE column_id = :columnId
  AND (
    position < :lastPosition
    OR (position = :lastPosition AND id < :lastId)
  )
ORDER BY position DESC, id DESC
LIMIT :limitPlusOne;
```

- Chấp nhận dữ liệu thay đổi trong lúc tải trang: client loại Card trùng theo `cardId` và dùng realtime event cập nhật Card đã di chuyển. Nếu phát hiện bất nhất, hiện banner đầu trang yêu cầu người dùng reload.

Hợp đồng tham chiếu: `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md` và phần Board preference tại `web-demo/docs/superpowers/specs/2026-10-21-project-board-navigation-design.md`.

## 6. Xác thực tại Gateway

- Trong Kỳ 1, API Gateway xác thực danh tính và định tuyến request; các service phía sau tin cậy danh tính/ngữ cảnh do Gateway chuyển tiếp, không cần triển khai lại toàn bộ xác thực.
- `board-service` là nơi có dữ liệu chuẩn để quyết định quyền theo membership Project. Internal REST từ `task-service` để hỏi/kiểm tra dữ liệu này sẽ triển khai sau khi có bảo mật giao tiếp nội bộ.
- Cơ chế bảo vệ danh tính/ngữ cảnh chuyển tiếp, xử lý header do client gửi và giới hạn truy cập trực tiếp đến service cần được cụ thể hóa khi triển khai.

## 7. Các điểm cần bàn tiếp

1. **Cascade xóa Account/Project:** đã thống nhất điều phối hai tầng, `operationId`/`projectDeletionId`, `202 Accepted`, ranh giới dữ liệu từng service và Inbox/checkpoint cho batch. Scaffold hiện trạng và thứ tự hoàn thiện từng service nằm tại [`account-deletion-skeleton-guide.md`](account-deletion-skeleton-guide.md); đây mới là biên compile-ready, chưa bật API/listener và chưa phải cascade end-to-end. Còn chốt route Admin tạo/xóa Account và status response, event phản hồi/payload, phạm vi Notification, trạng thái lỗi/retry của operation và cơ chế bảo đảm Task Service khóa ghi trước khi xóa.
2. **Membership API:** payload mời (đề xuất `userId`), lỗi khi account không tồn tại/đã là Member, và API đọc/từ chối/hủy/hết hạn Invitation. Khi Account bị xóa, Invitation `PENDING` của Account bị hủy; việc này đã thống nhất.
3. **Outbox và Consumer:** retry/backoff cụ thể, Dead Letter handling và cách quan sát khi Consumer chưa xử lý được event. API trả thành công sau khi nghiệp vụ và Outbox commit; cascade xóa Project trả `202 Accepted` kèm `operationId`, chưa khẳng định xử lý downstream đã hoàn tất.
4. **Snapshot/event:** schema envelope, TTL/invalidation và cách xử lý event gap/sai thứ tự theo `aggregateVersion`. `eventId` + Inbox xử lý duplicate; version là bắt buộc nhưng không thay thế Inbox.
5. **Status Project/Board:** status áp dụng cho cả hai là `ACTIVE`, `COMPLETED`, `CANCELLED`; cần chốt transition graph và tác động khi Project bị hủy/hoàn thành.
6. **Kỳ 2:** thiết kế service đích, ranh giới Git core/Workflow và database được hoãn đến khi bắt đầu Kỳ 2.
7. **Board preference:** giữ trải nghiệm Board mặc định theo user + Project từ demo; route tham khảo có trong API map. Service sở hữu preference, nơi lưu, kiểm tra membership và behavior khi Board/Project bị xóa chưa chốt.

Hiện chưa có dữ liệu legacy cần migration. Nếu sau này xuất hiện dữ liệu backend cần giữ, sẽ kiểm tra dữ liệu thực tế trước khi lập kế hoạch migration.

## 8. Tài liệu liên quan và thứ tự ưu tiên

- `docs/README.md` là chỉ mục tài liệu và ghi rõ tài liệu nào mô tả quyết định mục tiêu, API mục tiêu, runtime cũ hay mock demo.
- Ghi chú này là nguồn chuẩn cho quyết định backend Kỳ 1 đã thống nhất và các mục còn mở. Nó thay thế phần topology/permission backend trong demo spec nói Kỳ 1 có deployable `project-service`; phần React/MSW của spec vẫn là thiết kế demo.
- `README.md` tóm tắt sản phẩm, topology mục tiêu và chênh lệch giữa thiết kế với runtime; README không thay thế API contract.
- `docs/api/kanban-api.md` ghi route Project-first mục tiêu và giữ Board-first API cũ ở phần tham khảo. Payload, lỗi và hành vi còn mở tại mục 7 không được coi là contract đã duyệt.
- `docs/project/06-integration.md` là checklist tích hợp mục tiêu, không phải bằng chứng các luồng đã chạy.
- `docs/project/account-deletion-skeleton-guide.md` ghi hiện trạng scaffold và cách tiếp tục triển khai cascade theo từng service.
- `docs/project/01-user-service.md` đến `06-integration.md` và `services/*/docs/` đã được rà theo Project-first. Task sheets/docs riêng tách thiết kế mục tiêu khỏi code/schema runtime hiện có; các mục API còn mở vẫn cần chốt trước khi dùng làm implementation contract.
- React/MSW demo và các spec của nó chỉ là nguồn tham khảo cho UX/mock API. Các chi tiết backend trong demo không vượt quyền ưu tiên của ghi chú này.
