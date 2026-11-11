# Shared modules

`shared/` chứa thư viện Maven được các service dùng chung; đây **không** phải service triển khai riêng và cũng **không** phải database dùng chung.

Ở đây, “shared” nói về **code Java dùng chung** cho event contract và messaging. Nó khác với Redis Authentication/Authorization Snapshot: Redis vẫn nằm trong kiến trúc mục tiêu Kỳ 1, nhưng hiện chưa có Redis client, cache model hay consumer cập nhật snapshot trong module này. Xem [quyết định kiến trúc](../docs/project/architecture-decisions.md).

## Đọc theo thứ tự

| Module | Tác dụng | Phụ thuộc |
|---|---|---|
| [`event-contracts`](event-contracts/pom.xml) | Các record Java cho envelope và payload domain event | Không phụ thuộc Spring/Rabbit/JPA |
| [`messaging-support`](messaging-support/pom.xml) | Ghi Outbox, ghi Inbox, persistence mapping và Outbox Relay | `event-contracts`, Spring AMQP, Spring Data JPA, Jackson |

Luồng phụ thuộc hiện tại:

```text
service → messaging-support → event-contracts
```

Các service khai báo dependency `messaging-support`, import `MessagingSupportConfiguration`, và scan `shared.messaging.persistence.entity` cùng repository. Vì vậy entity shared được map vào **database riêng của từng service** (`user_db`, `board_db`, `task_db`, `notification_db`). Không có schema `shared_db`.

### Lộ trình đọc code

1. Đọc [`EventEnvelope`](event-contracts/src/main/java/dev/platform/contracts/EventEnvelope.java), [`EventTypes`](event-contracts/src/main/java/dev/platform/contracts/EventTypes.java) và một [payload](event-contracts/src/main/java/dev/platform/contracts/event/MemberAssignmentsCleanupRequested.java) để hiểu dữ liệu trao đổi.
2. Đọc [`MessagingSupportConfiguration`](messaging-support/src/main/java/dev/platform/shared/messaging/MessagingSupportConfiguration.java) và [`UserServiceApplication`](../services/user-service/src/main/java/dev/platform/user/UserServiceApplication.java) để xem service đăng ký library và các JPA mapping.
3. Đọc [`OutboxEventWriter`](messaging-support/src/main/java/dev/platform/shared/messaging/OutboxEventWriter.java), [`OutboxRelay`](messaging-support/src/main/java/dev/platform/shared/messaging/OutboxRelay.java) và [`InboxEventRecorder`](messaging-support/src/main/java/dev/platform/shared/messaging/InboxEventRecorder.java).
4. Theo dõi ví dụ [Board gửi yêu cầu cleanup và Task xử lý](#ví-dụ-đọc-code-board-yêu-cầu-cleanup-task-xử-lý) bên dưới. Chú ý đây là các mảnh code hiện có, chưa phải luồng đã nối end-to-end.

## Service dùng shared trong code như thế nào?

Service khai báo dependency Maven trong `pom.xml`; ví dụ [`board-service/pom.xml`](../services/board-service/pom.xml):

```xml
<dependency>
    <groupId>dev.platform</groupId>
    <artifactId>messaging-support</artifactId>
    <version>${project.version}</version>
</dependency>
```

`messaging-support` phụ thuộc `event-contracts`, vì vậy service cũng có thể import `EventEnvelope` và các payload. Đây là lời gọi thư viện trong cùng tiến trình Java, không phải HTTP request tới một shared service.

Ví dụ cấu hình trong `UserServiceApplication` (rút gọn):

```java
@SpringBootApplication
@EntityScan(basePackages = {
        "dev.platform.user.infrastructure.persistence.entity",
        "dev.platform.shared.messaging.persistence.entity"
})
@EnableJpaRepositories(basePackages = {
        "dev.platform.user",
        "dev.platform.shared.messaging.persistence.repository"
})
@Import(MessagingSupportConfiguration.class)
public class UserServiceApplication { /* ... */ }
```

`@EntityScan` và `@EnableJpaRepositories` cho Spring tìm mapping và repository shared; `@Import` đăng ký `OutboxEventWriter`, `InboxEventRecorder` và cấu hình relay. Code shared chạy trong service và sử dụng datasource của service đó. Riêng relay chỉ được tạo khi bật các cờ được nêu trong [phần cấu hình](#bậttắt-và-trạng-thái-hiện-tại).

### Ví dụ đọc code: Board yêu cầu cleanup, Task xử lý

**Trạng thái:** ví dụ dưới đây lần theo code đã có và điểm nối dự kiến. Recorder Board chưa chứng minh đã được gọi từ luồng cập nhật Membership; Task có queue/binding/listener cho event cleanup đã duyệt nhưng consumer mặc định tắt và chưa có broker E2E. Không coi sơ đồ là bằng chứng luồng đã chạy hoàn chỉnh.

```text
board-service: ProjectAuthorizationSnapshotSyncRecorder
    → ghi snapshot-sync job + Outbox trong transaction của recorder
    → OutboxRelay có thể gửi event tới RabbitMQ khi được cấu hình
    → RabbitMQ route event tới queue cleanup của task-service khi consumer được bật
task-service: MemberAssignmentCleanupRecorder
    → ghi Inbox + fence + cleanup job trong transaction
    → MemberAssignmentCleanupWorker xử lý job và ghi Outbox completion
```

1. **Contract:** [`MemberAssignmentsCleanupRequested`](event-contracts/src/main/java/dev/platform/contracts/event/MemberAssignmentsCleanupRequested.java) chứa `operationId`, `projectId`, `userId`, `boardIds` và `membershipVersion`. [`EventEnvelope`](event-contracts/src/main/java/dev/platform/contracts/EventEnvelope.java) bọc payload cùng `eventId`, tên event, phiên bản schema, loại/ID/phiên bản aggregate và thời điểm phát sinh. `eventId` dùng nhận diện event giao lại; `aggregateVersion` không thay thế Inbox.

2. **Board ghi Outbox:** [`ProjectAuthorizationSnapshotSyncRecorder.recordMembershipRemoved`](../services/board-service/src/main/java/dev/platform/board/infrastructure/snapshot/ProjectAuthorizationSnapshotSyncRecorder.java) tạo snapshot-sync job và hai event, gồm `MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED`. Method helper bọc payload vào envelope rồi gọi shared:

   ```java
   outbox.append(new EventEnvelope<>(UUID.randomUUID(), eventType, 1,
           "PROJECT_MEMBERSHIP", aggregateId, version, now, payload));
   ```

   [`OutboxEventWriter.append`](messaging-support/src/main/java/dev/platform/shared/messaging/OutboxEventWriter.java) chuyển envelope thành JSON và lưu event `PENDING` vào `board_db.outbox_events`. Method yêu cầu transaction sẵn có (`MANDATORY`). Recorder có `@Transactional`, nhưng thay đổi Membership phải được caller nối vào cùng transaction để đạt tính nguyên tử; điều đó chưa được chứng minh chỉ bằng sự tồn tại của recorder.

3. **Relay:** khi được bật và cấu hình broker đúng, [`OutboxRelay`](messaging-support/src/main/java/dev/platform/shared/messaging/OutboxRelay.java) đọc Outbox của chính service, publish tới `dev-platform.domain-events` với routing key là `eventType`, rồi kiểm tra confirm/return. `SENT` không có nghĩa consumer đã xử lý xong. Xem [Outbox](#outbox) để biết retry và giới hạn hiện tại.

4. **Task nhận yêu cầu:** [`MemberAssignmentCleanupListener`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupListener.java) bind durable queue theo routing key đã duyệt. Listener giải mã envelope, kiểm tra contract và gọi [`MemberAssignmentCleanupRecorder.requestMemberAssignmentCleanup`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupRecorder.java). Container dùng AUTO ACK sau khi recorder transaction commit; JSON/contract sai phát tín hiệu reject không requeue. Shared factory áp dụng một lần xử lý đầu, tối đa 3 lần retry cách cố định 5 giây, rồi reject không requeue để DLX route vào DLQ. Consumer và topology của Task mặc định tắt.

   ```java
   if (!inbox.recordIfNew(CONSUMER_NAME, event.eventId(), event.eventType())) {
       return;
   }
   // Sau đó ghi fence và cleanup job trong cùng transaction.
   ```

   Inbox phân biệt theo `(consumerName, eventId)`. Cleanup job còn có khóa nghiệp vụ theo Project, User và `membershipVersion`; fence giúp phối hợp cleanup với assignment. Nếu ACK lỗi/mất sau commit, RabbitMQ có thể giao lại; Inbox làm delivery lặp idempotent. Shared sở hữu retry policy, listener factory và DLX exchange; mỗi service sở hữu queue/DLQ và binding riêng. Source policy chưa được nghiệm thu với RabbitMQ thật trong thay đổi này.

5. **Worker:** [`MemberAssignmentCleanupWorker`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupWorker.java) có code đọc job, xóa assignment theo batch và ghi event `MEMBER_ASSIGNMENTS_CLEANED` qua `OutboxEventWriter`. Worker chỉ được tạo khi bật `dev-platform.assignment-cleanup.enabled`. Việc có worker không chứng minh cleanup hoặc completion đã chạy end-to-end.

### Phân công Gateway và tích hợp giữa các service

Đây là phạm vi công việc Kỳ 1 đã thống nhất, không có nghĩa các luồng đã chạy end-to-end. Người phụ trách Gateway cũng chủ trì chuẩn tích hợp chung; mỗi nhóm service chịu trách nhiệm nối nghiệp vụ và kiểm thử phần của mình.

| Người phụ trách | Phạm vi |
|---|---|
| Gateway và tích hợp chung | Hoàn thiện xác thực token, kiểm tra Account hợp lệ, định tuyến và loại bỏ/ghi đè identity header do client gửi. Chủ trì contract Redis snapshot, event contract, RabbitMQ exchange/queue/binding và cách dùng Outbox/Inbox; phối hợp kiểm thử liên service. Gateway không sở hữu Account, Membership hay snapshot. |
| user-service | Account và system role là dữ liệu nguồn. Nối thay đổi Account/role với Outbox và durable Account snapshot-sync job trong cùng transaction; worker cập nhật Redis theo `authVersion` và retry. |
| board-service | Project, Membership, Invitation, Board và Column là dữ liệu nguồn. Nối thay đổi Membership với Outbox và durable Project snapshot-sync job trong cùng transaction; worker cập nhật Redis theo `authorizationVersion`, xử lý operation thu hồi quyền. |
| task-service | Card, assignment và comment. Đọc Account/Project snapshot từ Redis để kiểm tra quyền; nối RabbitMQ listener với Inbox, fence và durable cleanup job, xử lý unassign nền. |
| notification-service | Notification thuộc Account nhận. Tiếp nhận event liên quan, chống xử lý trùng và tạo thông báo theo contract được chốt. |

Redis và RabbitMQ là hạ tầng tích hợp; code cụ thể phải được nối vào transaction và use case của service sở hữu dữ liệu, không chuyển toàn bộ trách nhiệm đó vào Gateway. Internal REST để giao tiếp nội bộ vẫn hoãn cho đến khi có bảo mật phù hợp. Khi gỡ Member, operation thu hồi quyền chỉ hoàn tất sau khi Redis xác nhận apply snapshot; cleanup assignment chạy nền độc lập, không khôi phục quyền khi thất bại.

### Chỗ đọc code của từng service

| Service | Chỗ đọc code | Hiện trạng cần lưu ý |
|---|---|---|
| User | [`AccountAuthorizationSnapshotSyncRecorder`](../services/user-service/src/main/java/dev/platform/user/infrastructure/snapshot/AccountAuthorizationSnapshotSyncRecorder.java) | Có code ghi snapshot job và Outbox khi status/role thay đổi; chưa chứng minh đã nối vào toàn bộ lifecycle Account. |
| Board | [`ProjectAuthorizationSnapshotSyncRecorder`](../services/board-service/src/main/java/dev/platform/board/infrastructure/snapshot/ProjectAuthorizationSnapshotSyncRecorder.java) | Tạo snapshot job và event Membership/cleanup; caller phải nối mutation trong cùng transaction. |
| Task | [`MemberAssignmentCleanupListener`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupListener.java), [`MemberAssignmentCleanupRabbitConfiguration`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupRabbitConfiguration.java), [`MemberAssignmentCleanupRecorder`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupRecorder.java) và [`MemberAssignmentCleanupWorker`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupWorker.java) | Consumer có durable queue/DLQ, contract validation, Inbox transaction, AUTO ACK và shared retry factory; mặc định tắt bởi readiness flags. Unit test kiểm tra topology/config, nhưng broker retry/DLQ và ACK redelivery chưa được xác minh trong thay đổi này. |
| Notification | [`TaskEventListener`](../services/notification-service/src/main/java/dev/platform/notification/infrastructure/event/TaskEventListener.java), [`TaskNotificationEventMapper`](../services/notification-service/src/main/java/dev/platform/notification/infrastructure/event/TaskNotificationEventMapper.java) và [`NotificationRabbitConfiguration`](../services/notification-service/src/main/java/dev/platform/notification/infrastructure/event/NotificationRabbitConfiguration.java) | Hai Notification intent dùng shared retry factory; Notification sở hữu durable queue/binding/DLQ. Poison contract bị reject không retry. Publish trực tiếp qua broker local đã được kiểm thử trước đây; Task Outbox Relay và ACK-loss vẫn cần nghiệm thu. |

### Khi thêm luồng event mới

1. Chốt service sở hữu nghiệp vụ, consumer, event contract và version; sửa [`EventTypes`](event-contracts/src/main/java/dev/platform/contracts/EventTypes.java), payload và tài liệu contract khi cần.
2. Ở producer, ghi thay đổi nghiệp vụ và `outbox.append(envelope)` trong **cùng transaction**. Không gửi RabbitMQ trực tiếp thay cho Outbox nếu yêu cầu bảo đảm nguyên tử.
3. Cấu hình và kiểm tra publisher confirm/return, exchange, queue và binding; giữ nguyên event đã lưu khi retry publish.
4. Viết listener ở consumer, giải mã đúng contract và gọi `recordIfNew` cùng transaction với thay đổi nghiệp vụ hoặc job bền vững. Xử lý duplicate đồng thời, retry, thứ tự event và ACK sau commit theo chính sách được duyệt.
5. Kiểm tra toàn bộ luồng, gồm rollback, publish/ACK mất, message trùng và consumer lỗi; cập nhật tài liệu service và hợp đồng liên quan. Class, queue, listener hoặc API chưa triển khai phải được ghi rõ là mục tiêu.

Redis snapshot là phần riêng do service sở hữu, không do shared thực hiện; ví dụ task-service đọc qua [`RedisAuthorizationSnapshotAdapter`](../services/task-service/src/main/java/dev/platform/task/infrastructure/adapter/RedisAuthorizationSnapshotAdapter.java).

## `event-contracts`

`EventEnvelope<T>` dùng các trường `eventId`, `eventType`, `schemaVersion`, `aggregateType`, `aggregateId`, `aggregateVersion`, `occurredAt`, `payload`. Event ID là định danh retry/dedup; aggregate version thuộc từng aggregate. Tên wire và payload tối thiểu ở [event decisions](../docs/project/architecture-decisions.md) là hợp đồng thiết kế hiện tại; các record trong module này mới là skeleton contract, chưa chứng minh serialization, publish, consume hoặc schema evolution đã tích hợp end-to-end.

Mẫu JSON theo wire names hiện tại của `MembershipRemoved`:

```json
{
  "eventId": "11111111-1111-4111-8111-111111111111",
  "eventType": "project.membership.removed",
  "schemaVersion": 1,
  "aggregateType": "PROJECT_MEMBERSHIP",
  "aggregateId": "42:550e8400-e29b-41d4-a716-446655440002",
  "aggregateVersion": 7,
  "occurredAt": "2026-11-07T10:15:30Z",
  "payload": {
    "projectId": 42,
    "userId": "550e8400-e29b-41d4-a716-446655440002",
    "revokedMembershipVersion": 2,
    "membershipVersion": 3
  }
}
```

Khi Outbox retry, phải publish lại payload đã lưu với cùng `eventId`, `aggregateVersion` và dữ liệu nghiệp vụ; không tạo event mới cho lần gửi lại. Consumer dedupe theo `(consumerName, eventId)`. `aggregateVersion` hỗ trợ nhận biết phiên bản/gap theo ngữ nghĩa từng event, không thay thế Inbox. Ví dụ trên chỉ minh họa serialization contract; listener, queue, ACK và xử lý nghiệp vụ vẫn do từng service nối riêng.

Tên wire hiện tại là chữ thường, phân tách bằng dấu chấm. Module chỉ định nghĩa kiểu dữ liệu; nó không publish, validate schema, version migration hoặc đảm bảo consumer xử lý.

| Wire event | Payload fields hiện khai báo |
|---|---|
| `user.status.changed` | `userId`, `status`, `authVersion` |
| `user.system-roles.changed` | `userId`, `systemRoles`, `authVersion` |
| `project.membership.changed` | `projectId`, `userId`, `status`, `role`, `membershipVersion` |
| `project.membership.removed` | `projectId`, `userId`, `revokedMembershipVersion`, `membershipVersion` |
| `member.assignments.cleanup-requested` | `operationId`, `projectId`, `userId`, `boardIds`, `membershipVersion` |
| `member.assignments.cleaned` | `operationId`, `projectId`, `userId`, `membershipVersion` |
| `card.assigned` | `projectId`, `boardId`, `cardId`, `recipientUserId` |
| `comment.mentioned` | `projectId`, `boardId`, `cardId`, `commentId`, `recipientUserIds` |

Các field trong bảng là source shape đang khai báo và cần được giữ khớp với tên wire/payload tối thiểu trong architecture decisions. Việc có record/class không có nghĩa event đã được publish/consume end-to-end; cần xác minh riêng từng producer/consumer, Inbox transaction và ACK. Task có producer Outbox cho hai Notification intent `card.assigned` và `comment.mentioned`; Notification đã có consumer source, unit test và kiểm thử publish trực tiếp qua broker local, còn Task Outbox Relay/ACK-loss vẫn thuộc task tích hợp.

Với `member.assignments.cleanup-requested`, board-service phải commit việc gỡ Membership, Outbox và snapshot-sync job trước. Quyền chỉ bị thu hồi khi snapshot mới được apply vào Redis; nếu còn pending, API trả `202` cùng operation handle. Event này chỉ khởi chạy unassign nền, không phải tín hiệu thu hồi quyền. Cleanup mang `membershipVersion` và chỉ được bỏ assignment đúng generation đã revoke.

Trong runtime hiện tại, khi thu hồi, Board tăng `membershipVersion` và gửi riêng `revokedMembershipVersion` của phiên cũ; cleanup dùng phiên cũ, còn snapshot REMOVED mang version mới. `authorizationVersion` tăng mỗi khi quyền đổi, là Membership envelope `aggregateVersion`, đồng thời được dùng cho Redis CAS và unique key của snapshot-sync job. Retry snapshot ACTIVE cũ không thể ghi đè snapshot mới hơn. Nếu Redis đã có version mới hơn trước khi job cũ chạy, job đó được đánh dấu `SUPERSEDED`, không được hiểu là snapshot cũ đã apply.

Task lưu `membershipVersion` trên từng assignment và durable fence theo `(projectId,userId,membershipVersion)`; việc ghi assignment phải được tuần tự hóa với fence. Durable cleanup job cũng lưu `sourceAggregateVersion` từ request envelope, và completion giữ nguyên version nguồn này. Do đó cleanup cũ chỉ xóa đúng generation cũ, không xóa assignment sau khi User tham gia lại.

## `messaging-support`

### Outbox

1. Producer gọi `OutboxEventWriter.append()` bên trong transaction đang tồn tại (`Propagation.MANDATORY`). Record `outbox_events` được lưu cùng transaction nghiệp vụ.
2. `OutboxRelay` chạy theo lịch, lấy tối đa 50 bản ghi `PENDING` đến hạn, gửi message persistent tới exchange `dev-platform.domain-events`, routing key là `eventType`.
3. Relay chờ Publisher Confirm và kiểm tra Publisher Return. Confirm thành công, không bị return thì đánh dấu `SENT`; timeout/nack/unroutable thì giữ `PENDING`, tăng attempt và lên lịch retry.

Hiện retry delay mặc định 5 giây, confirm timeout 5 giây, poll delay 1 giây. Outbox retry không có giới hạn attempts hoặc DLQ; đây là retry phía producer, tách biệt với chính sách retry của consumer bên dưới. Retention/cleanup policy và metrics/alerting cũng chưa có.

Kỳ 1 dự kiến chỉ chạy một instance mỗi service; relay hiện chưa claim event cho nhiều instance. Shared consumer factory cấu hình AUTO ACK, concurrency tối đa 1 trong mỗi instance, retry tối đa 3 lần cách nhau cố định 5 giây rồi reject không requeue. `AmqpRejectAndDontRequeueException` dành cho poison message và bỏ qua retry. Shared khai báo DLX `dev-platform.dead-letter`; mỗi consumer phải khai báo durable DLQ và binding của mình. Notification và Task cleanup đã dùng policy chung trong source; Task cleanup còn cần `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY=true` để đăng ký listener/topology. Broker retry/DLX routing chưa được nghiệm thu end-to-end. Giới hạn này không áp dụng cho Outbox Relay.

### Inbox

`InboxEventRecorder.recordIfNew(consumerName, eventId, eventType)` cũng yêu cầu transaction có sẵn. Khóa bảng `inbox_events` là `(consumer_name, event_id)`, nên mỗi consumer có dedup scope riêng. Consumer phải ghi Inbox cùng transaction với side effect/job bền vững rồi mới ACK; helper này không tự cài listener, không ACK RabbitMQ và không đảm bảo business logic idempotent.

Implementation hiện kiểm tra `existsById` rồi mới `save`; unique composite key bảo vệ dữ liệu, nhưng code chưa xử lý riêng race giữa hai lần nhận đồng thời cùng một event. Caller cần xác định cách retry/xử lý unique violation trước khi đưa vào consumer có concurrency.

## Physical table mappings

Các entity shared được map riêng trong DB của từng service. Mapping JPA hiện tại:

### `outbox_events`

| Cột | Kiểu/mapping | Constraint/default |
|---|---|---|
| `event_id` | UUID | PK, not null |
| `event_type` | varchar(120) | not null |
| `aggregate_type` | varchar(80) | not null |
| `aggregate_id` | varchar(80) | not null |
| `aggregate_version` | bigint | not null |
| `payload` | nvarchar(max) | not null; JSON serialized envelope |
| `status` | varchar(20) | not null; `PENDING` hoặc `SENT`; mặc định `PENDING` |
| `attempt_count` | integer | not null; entity khởi tạo 0 |
| `created_at`, `next_attempt_at` | datetimeoffset | not null; `next_attempt_at` khởi tạo bằng `created_at` |
| `sent_at` | datetimeoffset | nullable |
| `last_error` | nvarchar(max) | nullable |

Index `idx_outbox_due(status, next_attempt_at, created_at)`. Không có FK tới aggregate/domain row.

### `inbox_events`

| Cột | Kiểu/mapping | Constraint/default |
|---|---|---|
| `consumer_name` | varchar(120) | một nửa composite PK, not null |
| `event_id` | UUID | một nửa composite PK, not null |
| `event_type` | varchar(120) | not null |
| `received_at` | datetimeoffset | not null |

Không khai báo index phụ; composite primary key bảo vệ cặp consumer/event.

Kỳ 1 chưa dùng migration; local service config dùng Hibernate `ddl-auto=none`, vì vậy không tự tạo bảng. [Script khởi tạo local](../sql/local-sqlserver-bootstrap.sql) tạo bảng Outbox/Inbox rỗng trong từng database service khi cả bốn database còn trống.

### Bật/tắt và trạng thái hiện tại

Cấu hình trong mỗi service mặc định tắt. [`.env.example`](../.env.example) gom các cờ bật bộ tích hợp local đầy đủ, kèm kiểu dữ liệu và ví dụ. Copy lần đầu sang `.env`, điền credential/secret rồi khởi động lại service; không ghi chú thích sau giá trị vì file được đọc theo Java properties.

```yaml
dev-platform:
  messaging:
    enabled: ${MESSAGING_ENABLED:false}
    outbox-relay:
      enabled: ${OUTBOX_RELAY_ENABLED:false}
    consumers:
      enabled: ${MESSAGING_CONSUMERS_ENABLED:false}
```

Relay chỉ được đăng ký khi cả `MESSAGING_ENABLED=true` và `OUTBOX_RELAY_ENABLED=true`. Shared consumer factory và DLX exchange được đăng ký khi `MESSAGING_ENABLED=true` và `MESSAGING_CONSUMERS_ENABLED=true`. Consumer cleanup Task chỉ được đăng ký khi thêm `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY=true`; queue mặc định `task-service.member-assignment-cleanup`, DLQ `task-service.member-assignment-cleanup.dlq`. Notification consumer bind queue `notification-service.task-events` với `card.assigned`/`comment.mentioned`, dùng AUTO ACK, retry tối đa 3 lần cách 5 giây và DLQ `notification-service.task-events.dlq`.

Không bật messaging như thể toàn bộ integration đã hoạt động. Trước khi nghiệm thu end-to-end cần kiểm tra Task Notification Outbox/relay, broker routing, Notification consumer ACK/redelivery/DLQ, producer Outbox Membership, cleanup worker và completion publisher.

## Chạy và dùng local

Các module là library, không có `main()` và không chạy bằng `spring-boot:run`. Từ thư mục gốc repo, build chúng cùng dependency:

```bash
mvn -pl shared/event-contracts,shared/messaging-support -am -DskipTests package
```

Test Inbox với SQL Server là opt-in; mặc định test này bị bỏ qua. Để chạy trên máy có schema local đã khởi tạo, từ root dùng PowerShell:

```powershell
mvn -pl shared/messaging-support -am '-Dshared.sqlserver.tests=true' '-Dtest=InboxSqlServerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Test mặc định kết nối `localhost\PHUC`, database `kanban_user_db`, dùng `DB_USERNAME`/`DB_PASSWORD` trong `.env` ở root. Có thể chỉ định `INBOX_TEST_DB_URL` riêng; chỉ dùng database thử nghiệm đáng tin cậy có bảng `inbox_events`, không trỏ production. Test ghi bản ghi có ID/consumer ngẫu nhiên và dọn đúng các bản ghi đó sau mỗi test; nếu tiến trình bị dừng đột ngột có thể còn dữ liệu test. Không thay đổi schema hoặc chạy script bootstrap. Test này chưa kiểm chứng ACK RabbitMQ hoặc rollback dữ liệu nghiệp vụ của service.

Khởi động broker local (chưa khởi động các service):

```bash
docker compose up -d rabbitmq
```

Để tích hợp vào service: khai báo dependency `messaging-support`, import `MessagingSupportConfiguration`, scan package shared cho JPA entity/repository, cấu hình Rabbit connection, rồi ghi Outbox trong transaction producer. Mỗi service vẫn dùng database riêng của nó. Tham khảo [`architecture decisions`](../docs/project/architecture-decisions.md), [schema chung trong user_db](../services/user-service/docs/database.md#bảng-messaging-dùng-chung) và [ghi chú Account deletion Kỳ 2](../docs/project/account-deletion-skeleton-guide.md).

## Giới hạn contract hiện tại

- At-least-once delivery; Outbox retry có thể publish lặp cùng `eventId` nếu confirm bị mất.
- Consumer cần Inbox theo `(consumerName, eventId)` và xử lý idempotent; không dùng `aggregateVersion` thay dedup.
- Redis Authentication/Authorization Snapshot vẫn thuộc kiến trúc mục tiêu Kỳ 1; schema/key, TTL, consumer cập nhật snapshot và cách xử lý gap/stale event chưa được chốt. `shared/` hiện không triển khai Redis cache.
- Account/Project deletion và Notification cascade không thuộc Kỳ 1. Event contracts Kỳ 1 gồm Membership, assignment cleanup và hai Notification intent của Task; Member assignment cleanup là tác vụ cleanup duy nhất, không có Card state stream hoặc `MOVED` notification event.
- Schema evolution, consumer ordering, xử lý sau DLQ, Outbox Relay/ACK-loss và broker E2E của Task cleanup vẫn cần nghiệm thu. Retry consumer/DLX source policy đã dùng chung; shared không sở hữu queue/DLQ cụ thể của service.
- Các class hiện có là nền dùng chung, không chứng minh cascade Account/Project đã chạy end-to-end.
