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

4. **Task nhận yêu cầu:** [`MemberAssignmentCleanupListener`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupListener.java) bind durable queue theo routing key đã duyệt. Listener giải mã envelope, kiểm tra contract, gọi [`MemberAssignmentCleanupRecorder.requestMemberAssignmentCleanup`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupRecorder.java) rồi manual ACK sau khi recorder transaction commit. Consumer mặc định tắt. Khi xử lý lỗi hiện NACK/requeue vô hạn; retry/backoff/DLQ chưa được chốt.

   ```java
   if (!inbox.recordIfNew(CONSUMER_NAME, event.eventId(), event.eventType())) {
       return;
   }
   // Sau đó ghi fence và cleanup job trong cùng transaction.
   ```

   Inbox phân biệt theo `(consumerName, eventId)`. Cleanup job còn có khóa nghiệp vụ theo Project, User và `membershipVersion`; fence giúp phối hợp cleanup với assignment. Nếu ACK lỗi/mất sau commit, RabbitMQ có thể giao lại; Inbox làm delivery lặp idempotent. Listener hiện chưa có bounded retry hoặc DLQ.

5. **Worker:** [`MemberAssignmentCleanupWorker`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupWorker.java) có code đọc job, xóa assignment theo batch và ghi event `MEMBER_ASSIGNMENTS_CLEANED` qua `OutboxEventWriter`. Worker chỉ được tạo khi bật `dev-platform.assignment-cleanup.enabled`. Việc có worker không chứng minh cleanup hoặc completion đã chạy end-to-end.

### Vai trò của từng service

| Service | Chỗ đọc code | Hiện trạng cần lưu ý |
|---|---|---|
| User | [`AccountAuthorizationSnapshotSyncRecorder`](../services/user-service/src/main/java/dev/platform/user/infrastructure/snapshot/AccountAuthorizationSnapshotSyncRecorder.java) | Có code ghi snapshot job và Outbox khi status/role thay đổi; chưa chứng minh đã nối vào toàn bộ lifecycle Account. |
| Board | [`ProjectAuthorizationSnapshotSyncRecorder`](../services/board-service/src/main/java/dev/platform/board/infrastructure/snapshot/ProjectAuthorizationSnapshotSyncRecorder.java) | Tạo snapshot job và event Membership/cleanup; caller phải nối mutation trong cùng transaction. |
| Task | [`MemberAssignmentCleanupListener`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupListener.java), [`MemberAssignmentCleanupRecorder`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupRecorder.java) và [`MemberAssignmentCleanupWorker`](../services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupWorker.java) | Consumer có durable queue, contract validation, Inbox transaction và manual ACK sau commit; mặc định tắt; broker redelivery, retry/DLQ policy và E2E chưa được xác minh. |
| Notification | [`TaskEventListener`](../services/notification-service/src/main/java/dev/platform/notification/infrastructure/event/TaskEventListener.java) | Có import cấu hình shared; listener nghiệp vụ hiện còn TODO. |

### Khi thêm luồng event mới

1. Chốt service sở hữu nghiệp vụ, consumer, event contract và version; sửa [`EventTypes`](event-contracts/src/main/java/dev/platform/contracts/EventTypes.java), payload và tài liệu contract khi cần.
2. Ở producer, ghi thay đổi nghiệp vụ và `outbox.append(envelope)` trong **cùng transaction**. Không gửi RabbitMQ trực tiếp thay cho Outbox nếu yêu cầu bảo đảm nguyên tử.
3. Cấu hình và kiểm tra publisher confirm/return, exchange, queue và binding; giữ nguyên event đã lưu khi retry publish.
4. Viết listener ở consumer, giải mã đúng contract và gọi `recordIfNew` cùng transaction với thay đổi nghiệp vụ hoặc job bền vững. Xử lý duplicate đồng thời, retry, thứ tự event và ACK sau commit theo chính sách được duyệt.
5. Kiểm tra toàn bộ luồng, gồm rollback, publish/ACK mất, message trùng và consumer lỗi; cập nhật tài liệu service và hợp đồng liên quan. Class, queue, listener hoặc API chưa triển khai phải được ghi rõ là mục tiêu.

Redis snapshot là phần riêng do service sở hữu, không do shared thực hiện; ví dụ task-service đọc qua [`RedisAuthorizationSnapshotAdapter`](../services/task-service/src/main/java/dev/platform/task/infrastructure/adapter/RedisAuthorizationSnapshotAdapter.java).

## `event-contracts`

`EventEnvelope<T>` dùng các trường `eventId`, `eventType`, `schemaVersion`, `aggregateType`, `aggregateId`, `aggregateVersion`, `occurredAt`, `payload`. Event ID là định danh retry/dedup; aggregate version thuộc từng aggregate. Tên wire và payload tối thiểu ở [event decisions](../docs/project/architecture-decisions.md) là hợp đồng thiết kế hiện tại; các record trong module này mới là skeleton contract, chưa chứng minh serialization, publish, consume hoặc schema evolution đã tích hợp end-to-end.

Tên wire hiện tại là chữ thường, phân tách bằng dấu chấm. Module chỉ định nghĩa kiểu dữ liệu; nó không publish, validate schema, version migration hoặc đảm bảo consumer xử lý.

| Wire event | Payload fields hiện khai báo |
|---|---|
| `user.status.changed` | `userId`, `status`, `authVersion` |
| `user.system-roles.changed` | `userId`, `systemRoles`, `authVersion` |
| `project.membership.changed` | `projectId`, `userId`, `status`, `role`, `membershipVersion` |
| `project.membership.removed` | `projectId`, `userId`, `membershipVersion` |
| `member.assignments.cleanup-requested` | `operationId`, `projectId`, `userId`, `boardIds`, `membershipVersion` |
| `member.assignments.cleaned` | `operationId`, `projectId`, `userId`, `membershipVersion` |

Các field trong bảng là source shape đang khai báo và cần được giữ khớp với tên wire/payload tối thiểu trong architecture decisions. Việc có record/class không có nghĩa event đã được publish/consume trong runtime; cần xác minh riêng từng producer/consumer, Inbox transaction và ACK. Consumer cleanup Membership của Task đã có source; các event flow khác vẫn cần cấu hình và nghiệm thu.

Với `member.assignments.cleanup-requested`, board-service phải commit việc gỡ Membership, Outbox và snapshot-sync job trước. Quyền chỉ bị thu hồi khi snapshot mới được apply vào Redis; nếu còn pending, API trả `202` cùng operation handle. Event này chỉ khởi chạy unassign nền, không phải tín hiệu thu hồi quyền. Cleanup mang `membershipVersion` và chỉ được bỏ assignment đúng generation đã revoke.

`membershipVersion` tăng theo từng lần Membership được tạo lại và giữ nguyên khi lần đó bị thu hồi. `authorizationVersion` tăng mỗi khi quyền đổi, là Membership envelope `aggregateVersion`, đồng thời được dùng cho Redis CAS và unique key của snapshot-sync job. Nhờ hai version tách biệt, retry snapshot ACTIVE cũ không thể ghi đè REMOVED cùng generation. Nếu Redis đã có version mới hơn trước khi job cũ chạy, job đó được đánh dấu `SUPERSEDED`, không được hiểu là snapshot cũ đã apply.

Task lưu `membershipVersion` trên từng assignment và durable fence theo `(projectId,userId,membershipVersion)`; việc ghi assignment phải được tuần tự hóa với fence. Durable cleanup job cũng lưu `sourceAggregateVersion` từ request envelope, và completion giữ nguyên version nguồn này. Do đó cleanup cũ chỉ xóa đúng generation cũ, không xóa assignment sau khi User tham gia lại.

## `messaging-support`

### Outbox

1. Producer gọi `OutboxEventWriter.append()` bên trong transaction đang tồn tại (`Propagation.MANDATORY`). Record `outbox_events` được lưu cùng transaction nghiệp vụ.
2. `OutboxRelay` chạy theo lịch, lấy tối đa 50 bản ghi `PENDING` đến hạn, gửi message persistent tới exchange `dev-platform.domain-events`, routing key là `eventType`.
3. Relay chờ Publisher Confirm và kiểm tra Publisher Return. Confirm thành công, không bị return thì đánh dấu `SENT`; timeout/nack/unroutable thì giữ `PENDING`, tăng attempt và lên lịch retry.

Hiện retry delay mặc định 5 giây, confirm timeout 5 giây, poll delay 1 giây. Chưa có giới hạn attempts, DLQ, retention/cleanup policy hoặc metrics/alerting.

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

Cấu hình trong mỗi service mặc định tắt:

```yaml
dev-platform:
  messaging:
    enabled: ${MESSAGING_ENABLED:false}
    outbox-relay:
      enabled: ${OUTBOX_RELAY_ENABLED:false}
    consumers:
      enabled: ${MESSAGING_CONSUMERS_ENABLED:false}
```

Relay chỉ được đăng ký khi cả `MESSAGING_ENABLED=true` và `OUTBOX_RELAY_ENABLED=true`. Consumer cleanup Task chỉ được đăng ký khi cả `MESSAGING_ENABLED=true` và `MESSAGING_CONSUMERS_ENABLED=true`; cấu hình queue mặc định là `task-service.member-assignment-cleanup`. Binding chỉ nhận event `member.assignments.cleanup-requested`; các event Card/Comment của #25 chưa được duyệt hoặc đăng ký. Retry hiện requeue vô hạn, chưa có DLQ/backoff; chốt policy trước production.

Không bật messaging như thể toàn bộ integration đã hoạt động. Trước khi nghiệm thu end-to-end cần kiểm tra producer Outbox Membership, broker routing, consumer ACK/redelivery, cleanup worker và completion publisher cùng retry/DLQ policy.

## Chạy và dùng local

Các module là library, không có `main()` và không chạy bằng `spring-boot:run`. Từ thư mục gốc repo, build chúng cùng dependency:

```bash
mvn -pl shared/event-contracts,shared/messaging-support -am -DskipTests package
```

Khởi động broker local (chưa khởi động các service):

```bash
docker compose up -d rabbitmq
```

Để tích hợp vào service: khai báo dependency `messaging-support`, import `MessagingSupportConfiguration`, scan package shared cho JPA entity/repository, cấu hình Rabbit connection, rồi ghi Outbox trong transaction producer. Mỗi service vẫn dùng database riêng của nó. Tham khảo [`architecture decisions`](../docs/project/architecture-decisions.md), [schema chung trong user_db](../services/user-service/docs/database.md#bảng-messaging-dùng-chung) và [ghi chú Account deletion Kỳ 2](../docs/project/account-deletion-skeleton-guide.md).

## Giới hạn contract hiện tại

- At-least-once delivery; Outbox retry có thể publish lặp cùng `eventId` nếu confirm bị mất.
- Consumer cần Inbox theo `(consumerName, eventId)` và xử lý idempotent; không dùng `aggregateVersion` thay dedup.
- Redis Authentication/Authorization Snapshot vẫn thuộc kiến trúc mục tiêu Kỳ 1; schema/key, TTL, consumer cập nhật snapshot và cách xử lý gap/stale event chưa được chốt. `shared/` hiện không triển khai Redis cache.
- Account/Project deletion và Notification cascade không thuộc Kỳ 1. Chỉ Member assignment cleanup là tác vụ cleanup được giữ trong event-contracts Kỳ 1.
- Schema evolution, retry/DLQ, queue names/bindings và consumer ordering chưa được chốt đầy đủ.
- Các class hiện có là nền dùng chung, không chứng minh cascade Account/Project đã chạy end-to-end.
