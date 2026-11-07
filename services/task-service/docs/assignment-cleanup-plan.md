# Issue #22 Implementation Plan

**Goal:** Hoàn thiện assignment theo Project và membership generation, đồng thời bảo đảm cleanup batch rollback trước khi ghi retry.

**Architecture:** `AssignCardService` dùng application port để khóa và đọc Card theo Project, sau đó thay đổi assignment qua adapter hiện tại. Worker chạy batch trong transaction riêng; lỗi được xử lý sau rollback bằng một transaction mới. Consumer bind event cleanup được duyệt và dùng AUTO ACK sau commit; broker E2E cùng retry/DLQ policy còn mở.

**Tech Stack:** Java 21, Spring Boot, JPA, SQL Server, Redis snapshot, Outbox/Inbox hiện có.

**Spec:** `services/task-service/docs/assignment-cleanup-design.md`.

## Global Constraints

- Chỉ sửa trong `services/task-service`; không thêm event chưa duyệt, internal REST không bảo mật hoặc mock dữ liệu bên ngoài phạm vi đã đồng ý.
- Account/Project identity hiện phụ thuộc Gateway và Redis; snapshot lỗi phải fail closed.
- Không xóa Comment hoặc assignment của membership generation mới; không dùng aggregateVersion thay Inbox.

## Review Focus

- Card sai Project trả 404 và không ghi assignment.
- Unassign, assign lặp và rejoin cùng user với generation khác không ghi sai version.
- Concurrent assignment/move không ghi đè position cũ; lock order không tạo vòng chờ với cleanup.
- Lỗi sau xóa batch hoặc Outbox append rollback toàn bộ trước khi lịch retry được lưu.
- Cleanup không được hoàn tất khi thiếu fence, kể cả danh sách Board rỗng.

## Task 1: Assignment theo Project

**Files:** `application/port/out/SaveCardPort.java`, `infrastructure/adapter/SaveCardAdapter.java`, `application/service/AssignCardService.java`, `application/service/UnavailableAssignCardService.java`, tests trong `src/test`.

**Interfaces:** Thêm `Optional<CardAggregate> findForUpdate(Long projectId, Long cardId)` vào SaveCardPort; implement bằng `lockByIdAndProjectId`, map assignment hiện tại. `AssignCardService` implement `AssignCardPort.assign(AssignCardCommand)` trong transaction, 404 nếu không thấy Card, gọi `CardAggregate.assign(...)` và `save` khi thay đổi.

- [x] Thêm kiểm tra unit cho assign, unassign, idempotency, rejoin và Card ngoài Project; kiểm tra SQL Server cho membership generation và fence. Snapshot Redis không được xác minh E2E trong task-service test.
- [x] Implement port, adapter và service; chỉ bật `AssignCardService` khi `TASK_TRUSTED_GATEWAY_IDENTITY_ENABLED=true`. Mặc định PATCH assignee và POST create có `assigneeId` bị từ chối trước authorization guard; fallback trả 503. Lấy lock Project/User trước khi khóa Card để tránh ghi đè position cũ.
- [ ] Chưa kiểm chứng đồng thời assignment/move hoặc khả năng phát hiện snapshot ACTIVE cũ nhưng hợp lệ về cấu trúc. Chỉ bật cờ sau khi Gateway xác thực principal, ghi đè `X-User-Id` và chặn truy cập trực tiếp tới task-service; kiểm thử tích hợp thuộc task integration.

## Task 2: Cleanup rollback và retry

**Files:** `assignmentcleanup/MemberAssignmentCleanupWorker.java`, `assignmentcleanup/persistence/repository/MemberAssignmentCleanupJobJpaRepository.java`, tests trong `src/test`.

**Interfaces:** `processNextBatch()` chạy `TransactionTemplate` cho batch; ghi nhớ jobId sau khi khóa job. Nếu batch thất bại, rollback trước; transaction mới khóa lại job theo id và chỉ `scheduleRetry` nếu job vẫn PENDING. `process` báo lỗi ra ngoài transaction thay vì bắt rồi commit. Fence được kiểm tra trước cả trường hợp Board rỗng.

- [x] Thêm unit cho thiếu fence và thứ tự rollback/retry; thêm SQL Server integration chứng minh Outbox lỗi sau delete rollback assignment, giữ Comment và lưu retry ở transaction sau. SQL Server test generation kiểm tra query cleanup giới hạn đúng version.
- [x] Implement transaction boundary và locking; giữ Outbox completion cùng transaction batch thành công.
- [x] Nối durable queue/routing key của event cleanup đã có trong catalog; listener dùng AUTO ACK sau khi recorder transaction commit và Inbox dedupe khi redelivery.
- [x] Consumer chỉ đăng ký khi `MESSAGING_ENABLED`, `MESSAGING_CONSUMERS_ENABLED` và `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY` đều bật; cờ cuối mặc định false.
- [x] Listener phân loại JSON/contract poison bằng `AmqpRejectAndDontRequeueException`; lỗi recorder được chuyển lên container retry policy; thêm unit coverage cho ACK sau handler, poison, handler failure và message hợp lệ sau poison.
- [ ] Chưa kiểm chứng broker redelivery/ACK, bounded retry/backoff, DLQ routing, thứ tự poison/message kế tiếp hoặc nhiều worker cạnh tranh. Giữ consumer tắt cho tới khi integration cung cấp policy Rabbit chung và xác minh broker behavior.

## Verification evidence

- Command recorded before adding the Rabbit listener: `mvn -pl services/task-service -am '-Dtask.sqlserver.integration=true' test`
- Result on 2026-11-06 before the listener change: `BUILD SUCCESS`; task-service: 18 tests, 0 failures, 0 errors, 0 skipped. SQL Server 16.0 database `kanban_task_db` was reached by opt-in integration tests. This result predates the listener and does not verify its runtime behavior.
- Compile after adding the listener: `mvn -pl services/task-service -am -DskipTests compile` — `BUILD SUCCESS`, 63 task-service source files compiled. This is compile evidence only, not broker/ACK or test evidence.
- `MemberAssignmentCleanupSqlServerTest.outboxFailureRollsBackSqlDeleteBeforePersistingRetry` uses SQL Server repositories and transaction manager. It injects an Outbox failure after the assignment delete, then reads persisted state: assignment remains, Comment remains, and job is `PENDING` with retry attempt 1 and the failure message.
- Remaining evidence: rerun tests after the listener change; broker ACK/redelivery, Redis/Gateway identity and latest-snapshot behavior, board-service cleanup delivery, and concurrent multi-worker execution.
