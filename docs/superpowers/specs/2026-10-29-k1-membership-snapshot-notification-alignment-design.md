# Kỳ 1 Membership, Authorization Snapshot và Notification — Design

**Ngày:** 2026-10-29
**Trạng thái:** Thiết kế đã được duyệt; skeleton đang được căn chỉnh theo thiết kế.
**Phạm vi:** Đồng bộ hợp đồng Kỳ 1, schema mục tiêu và skeleton đang có; Account deletion/cascade được chuyển sang Kỳ 2.

## 1. Mục tiêu

Đưa tài liệu và skeleton về cùng một thiết kế Kỳ 1 sau các quyết định mới nhất: quản lý role hệ thống trong user-service, Invitation theo Project, thu hồi quyền qua Redis snapshot, cleanup assignment an toàn khi Member rời rồi được thêm lại, và Notification thuộc về Account nhận.

Account deletion và cascade Project chưa triển khai trong Kỳ 1. Các lớp scaffold chỉ phục vụ quy trình này sẽ được gỡ khỏi code; Outbox/Inbox và cleanup assignment khi Member rời Project vẫn được giữ vì đó là nền tảng messaging dùng chung và nghiệp vụ Kỳ 1.

Tài liệu này là thiết kế, không xác nhận API, Redis hay RabbitMQ đã chạy end-to-end.

## 2. Baseline trước đợt căn chỉnh (2026-10-29)

Các gạch đầu dòng dưới đây ghi lại trạng thái được đối chiếu khi lập thiết kế, không mô tả runtime sau khi plan triển khai được duyệt.

- `EventEnvelope` trong `shared/event-contracts` đã có `aggregateType`; một số tài liệu chuẩn vẫn thiếu trường này.
- `EventTypes` và payload contracts hiện có các event Account/Project deletion. `messaging-support` là thư viện dùng chung chứa Outbox/Inbox/Relay.
- `board-service`, `user-service`, `task-service` và `notification-service` có các package deletion scaffold; chưa có luồng consumer/worker/API end-to-end.
- Cleanup membership hiện gửi `operationId`, `projectId`, `userId`, `boardIds`. `TaskDeletionJobEntity` không lưu generation membership; `CardEntity` lưu assignment trực tiếp ở `cards.assignee_id`.
- Notification runtime đã có `user_id` recipient và `board_id`/`card_id` làm context tùy chọn; chưa có `project_id`.
- Tài liệu đang mô tả snapshot được cập nhật qua RabbitMQ và Account deletion trong Kỳ 1, không khớp các quyết định dưới đây.
- Chưa thấy Redis client hoặc luồng authorization snapshot trong service runtime. Redis và các hợp đồng mới là thiết kế mục tiêu.

## 3. Quyết định đã xác nhận

1. **Account deletion:** hoãn toàn bộ luồng khỏi Kỳ 1. Gỡ code scaffold riêng cho xóa Account/cascade Project; giữ tài liệu định hướng để bàn kỹ ở Kỳ 2. Không triển khai hard delete hay soft delete Account trong Kỳ 1.
2. **Owner ở Kỳ 2:** hướng mục tiêu là soft-delete Account. Nếu Account là Owner, chuyển Owner sang Member đủ điều kiện có thời gian tham gia Project sớm nhất; mỗi Project luôn có đúng một Owner. Nếu không có Member kế nhiệm, chặn xóa Account cho đến khi có Member đủ điều kiện.
3. **Invitation:** chỉ mời Account đã tồn tại trong Kỳ 1. Invitation `PENDING` chưa cấp quyền; người nhận Accept thì mới tạo Membership. Mời email chưa đăng ký để Kỳ 2.
4. **Invitation inbox:** các route Invitation, kể cả inbox của người nhận, nằm dưới `/api/v1/projects` và do board-service sở hữu.
5. **Membership removal:** quyền được xem là đã thu hồi khi Redis apply snapshot mới. `202 Accepted` chỉ báo DB change và sync work đã được ghi bền, không báo operation thu hồi quyền đã hoàn tất; unassign là tác vụ nền độc lập, retry được, lọc theo `membershipVersion`, và lỗi cleanup không khôi phục quyền.
6. **Authorization snapshot:** Redis lưu snapshot Authentication và Authorization. `user-service` sở hữu snapshot Account/Authentication; `board-service` sở hữu snapshot Project/Membership Authorization. Task đọc snapshot trực tiếp từ Redis để kiểm tra. Gateway xác thực/định tuyến và không sở hữu snapshot.
7. Khi dữ liệu Authentication/Authorization đổi, service sở hữu nguồn chuẩn phải thu hồi snapshot cũ và ghi snapshot mới. RabbitMQ không phải cơ chế duy nhất để thực hiện revoke có hiệu lực tức thời.
8. **Event envelope:** hợp đồng chuẩn có đủ `eventId`, `eventType`, `schemaVersion`, `aggregateType`, `aggregateId`, `aggregateVersion`, `occurredAt`, `payload`.
9. **System role:** role hệ thống được lưu trong `SystemRoles`; việc gán role cho Account nằm trong bảng liên kết của user-service. System Admin không có quyền nghiệp vụ mặc định trên Project.
10. **Notification:** Notification thuộc về Account nhận. Một Account có thể nhận Notification từ nhiều Project/Board hoặc Notification hệ thống không có context Project.
11. **Outbox/Inbox:** tiếp tục giữ module messaging dùng chung và mapping riêng trong database của từng service.
12. **Assignment schema:** giữ target table `TaskAssignees` trong SQL starter; mỗi assignment lưu `membershipVersion`. Runtime persistence sẽ align về bảng assignment này, không tự mở API multi-assignee trong Kỳ 1.
13. **Task cleanup fence:** thêm fence bền trong `task_db`; ghi cùng Inbox và durable cleanup job trước ACK, đồng thời serialize assignment writes với fence.
14. **Redis synchronization:** commit thay đổi Authentication/Authorization, Outbox và snapshot sync job trong DB trước, sau đó retry cập nhật Redis. Revoke có hiệu lực khi Redis apply snapshot mới; DB/Outbox commit tự nó chưa phải revoke.
15. **Notification context:** mỗi Notification có tối đa một Project context và tối đa một Board/Card context; inbox tổng hợp Notification của Account trên nhiều Project/Board.
16. **API khi Redis pending:** trả `202 Accepted` cùng `operationId` và `statusUrl`; operation chỉ hoàn tất khi Redis apply được xác nhận.
17. **Project context của Card API:** các route Card mang `projectId` trong URL; Task lưu `project_id` trên Card để kiểm tra Project scope và đọc Membership snapshot mà không gọi internal REST.

## 4. Các hướng đã cân nhắc

### 4.1 Account deletion trong Kỳ 1

- **Triển khai cascade ngay:** giữ Account/Project deletion operations, các consumer/job và write lock. Trade-off: mở rộng đáng kể phạm vi Kỳ 1 và buộc chốt retention/cascade qua nhiều service.
- **Hoãn toàn bộ Account deletion (đã chọn):** gỡ scaffold xóa riêng khỏi runtime Kỳ 1; giữ Outbox/Inbox và ghi chính sách Owner succession thành chủ đề Kỳ 2. Trade-off: Admin chưa có chức năng xóa Account ở Kỳ 1.

### 4.2 Cleanup assignment khi Member rời rồi tham gia lại

- **Chặn rejoin/assignment đến khi cleanup xong:** dễ biểu diễn nhưng làm trải nghiệm bị khóa trong thời gian xử lý nền.
- **Lưu `membershipVersion` trên assignment cùng Task-side fence (đã chọn):** một cleanup chỉ nhắm assignment của đúng vòng đời membership; fence chặn assignment cũ đang bay qua cleanup. Member có thể tham gia lại với version mới. Cần version bền vững tại board-service và serialize fence/assignment trong task_db.

### 4.3 Cập nhật Authorization snapshot

- **Chỉ phát event RabbitMQ để cập nhật Redis:** tách service tốt nhưng broker confirm không chứng minh Redis apply; khó xác định khi nào operation revoke được báo hoàn tất.
- **DB + Outbox trước, Redis sau (đã chọn):** user-service và board-service commit dữ liệu nguồn cùng Outbox/snapshot-sync job trước; sau đó retry ghi snapshot phiên bản mới vào Redis. Trade-off: có một khoảng đồng bộ cần được biểu diễn bền vững; broker confirm không chứng minh Redis đã áp dụng snapshot.

### 4.4 Notification context

- **Gắn Notification vào Project:** không phù hợp vì Notification thuộc về Account và Account có thể theo dõi nhiều Project.
- **Gắn Notification vào recipient Account, context Project/Board tùy chọn (đã chọn):** inbox thuộc Account; context chỉ là tham chiếu scalar tới Project/Board, hoặc rỗng với Notification hệ thống. Không tạo FK chéo database.

## 5. Thiết kế mục tiêu Kỳ 1

### 5.1 Phân quyền và Redis snapshot

- `user-service` là nguồn chuẩn cho Account, trạng thái đăng nhập và SystemRoles. `board-service` là nguồn chuẩn cho Project, Invitation và Membership.
- Snapshot Account do user-service sở hữu chứa tối thiểu `accountId`, trạng thái Account, danh sách `systemRoles` và `authVersion`.
- Snapshot Project membership chứa tối thiểu `projectId`, `userId`, trạng thái membership, role, `membershipVersion` và `authorizationVersion`.
- Task-service kiểm tra snapshot Account và snapshot Membership trong Redis trước thao tác cần quyền. Snapshot thiếu, hết hạn mà chưa xác minh được, hoặc Redis không truy cập được thì từ chối thao tác (fail closed).
- Mỗi thay đổi trạng thái phân quyền làm tăng `authorizationVersion` và ghi Outbox trong cùng DB transaction. `authorizationVersion` cũng là `EventEnvelope.aggregateVersion` của aggregate Membership. `membershipVersion` chỉ là generation của lần Membership đang hoạt động: giữ nguyên khi revoke, tăng khi Accept tạo lần tham gia mới. Redis compare-and-set, unique key của snapshot job và thứ tự retry phải dùng `authorizationVersion`, không dùng `membershipVersion`, để retry snapshot ACTIVE không ghi đè snapshot REMOVED cùng generation.
- Cần trạng thái đồng bộ bền vững (ví dụ snapshot update receipt/job với trạng thái `PENDING`/`SYNCED`/`SUPERSEDED`) được ghi trong **cùng transaction** với source mutation và Outbox; nếu không, process crash sau commit có thể để lại snapshot stale mà không có retry job. Outbox `SENT` chỉ chứng minh broker nhận event, không chứng minh Redis đã cập nhật. Mọi API thay đổi Authentication/Authorization chỉ báo hoàn tất sau khi Redis xác nhận đúng snapshot đã được áp dụng. Sync job giữ `operationId` để nối status request với kết quả bền vững. Khi DB + Outbox + sync job đã commit nhưng Redis còn `PENDING`, API trả `202 Accepted` + `operationId`/`statusUrl`; job `SYNCED` ánh xạ thành operation `COMPLETED` sau khi apply hoặc xác nhận cùng version đã có. Nếu bị version mới hơn vượt qua, cả job và operation có status `SUPERSEDED`, không phải `COMPLETED`.
- Khi Redis không truy cập được, Task không thể kiểm tra snapshot nên từ chối request (fail closed). Nếu DB đã commit mà Redis vẫn đọc được snapshot cũ trong lúc retry chưa áp dụng, Task có thể tiếp tục thấy snapshot đó; anh chốt mốc revoke có hiệu lực khi Redis apply, do chưa triển khai bảo mật giao tiếp nội bộ để Task kiểm tra nguồn chuẩn.
- Version/CAS ngăn lần retry/event cũ ghi đè snapshot mới hơn nhưng không chặn lần đọc snapshot cũ trước khi Redis apply. Khoảng đồng bộ này là giới hạn đã chấp nhận của phương án Kỳ 1; không báo operation hoàn tất trước khi Redis xác nhận.
- Redis compare-and-set phân biệt snapshot vừa được ghi, cùng version đã tồn tại và snapshot bị version cao hơn vượt qua. Job cũ bị vượt qua chuyển `SUPERSEDED`; nó không xác nhận rằng snapshot gỡ quyền đó từng có hiệu lực. Operation trạng thái quyền tương ứng báo `SUPERSEDED`, không `COMPLETED`; client tải lại Membership hiện tại. Sync job liên kết operation bằng `operationId`; status route và quyền đọc operation vẫn là API TODO.
- Gateway không tự tra/ghi Membership snapshot. Gateway xác thực Account, tự suy ra identity context từ token hợp lệ và ghi đè/xóa identity header do client gửi; Task vẫn kiểm tra Authorization snapshot theo Project.

#### Luồng gỡ Membership

1. `board-service` ghi Membership `REMOVED`, giữ `membershipVersion` của generation đang bị thu hồi, tăng `authorizationVersion`, rồi ghi Outbox và snapshot-sync job trong cùng transaction. Operation thu hồi quyền ở trạng thái `PENDING`.
2. API trả `202 Accepted` cùng `operationId` và `statusUrl`; đây là xác nhận request đã được ghi bền, không phải xác nhận quyền đã bị thu hồi.
3. Snapshot-sync worker retry Redis. Operation chuyển sang `COMPLETED` khi Redis xác nhận snapshot mới đã apply. Đây là mốc hoàn tất theo bảo đảm hiện tại; không bảo đảm mọi request ngừng được chấp nhận ngay tại thời điểm DB commit. Nếu Redis không truy cập được, Task fail closed; nếu Redis vẫn đọc được snapshot cũ trong khi retry, snapshot đó còn có thể được dùng đến khi apply mới.
4. Unassign Member khỏi Card là tác vụ nền độc lập. Tác vụ có job bền vững và retry; chỉ xóa assignment của `membershipVersion` vừa bị thu hồi. Nó không chặn operation thu hồi quyền chuyển sang `COMPLETED`, và lỗi unassign không khôi phục quyền đã thu hồi.

**Redis key layout dưới đây là đề xuất triển khai, không phải tên wire/API đã chốt:**

```text
authn:account:{userId} -> { status, systemRoles, authVersion }
authz:project:{projectId}:user:{userId}
  -> { status, role, membershipVersion, authorizationVersion }
```

SystemRoles vẫn có nguồn chuẩn trong `user_db`; user-service kiểm tra `SYSTEM_ADMIN` tại các API quản trị Account bằng dữ liệu của chính service đó. Snapshot chia sẻ giúp downstream nhận trạng thái Account/role mới nhất sau khi apply; Task cần trạng thái Account và Project Membership, không dùng `SYSTEM_ADMIN` để cấp quyền Project.

Service ghi snapshot phải dùng so sánh version nguyên tử hoặc cơ chế tương đương để snapshot cũ không ghi đè snapshot mới. Chi tiết TTL, Redis script/transaction và reconciliation khi DB commit thành công nhưng Redis write lỗi sẽ được chốt trong kế hoạch triển khai; mọi đường đọc quyền phải fail closed trong khoảng chưa đồng bộ.

### 5.2 Membership version và assignment cleanup

- `membershipVersion` là số tăng theo **vòng đời Membership đang hoạt động**, khác với `eventId` và `aggregateVersion`. Remove giữ nguyên version của Membership bị thu hồi; Accept/rejoin tạo generation mới và tăng version.
- `authorizationVersion` là version trạng thái phân quyền tăng trên mọi thay đổi tạo snapshot mới, gồm ACTIVE/REMOVED, đổi role và rejoin. Nó bằng `EventEnvelope.aggregateVersion` của aggregate `(projectId,userId)`, được lưu riêng trong `project_membership_versions`, và là version duy nhất dùng cho Redis CAS/job dedup. Durable cleanup job cũng lưu `sourceAggregateVersion` từ request envelope để completion không nhầm generation với aggregate version.
- board-service giữ cả hai bộ đếm bền vững cho cặp `(projectId,userId)` qua các trạng thái `ACTIVE` và `REMOVED`; `rowVersion` JPA chỉ là optimistic-lock version, không thay thế hai version nghiệp vụ.
- Khi gán Card, Task-service đọc `membershipVersion` từ Authorization snapshot và lưu `membership_version BIGINT NOT NULL` trên assignment row trong `task_assignees`. Skeleton hiện đã có mapping target này; API Kỳ 1 vẫn hỗ trợ một assignee.
- `member.assignments.cleanup-requested` mang `operationId`, `projectId`, `userId`, `boardIds`, `membershipVersion`; envelope `aggregateVersion` là `authorizationVersion`. Event xác nhận cleanup có `eventId` mới nhưng giữ `operationId`, `membershipVersion` và source `aggregateVersion` để board-service đối chiếu.
- Task-service chỉ clear assignment khi đồng thời khớp Board scope, User ID và `membershipVersion` của request. Cleanup cũ version 1 không thể clear assignment version 2 sau khi người dùng tham gia lại.
- `aggregateId` của aggregate Membership là định danh ổn định `(projectId,userId)`; `aggregateVersion`/`authorizationVersion` tăng theo thay đổi trạng thái phân quyền. Rejoin không nhất thiết sinh aggregateId mới. `eventId` chỉ dedupe một event cụ thể.
- Consumer ghi Inbox và durable cleanup job trong cùng transaction rồi mới ACK. Job và thay đổi assignment có thể retry; xử lý cùng eventId nhiều lần không làm thay đổi kết quả đã hoàn tất.

Task-service ghi fence bền trong `task_db` theo `(projectId,userId,membershipVersion)`. Consumer ghi Inbox, fence và durable cleanup job trong cùng transaction trước ACK. Assignment write và fence check phải được tuần tự hóa trong cùng database transaction bằng row lock hoặc atomic upsert trên cùng khóa: nếu assignment commit trước fence thì cleanup sẽ thấy và gỡ nó; nếu fence commit trước thì assignment version đó bị từ chối. Worker chỉ quét sau khi fence transaction commit. Fence chỉ đóng race này ở Task DB; Redis apply là mốc hiệu lực revoke theo bảo đảm hiện tại. Cleanup thất bại không thay đổi Membership hoặc snapshot đã bị thu hồi.

#### Project context cho Card API

Các route mục tiêu đưa Card vào Project scope:

| Method | Route |
|---|---|
| `GET`, `POST` | `/api/v1/projects/{projectId}/boards/{boardId}/cards` |
| `GET` | `/api/v1/projects/{projectId}/cards/{cardId}` |
| `PATCH` | `/api/v1/projects/{projectId}/cards/{cardId}/move` |
| `PATCH` | `/api/v1/projects/{projectId}/cards/{cardId}/assignee` |
| `GET`, `POST` | `/api/v1/projects/{projectId}/cards/{cardId}/comments` |

`projectId` lấy từ path, không nhận thêm trong body. `cards.project_id` là scalar reference trong `task_db`, không có FK chéo database; các thao tác trên Card đã tồn tại phải khớp `projectId` trong path với `cards.project_id`. Đây là context để kiểm tra Membership snapshot và giới hạn truy vấn/cleanup theo Project. Việc xác minh Board và Column truyền khi tạo Card có thuộc Project trên URL hay không vẫn cần một Board-scope integration đáng tin cậy; không coi `projectId` do client gửi là bằng chứng Board/Column thuộc Project. Internal REST hiện được hoãn, nên phần xác minh này phải giữ thành integration TODO trước khi bật luồng đầy đủ.

### 5.3 Event envelope và messaging

Envelope chuẩn:

```json
{
  "eventId": "uuid",
  "eventType": "membership.removed",
  "schemaVersion": 1,
  "aggregateType": "PROJECT_MEMBERSHIP",
  "aggregateId": "{projectId}:{userId}",
  "aggregateVersion": 12,
  "occurredAt": "2026-10-29T00:00:00Z",
  "payload": {}
}
```

Các domain event Kỳ 1 dùng Outbox của service sở hữu nguồn dữ liệu:

| Constant | Wire `eventType` | Aggregate | Payload tối thiểu |
|---|---|---|---|
| `USER_STATUS_CHANGED` | `user.status.changed` | `USER_ACCOUNT`, ID là `userId`; aggregate version là `authVersion` | `userId`, `status`, `authVersion` |
| `USER_SYSTEM_ROLES_CHANGED` | `user.system-roles.changed` | `USER_ACCOUNT`, ID là `userId`; aggregate version là `authVersion` | `userId`, `systemRoles`, `authVersion` |
| `MEMBERSHIP_CHANGED` | `project.membership.changed` | `PROJECT_MEMBERSHIP`, ID ổn định `{projectId}:{userId}`, `aggregateVersion=authorizationVersion` | `projectId`, `userId`, `status`, `role`, `membershipVersion` |
| `MEMBERSHIP_REMOVED` | `project.membership.removed` | `PROJECT_MEMBERSHIP`, ID ổn định `{projectId}:{userId}`, `aggregateVersion=authorizationVersion` | `projectId`, `userId`, `membershipVersion` |
| `MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED` | `member.assignments.cleanup-requested` | `PROJECT_MEMBERSHIP`, ID ổn định `{projectId}:{userId}`, source `aggregateVersion=authorizationVersion` | `operationId`, `projectId`, `userId`, `boardIds`, `membershipVersion` |

Các event trạng thái/role/membership mang version nguồn để consumer phát hiện snapshot cũ; chúng không thay thế snapshot-sync job ghi trong DB nguồn. Với Membership, `authorizationVersion` là `aggregateVersion`, còn payload `membershipVersion` chỉ nhận diện generation assignment. `USER_SYSTEM_ROLES_CHANGED` được phát khi role hệ thống đổi dù trạng thái Account không đổi. `MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED` là yêu cầu dọn assignment riêng; completion dùng eventId mới nhưng giữ `operationId`, `membershipVersion` cùng source `aggregateVersion`.

- `eventId` nhận diện event để Inbox loại lần giao lại.
- `aggregateType` và `aggregateId` xác định aggregate sở hữu version.
- `aggregateVersion` giúp phát hiện event cũ/gap theo quy tắc của consumer; không thay thế Inbox và không tự chứng minh hai event khác ID là cùng command.
- Retry Outbox gửi lại cùng event và giữ nguyên `eventId`/version/payload.
- Giữ `shared/event-contracts` và `shared/messaging-support`. Gỡ event contract Account/Project deletion khỏi code Kỳ 1; giữ event cleanup assignment Membership.

### 5.4 SystemRoles

Schema mục tiêu trong `user_db`:

```text
system_roles(role_code PK, description)
user_system_roles(user_id FK users, role_code FK system_roles,
                  granted_at, granted_by_user_id, PK(user_id, role_code))
```

`USER` và `SYSTEM_ADMIN` là role code khởi điểm. Role do user-service cấp/thu hồi; request tạo Account không nhận role từ client. Quan hệ Project `OWNER`/`MEMBER` nằm trong board-service và không dùng `SystemRoles`.

Bootstrap System Admin đầu tiên và chính sách một Account vừa là System Admin vừa tham gia Project chưa được quyết định trong trao đổi; kế hoạch phải giữ chúng thành câu hỏi mở thay vì tự suy diễn.

### 5.5 Invitation API

API mục tiêu thuộc board-service và được Gateway định tuyến theo `/api/v1/projects/**`:

| Method | Route | Ý nghĩa |
|---|---|---|
| `POST` | `/api/v1/projects/{projectId}/invitations` | Owner gửi Invitation tới Account đã tồn tại |
| `GET` | `/api/v1/projects/invitations` | Người dùng hiện tại xem inbox Invitation của mình |
| `POST` | `/api/v1/projects/{projectId}/invitations/{invitationId}/accept` | Người nhận Accept; tạo Membership cùng transaction |
| `POST` | `/api/v1/projects/{projectId}/invitations/{invitationId}/reject` | Người nhận Reject |
| `GET` | `/api/v1/projects/{projectId}/members` | Xem thành viên theo quyền Project |
| `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Owner gỡ Member; Member tự rời; `202` + operation handle khi Redis snapshot còn pending, hoàn tất sau Redis apply |

`GET /api/v1/projects/invitations` lấy người nhận từ identity đã xác thực, không nhận recipient ID tùy ý trong query/body. Invitation `PENDING` không cấp quyền; Accept mới tạo Membership và version hoạt động mới. Không cho Owner tự gỡ Owner duy nhất; lỗi nghiệp vụ cụ thể và expiry/cancel policy vẫn theo API contract hiện hành hoặc mục còn mở.

### 5.6 Notification ownership và database

- notification-service sở hữu `notifications`; mỗi row thuộc một recipient Account (`user_id` UUID scalar, không FK chéo DB).
- Bổ sung `project_id BIGINT NULL` làm context cho Notification Project-level. Giữ `board_id BIGINT NULL`, `card_id BIGINT NULL` cho context chi tiết. Notification hệ thống/account-level có thể không có Project/Board/Card context.
- Index inbox theo recipient và thứ tự thời gian; query không yêu cầu truy cập database Project/Board.
- Notification history không bị cascade khi Project/Board kết thúc; Account/Project deletion và retention được bàn ở Kỳ 2.

Mỗi Notification có tối đa một Project context và một Board/Card context; Account inbox có thể tổng hợp nhiều Project/Board. Notification hệ thống không có context vẫn thuộc recipient Account.

### 5.7 Account deletion và Owner succession — Kỳ 2

Kỳ 1 không có endpoint/consumer/job/status `DELETING` cho Account deletion và không có Project cascade. Tài liệu Kỳ 2 giữ các quyết định định hướng sau, không mô tả chúng như runtime Kỳ 1:

1. Account deletion là soft delete; giữ định danh và attribution lịch sử theo retention policy sẽ được bàn ở Kỳ 2.
2. Nếu Account là Owner, chuyển Owner cho Account đang là Member đủ điều kiện có `joinedAt` sớm nhất.
3. Mỗi Project phải có đúng một Owner. Không cho xóa Owner Account nếu chưa có Member đủ điều kiện để tiếp nhận vai trò.
4. Project mà Account chỉ là Member không bị xóa khi Account bị xóa; membership bị thu hồi và assignment xử lý theo policy Kỳ 2.
5. Hard delete và cascade toàn bộ Project/Board/Card/Comment/Notification không thuộc quyết định Kỳ 1.

### 5.8 Code Kỳ 1 cần giữ/gỡ

**Giữ:** `shared/event-contracts` envelope chuẩn; `shared/messaging-support` Outbox/Inbox/Relay; membership assignment cleanup contract/job; Notification recipient model; service DB riêng.

**Gỡ khỏi runtime Kỳ 1:**

- user-service: Account deletion use case/operation/tombstone, Account `DELETING` transition và event/wiring riêng.
- board-service: Project deletion coordinator/operation và event cascade.
- task-service: Project cascade handler/job type, Board write lock/guard chỉ phục vụ cascade. Giữ job/handler cleanup assignment cho Member rời Project.
- notification-service: Project notification deletion job/handler.
- shared event contracts/types dành riêng cho Account/Project deletion; không gỡ Outbox/Inbox dùng chung.

Nếu một class hoặc config hiện tại dùng chung giữa cascade và Membership cleanup, chỉ bỏ nhánh Project cascade sau khi đã trace caller; không xóa đường cleanup Member.

## 6. Phạm vi tài liệu và code cần đồng bộ sau khi duyệt

- Nguồn chuẩn chung: `README.md`, `docs/README.md`, `docs/project/architecture-decisions.md`, `docs/api/kanban-api.md`, `docs/project/06-integration.md`.
- Tài liệu theo service: `docs/project/01-user-service.md`, `02-board-service.md`, `03-task-service.md`, `05-gateway.md`; `services/{user,board,task,notification}-service/docs/{api,database}.md`; `gateway/README.md` và `gateway/docs/routing.md`; `shared/README.md`.
- Kế hoạch/task sheets và account deletion guide phải được gắn đúng phạm vi Kỳ 1/Kỳ 2, không còn hướng dẫn chạy scaffold deletion như chức năng Kỳ 1.
- Code cần cập nhật/xóa theo mục 5.8; Task assignment persistence và cleanup payload/job cần có `membershipVersion`; envelope Java hiện đã có `aggregateType`.
- Giữ các thay đổi người dùng chưa commit đang có trong working tree; sửa có mục tiêu theo diff, không restore hàng loạt.

## 7. Rủi ro và điểm để người duyệt/builder xác nhận

1. **Status route/response:** đã chốt `202 Accepted` + `operationId`/`statusUrl` khi Redis sync pending; tên route status cụ thể, authorization của status lookup và retry fields cần được định nghĩa trong API contract.
2. **System role:** cách bootstrap System Admin đầu tiên và khả năng một Account đồng thời có System Admin + Project role còn mở.
3. **API lifecycle:** status Project/Board, Invitation expiry và tập error code cụ thể chưa được chốt bởi quyết định trong tài liệu này.
4. **Schema rollout:** chưa chọn công cụ migration; schema thiết kế cần versioned migration ở service sở hữu DB, không dùng `ddl-auto=update` làm versioning.

## 8. Tiêu chí nghiệm thu tài liệu/triển khai

- Tài liệu nói rõ Account deletion/cascade thuộc Kỳ 2; không còn mô tả các route/job deletion như Kỳ 1.
- Tất cả event envelope docs và code contract ghi `aggregateType`.
- Mọi nơi mô tả Invitation dùng cùng Project route namespace và Accept mới tạo Membership.
- Role hệ thống được mô hình hóa bằng `SystemRoles` và bảng gán role trong user_db.
- Notification ownership là recipient Account, có context Project/Board tùy chọn, không bị xóa theo Project trong Kỳ 1.
- Assignment lưu membershipVersion; cleanup payload/job/query giới hạn đúng version và Inbox được commit cùng durable job trước ACK.
- Redis snapshot ownership, Task read path, operation completion sau Redis apply và fail-closed được mô tả nhất quán ở tài liệu chung, service, Gateway và Shared.
- Outbox/Inbox dùng chung vẫn còn, mỗi service tiếp tục sở hữu DB riêng.

## 9. Approaches and selection

| Vấn đề | Hướng được chọn | Trade-off |
|---|---|---|
| Account deletion | Hoãn luồng runtime đến Kỳ 2 | Giảm rủi ro/phạm vi Kỳ 1; Admin chưa xóa Account được |
| Assignment cleanup race | `membershipVersion` trên `TaskAssignees` và Task-side fence (đã chọn) | Cho phép rejoin ngay; cần version bền vững và transaction fence |
| Redis snapshot | DB + Outbox + sync job commit trước; source service retry Redis; Task đọc; Gateway không sở hữu | Revoke có hiệu lực tại Redis apply; `202`/status URL biểu diễn thời gian chờ; Task có thể đọc snapshot cũ trong thời gian đó |
| Notification | Recipient Account sở hữu; mỗi row tối đa một Project và Board/Card context | Inbox tổng hợp nhiều Project/Board mà không cần quan hệ nhiều context cho một row |
