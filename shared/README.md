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

Các field trong bảng là source shape đang khai báo và cần được giữ khớp với tên wire/payload tối thiểu trong architecture decisions. Việc có record/class không có nghĩa event đã được publish/consume trong runtime; queue/binding, listener, Inbox transaction và ACK vẫn là phần tích hợp riêng.

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
| `payload` | TEXT | not null; JSON serialized envelope |
| `status` | varchar(20) | not null; `PENDING` hoặc `SENT`; mặc định `PENDING` |
| `attempt_count` | integer | not null; entity khởi tạo 0 |
| `created_at`, `next_attempt_at` | timestamptz | not null; `next_attempt_at` khởi tạo bằng `created_at` |
| `sent_at` | timestamptz | nullable |
| `last_error` | TEXT | nullable |

Index `idx_outbox_due(status, next_attempt_at, created_at)`. Không có FK tới aggregate/domain row.

### `inbox_events`

| Cột | Kiểu/mapping | Constraint/default |
|---|---|---|
| `consumer_name` | varchar(120) | một nửa composite PK, not null |
| `event_id` | UUID | một nửa composite PK, not null |
| `event_type` | varchar(120) | not null |
| `received_at` | timestamptz | not null |

Không khai báo index phụ; composite primary key bảo vệ cặp consumer/event.

Migrations chưa được thêm; local service config dùng Hibernate `ddl-auto=update`. Khi schema cần version/reviewable rollout, chuyển các mapping này thành migration trong từng service, không đặt migration vào `shared` vì mỗi service sở hữu DB riêng.

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

Relay chỉ được đăng ký khi cả `MESSAGING_ENABLED=true` và `OUTBOX_RELAY_ENABLED=true`. `MESSAGING_CONSUMERS_ENABLED` hiện chưa được dùng bởi consumer implementation nào. Code hiện khai báo durable Topic Exchange nhưng chưa có queue/binding hoặc `@RabbitListener`; chỉ bật hai cờ Relay không tạo được luồng event tới consumer, và message không route sẽ được retry.

Không bật messaging như thể cascade đã hoạt động. Trước khi thử end-to-end cần thêm queue/binding, consumer, Inbox + job transaction, ACK policy và completion publisher theo quyết định chung.

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
