# Kỳ 1 Architecture Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Đồng bộ tài liệu và skeleton Kỳ 1 theo thiết kế đã duyệt: Project-owned membership, Invitation chờ Accept, Redis authorization snapshot, assignment cleanup theo generation, SystemRoles và Notification thuộc Account; loại bỏ code Account/Project cascade khỏi Kỳ 1.

**Architecture:** Giữ bốn service hiện có và database riêng từng service; Project/Membership tiếp tục thuộc board-service cùng Board/Column. User-service sở hữu Account/SystemRoles; Task-service lưu Card/Comment cùng TaskAssignees và fence trong task_db; notification-service sở hữu inbox theo recipient Account và context Project/Board tùy chọn. Gateway xác thực/định tuyến, source service quản lý snapshot Redis, Task đọc snapshot; Outbox/Inbox dùng chung vẫn được giữ. Các tích hợp chưa triển khai được ghi rõ là TODO, không mô tả như runtime.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL, Maven, RabbitMQ Outbox/Inbox hiện có; Redis là hợp đồng/snapshot integration theo thiết kế Kỳ 1.

**Spec:** [Kỳ 1 Membership, Authorization Snapshot và Notification](../specs/2026-10-29-k1-membership-snapshot-notification-alignment-design.md)

## Global Constraints

- Giữ nguyên mọi thay đổi đã có trong working tree; sửa theo từng file, không restore hàng loạt.
- Không triển khai Account deletion/cascade trong Kỳ 1. Không xóa shared Outbox/Inbox hoặc Member assignment cleanup.
- Không thêm luồng mời email ngoài hệ thống, không cấp quyền Project cho System Admin, không bootstrap Admin đầu tiên khi chính sách chưa chốt.
- Không tạo FK giữa các database/service. `project_id`, `board_id`, `user_id` dùng làm scalar reference ở nơi service không sở hữu.
- Assignment vẫn là một assignee trong API Kỳ 1; `TaskAssignees` chỉ là persistence target có `membership_version`.
- API Card mục tiêu mang `projectId` trong route. `cards.project_id` được kiểm tra trên truy vấn Card hiện hữu. Xác minh Board/Column cùng Project vẫn là integration TODO vì internal REST đang hoãn; không lấy path do client gửi làm bằng chứng quan hệ Board–Project.
- Snapshot revocation có hiệu lực khi Redis apply; `202 + operationId/statusUrl` biểu diễn trạng thái đồng bộ đang chờ. Assignment unassign chạy nền độc lập, retry được và không trì hoãn trạng thái revoke `COMPLETED`.
- `membershipVersion` nhận diện generation assignment; `authorizationVersion` tăng với mọi thay đổi quyền của `(projectId,userId)`, là Membership `aggregateVersion`, Redis CAS version và snapshot-job dedupe key. Cleanup completion giữ source `aggregateVersion` riêng.
- Không thêm hoặc chạy test theo yêu cầu workflow hiện tại; xác nhận compile bằng Maven với tests bị skip sau khi sửa Java.

## Review Focus

- Không còn endpoint, status `DELETING`, event, job hay hướng dẫn cascade được mô tả/đưa vào runtime Kỳ 1.
- Mọi tài liệu phân biệt nguồn chuẩn (DB), Redis snapshot, RabbitMQ event, Outbox retry và Inbox dedupe.
- `membershipVersion` được giữ nguyên qua Request event → durable job → assignment/fence query → completion event; cleanup chỉ xóa đúng Project/User/version.
- Membership ACTIVE/REMOVED/role changes dùng `authorizationVersion` đơn điệu để snapshot CAS/job uniqueness; membership removal không được tái dùng cùng version cho hai snapshot khác trạng thái.
- Redis CAS phân biệt applied/already current/superseded; job `SUPERSEDED` không được dùng làm bằng chứng Membership revoke đã có hiệu lực.
- Inbox, fence và job được ghi trước ACK theo cùng transaction; assignment write được serialize với fence.
- `projectId` trong Card route, Card persistence, API contract, service docs và cursor/cleanup scope không mâu thuẫn.
- Task đọc snapshot từ Redis; `membershipVersion` lấy từ snapshot có thẩm quyền, không nhận từ client.
- Notification thuộc Account nhận; Project/Board/Card chỉ là context; không cascade xóa theo Project trong Kỳ 1.
- Các tài liệu cũ được đánh dấu phạm vi lịch sử/Kỳ 2 thay vì bị xóa nếu còn giá trị tham khảo.
- Sau từng lượt sửa, rà file đầy đủ, cross-reference, linter/hygiene và compile theo giới hạn trên.

---

## Task 1: Cập nhật hợp đồng kiến trúc và API chuẩn

**Files:**
- Modify: `docs/project/architecture-decisions.md`
- Modify: `docs/api/kanban-api.md`
- Modify: `docs/project/06-integration.md`
- Modify: `README.md`
- Modify: `docs/README.md`
- Modify: `gateway/src/main/resources/application.yml`
- Modify: `docs/superpowers/specs/2026-10-29-k1-membership-snapshot-notification-alignment-design.md`

1. Ghi Account deletion/cascade là Kỳ 2, gồm soft delete và chuyển Owner cho Member đủ điều kiện tham gia sớm nhất; chặn xóa nếu không có người kế nhiệm. Loại cascade/hard delete khỏi Kỳ 1.
2. Ghi rõ SystemRoles/UserSystemRoles thuộc user-service; System Admin chỉ quản trị Account. Bootstrap role đầu tiên và role chồng lấp vẫn là điểm mở.
3. Đồng bộ Membership removal: source DB + Outbox + snapshot-sync job commit; Redis apply là mốc hiệu lực/revoke completion; API pending trả `202`, `operationId`, `statusUrl`; assignment cleanup là job độc lập, không trì hoãn completion.
4. Đồng bộ Invitation endpoints dưới `/api/v1/projects`, gồm inbox của người dùng; Invitation `PENDING` không tạo Membership, Accept mới tạo Membership.
5. Xóa các route Account deletion khỏi contract Kỳ 1 và chuyển nội dung còn giữ sang ghi chú Kỳ 2.
6. Cập nhật Card routes theo Project scope:
   - `GET/POST /api/v1/projects/{projectId}/boards/{boardId}/cards`
   - `GET /api/v1/projects/{projectId}/cards/{cardId}`
   - `PATCH /api/v1/projects/{projectId}/cards/{cardId}/move`
   - `PATCH /api/v1/projects/{projectId}/cards/{cardId}/assignee`
   - `GET/POST /api/v1/projects/{projectId}/cards/{cardId}/comments`
7. Thay route Card Board-first trong Gateway bằng Task-specific `/api/v1/projects/*/boards/*/cards/**` và `/api/v1/projects/*/cards/**`; đặt chúng trước board-service và đổi predicate board-service thành `/api/v1/projects/**,/api/v1/boards/**` để path Project-scoped không bị route tổng quát bắt trước.
8. Ghi `aggregateType` trong envelope chuẩn và phân biệt `eventId`, `aggregateVersion`, `operationId`; chốt payload/version tối thiểu cho `USER_STATUS_CHANGED`, `USER_SYSTEM_ROLES_CHANGED`, `MEMBERSHIP_CHANGED`, `MEMBERSHIP_REMOVED` và assignment cleanup request.
9. Gỡ các mục đã chốt khỏi “cần bàn tiếp”; giữ các quyết định chưa được đưa ra như bootstrap Admin, expiry Invitation, migration tool và Board/Column scope validation.

**Expected:** Hợp đồng trung tâm nhất quán và nêu rõ thiết kế mục tiêu khác runtime scaffold hiện tại ở đâu.

## Task 2: Đồng bộ tài liệu service, Gateway, Shared và tài liệu lịch sử

**Files:**
- Modify: `docs/project/01-user-service.md`
- Modify: `docs/project/02-board-service.md`
- Modify: `docs/project/03-task-service.md`
- Modify: `docs/project/04-notification-service.md`
- Modify: `docs/project/05-gateway.md`
- Modify: `services/user-service/docs/api.md`
- Modify: `services/user-service/docs/database.md`
- Modify: `services/board-service/docs/api.md`
- Modify: `services/board-service/docs/database.md`
- Modify: `services/task-service/docs/api.md`
- Modify: `services/task-service/docs/database.md`
- Modify: `services/notification-service/docs/api.md`
- Modify: `services/notification-service/docs/database.md`
- Modify: `gateway/README.md`
- Modify: `gateway/docs/routing.md`
- Modify: `shared/README.md`
- Modify: `docs/project/account-deletion-skeleton-guide.md`
- Modify: `docs/superpowers/plans/2026-10-28-account-deletion-cascade-skeleton.md`
- Modify: `docs/superpowers/plans/2026-10-28-common-docs-alignment.md`
- Modify: `docs/superpowers/plans/2026-10-28-service-docs-alignment.md`
- Modify: `docs/superpowers/plans/2026-10-28-service-internal-docs-completion.md`
- Modify: `docs/superpowers/plans/2026-10-21-kanban-web-demo.md`
- Modify: `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md`
- Modify: `web-demo/README.md`
- Modify: `web-demo/docs/superpowers/plans/2026-10-21-project-board-navigation.md`
- Modify: `web-demo/docs/superpowers/specs/2026-10-21-project-board-navigation-design.md`

1. Update service responsibility, DB owner, target schema, routes, event ownership, Redis snapshot read/write ownership and compile/runtime TODO per service.
2. In task-service docs, distinguish active `TaskAssignees`/membership cleanup target from removed Project cascade; explain `project_id`, generation, fence, durable job, and Redis Project snapshot.
3. In user-service docs, replace `DELETING`/Account operation schema with SystemRoles target and K2-only Account deletion note.
4. In notification-service docs, add nullable `project_id` context while retaining recipient `user_id`; remove Project-deletion job as K1 runtime.
5. In Shared docs, explain module purpose/integration order/DB-local mappings; keep generic Outbox/Inbox; list only K1 membership cleanup event among cleanup operations and include `membershipVersion`.
6. Convert `account-deletion-skeleton-guide.md` from K1 build instructions to a K2 reference, and label old plans as historical/superseded without deleting them.
7. Preserve demo route behavior if it remains a mock API; add an explicit note where its Board-first routes differ from the Project-scoped backend contract. Update links and status claims so docs do not imply the demo is backend runtime.
8. Search all Markdown files for stale Account deletion, Project cascade, old invitation inbox route, Card route, `assignee_id`, missing `aggregateType`, and old shared links; update every active-current-state claim.

**Expected:** Người đọc từng service biết DB nào sở hữu dữ liệu, Shared cung cấp gì, integration nào hoạt động, integration nào còn TODO và tài liệu nào chỉ là lịch sử/Kỳ 2.

## Task 3: Gỡ scaffold Account/Project cascade khỏi runtime Kỳ 1

**Files:**
- Delete: `services/user-service/src/main/java/dev/platform/user/application/service/AccountDeletionApplicationService.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/application/service/AccountDeletionCompletionHandler.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/application/command/RequestAccountDeletionCommand.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/application/deletion/AccountDeletionOperationStatus.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/application/deletion/AccountDeletionOperationView.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/application/port/in/GetAccountDeletionOperationPort.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/application/port/in/RequestAccountDeletionPort.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/application/port/out/AccountDeletionStorePort.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/infrastructure/adapter/AccountDeletionPersistenceAdapter.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/entity/AccountDeletionOperationEntity.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/entity/DeletedAccountTombstoneEntity.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/repository/AccountDeletionOperationJpaRepository.java`
- Delete: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/repository/DeletedAccountTombstoneJpaRepository.java`
- Modify: `services/user-service/src/main/java/dev/platform/user/domain/AccountStatus.java`
- Modify: `services/user-service/src/main/java/dev/platform/user/domain/aggregate/UserAggregate.java`
- Modify: `services/user-service/src/main/resources/application.yml`
- Delete: `services/board-service/src/main/java/dev/platform/board/deletion/ProjectDeletionDataPort.java`
- Delete: `services/board-service/src/main/java/dev/platform/board/deletion/ProjectDeletionTarget.java`
- Delete: `services/board-service/src/main/java/dev/platform/board/deletion/ProjectDeletionStatus.java`
- Delete: `services/board-service/src/main/java/dev/platform/board/deletion/persistence/entity/ProjectDeletionOperationEntity.java`
- Delete: `services/board-service/src/main/java/dev/platform/board/deletion/persistence/repository/ProjectDeletionOperationJpaRepository.java`
- Move/refactor: `services/board-service/src/main/java/dev/platform/board/deletion/ProjectDeletionCoordinator.java` into `services/board-service/src/main/java/dev/platform/board/membership/assignmentcleanup/MemberAssignmentCleanupCompletionHandler.java`, retaining `onMemberAssignmentsCleaned(EventEnvelope<MemberAssignmentsCleaned>)` only
- Modify: `services/task-service/src/main/java/dev/platform/task/deletion/TaskDeletionHandler.java` to remove only `requestProjectCascade` and preserve `requestMemberAssignmentCleanup` for the cleanup-package move in Task 4
- Modify: `services/task-service/src/main/java/dev/platform/task/deletion/persistence/entity/TaskDeletionJobEntity.java` to remove cascade-only columns and preserve assignment cleanup fields for the persistence rename in Task 4
- Delete: `services/task-service/src/main/java/dev/platform/task/deletion/TaskDeletionJobType.java`, `TaskWriteGuard.java`, `persistence/entity/BoardWriteLockEntity.java`, and `persistence/repository/BoardWriteLockJpaRepository.java`
- Delete: `services/notification-service/src/main/java/dev/platform/notification/deletion/NotificationDeletionHandler.java`, `NotificationDeletionStatus.java`, `persistence/entity/NotificationDeletionJobEntity.java`, and `persistence/repository/NotificationDeletionJobJpaRepository.java`
- Delete: deletion-only DTOs under `shared/event-contracts/src/main/java/dev/platform/contracts/event/`: `AccountDeletionRequested`, `AccountProjectCleanupCompleted`, `ProjectDeletionRequested`, `ProjectTaskDataDeleted`, `ProjectNotificationsDeleted`, `ProjectCleanupCompleted`
- Modify: `shared/event-contracts/src/main/java/dev/platform/contracts/EventTypes.java`
- Preserve: `shared/messaging-support/**`, `EventEnvelope`, `USER_STATUS_CHANGED`, `USER_SYSTEM_ROLES_CHANGED`, `MEMBERSHIP_CHANGED`, `MEMBERSHIP_REMOVED`, `MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED`, `MEMBER_ASSIGNMENTS_CLEANED` and their payload contracts

1. Remove Account `DELETING` transition and `ACCOUNT_DELETION_ADMIN_API_ENABLED` configuration. Keep other Account behavior/status unchanged.
2. Remove Board Project cascade data port, Project deletion operation persistence and cascade callbacks; preserve the `MemberAssignmentsCleaned` completion boundary by moving it to a membership-cleanup package.
3. Remove Task cascade listener boundary, cascade job branch/checkpoints and Board write locks. Keep and accurately rename the independent Member assignment cleanup job/handler; retain retry status/error/checkpoint fields required for that job.
4. Remove Notification Project-deletion handler/job.
5. Remove deletion-only event classes/constants, but retain Account status/SystemRoles and Membership change/removal event contracts, Member assignment cleanup event types and shared messaging support.
6. Search Java imports and configuration references before deleting; compile after the group to catch broken wiring.

**Expected:** Kỳ 1 no longer exposes or scaffolds Account/Project deletion, while Outbox/Inbox and membership assignment cleanup remain available.

## Task 4: Align user, task and notification persistence contracts

**Files:**
- Add: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/entity/SystemRoleEntity.java`
- Add: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/entity/UserSystemRoleEntity.java` and `UserSystemRoleId.java`
- Add: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/repository/SystemRoleJpaRepository.java` and `UserSystemRoleJpaRepository.java`
- Add: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/entity/AccountAuthorizationSnapshotSyncJobEntity.java`
- Add: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/repository/AccountAuthorizationSnapshotSyncJobJpaRepository.java`
- Add: `services/user-service/src/main/java/dev/platform/user/infrastructure/snapshot/AccountAuthorizationSnapshotSyncRecorder.java`, `RedisAccountAuthorizationSnapshotStore.java`, and `AccountAuthorizationSnapshotSyncWorker.java`
- Modify: `services/user-service/src/main/java/dev/platform/user/domain/aggregate/UserAggregate.java` and `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/entity/UserEntity.java` to persist `auth_version`
- Add: `services/board-service/src/main/java/dev/platform/board/infrastructure/persistence/entity/ProjectMembershipVersionEntity.java` and `ProjectMembershipVersionId.java`
- Add: `services/board-service/src/main/java/dev/platform/board/infrastructure/persistence/entity/ProjectAuthorizationSnapshotSyncJobEntity.java`
- Add: `services/board-service/src/main/java/dev/platform/board/infrastructure/persistence/repository/ProjectAuthorizationSnapshotSyncJobJpaRepository.java`
- Add: `services/board-service/src/main/java/dev/platform/board/infrastructure/snapshot/ProjectAuthorizationSnapshotSyncRecorder.java`, `RedisProjectAuthorizationSnapshotStore.java`, and `ProjectAuthorizationSnapshotSyncWorker.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/api/CardController.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/api/dto/CreateCardRequest.java`, `AssignCardRequest.java`, and `CardResponse.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/application/command/CreateCardCommand.java`, `AssignCardCommand.java`, and `MoveCardCommand.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/application/port/in/CreateCardPort.java`, `AssignCardPort.java`, `MoveCardPort.java`, and `GetCardsPort.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/application/port/out/SaveCardPort.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/domain/aggregate/CardAggregate.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/infrastructure/adapter/SaveCardAdapter.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/infrastructure/persistence/repository/CardJpaRepository.java`
- Add: `services/task-service/src/main/java/dev/platform/task/infrastructure/persistence/repository/TaskAssigneeJpaRepository.java`
- Modify: `services/task-service/src/main/java/dev/platform/task/infrastructure/persistence/entity/CardEntity.java`
- Add: `services/task-service/src/main/java/dev/platform/task/infrastructure/persistence/entity/TaskAssigneeEntity.java`
- Add: `services/task-service/src/main/java/dev/platform/task/assignmentcleanup/persistence/entity/MembershipAssignmentFenceEntity.java` and `services/task-service/src/main/java/dev/platform/task/assignmentcleanup/persistence/repository/MembershipAssignmentFenceJpaRepository.java`
- Add: `services/task-service/src/main/java/dev/platform/task/application/port/out/AuthorizationSnapshotReader.java` and `services/task-service/src/main/java/dev/platform/task/infrastructure/adapter/RedisAuthorizationSnapshotAdapter.java`
- Add: `services/task-service/src/main/java/dev/platform/task/application/service/ProjectAuthorizationGuard.java`
- Add: `services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupRecorder.java` and `MemberAssignmentCleanupWorker.java`; Rabbit listener and ACK wiring remain TODO
- Rename: `services/task-service/src/main/java/dev/platform/task/deletion/TaskDeletionHandler.java` to `services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupHandler.java`
- Rename/Modify: `TaskDeletionJobEntity` to `services/task-service/src/main/java/dev/platform/task/assignmentcleanup/persistence/entity/MemberAssignmentCleanupJobEntity.java`
- Rename: `TaskDeletionJobJpaRepository` to `services/task-service/src/main/java/dev/platform/task/assignmentcleanup/persistence/repository/MemberAssignmentCleanupJobJpaRepository.java`
- Rename: `TaskDeletionStatus` to `services/task-service/src/main/java/dev/platform/task/assignmentcleanup/MemberAssignmentCleanupStatus.java`
- Rename the job table to `member_assignment_cleanup_jobs`; remove cascade-only `project_deletion_id`, comment checkpoint and cascade discriminator fields
- Delete: `services/task-service/src/main/java/dev/platform/task/deletion/TaskDeletionJobType.java` after removing its sole `MEMBER_ASSIGNMENT_CLEANUP` discriminator
- Modify: `shared/event-contracts/src/main/java/dev/platform/contracts/EventTypes.java`
- Add: `shared/event-contracts/src/main/java/dev/platform/contracts/event/UserStatusChanged.java`
- Add: `shared/event-contracts/src/main/java/dev/platform/contracts/event/UserSystemRolesChanged.java`
- Add: `shared/event-contracts/src/main/java/dev/platform/contracts/event/MembershipChanged.java`
- Add: `shared/event-contracts/src/main/java/dev/platform/contracts/event/MembershipRemoved.java`
- Modify: `shared/event-contracts/src/main/java/dev/platform/contracts/event/MemberAssignmentsCleanupRequested.java`
- Modify: `shared/event-contracts/src/main/java/dev/platform/contracts/event/MemberAssignmentsCleaned.java`
- Modify: `services/notification-service/src/main/java/dev/platform/notification/infrastructure/persistence/entity/NotificationEntity.java`
- Delete or retire the deprecated duplicate JPA mapping `services/notification-service/src/main/java/dev/platform/notification/domain/Notification.java` after confirming no callers; `NotificationEntity` remains the only mapping for `notifications`
- Add: user-service, board-service and task-service Redis client dependency/configuration for their snapshot adapters
- Modify: `services/user-service/src/main/resources/application.yml`, `services/board-service/src/main/resources/application.yml`, and `services/task-service/src/main/resources/application.yml` for Redis connection and snapshot-sync flags

1. Add `SystemRoles` and `UserSystemRoles` JPA mappings in user_db with `USER` and `SYSTEM_ADMIN` role codes; do not implement a first-admin bootstrap or trust role values from client input.
2. Persist `auth_version` on User and both `membership_version` and `authorization_version` per `(project_id,user_id)` in board-service. Membership generation survives removal and increments on each new active generation; authorization version increments on every auth-state change, including removal and role change.
3. Add source-side snapshot sync job entities, recorders and Redis workers in user-service and board-service. Each source mutation, Outbox event and durable snapshot-sync job must be written in the same owning-DB transaction. Define Outbox payloads/constants for `USER_STATUS_CHANGED` (`userId`, `status`, `authVersion`), `USER_SYSTEM_ROLES_CHANGED` (`userId`, `systemRoles`, `authVersion`), `MEMBERSHIP_CHANGED` (`projectId`, `userId`, `status`, `role`, `membershipVersion`) and `MEMBERSHIP_REMOVED` (`projectId`, `userId`, `membershipVersion`); Membership envelope `aggregateVersion` is `authorizationVersion`. Workers retry Redis apply and compare `authorizationVersion` atomically so an older retry cannot overwrite a newer snapshot. Board snapshot-sync jobs link to `operationId` for future status lookup; the mutation/status API remains TODO and must not be described as end-to-end.
4. Add a Task authorization-snapshot reader port and Redis adapter. `CardController` calls `ProjectAuthorizationGuard` before every Card/Comment GET, POST or PATCH, using trusted `X-User-Id` supplied by Gateway and route `projectId`; missing snapshots or Redis errors fail closed. For assign/create, read the target assignee's `membershipVersion` from Redis and pass it through `CreateCardCommand`/`AssignCardCommand`; never accept a client-supplied version.
5. Add `projectId` to Card create/read context and use the Project-scoped route shapes from Task 1. Persist it as `cards.project_id` scalar; existing Card operations must match path Project ID against the stored value.
6. Preserve a single assignee in the API while replacing inline `cards.assignee_id` with `task_assignees`: `task_id`, `user_id`, `assigned_by_user_id`, `assigned_at`, `membership_version`, `is_primary`; PK `(task_id,user_id)` and FK `task_id -> cards.id` within task_db. Serialize primary-assignment replacement by locking the Card row in the same transaction; this is the Kỳ 1 invariant because no migration tool/partial-index mechanism has been selected. Account/Project IDs remain scalar.
7. Extend cleanup request/completion payloads with `membershipVersion`; retain `projectId`, `userId`, `boardIds`, and operation tracking. Persist source envelope `aggregateVersion` in the Task cleanup job and retain it in completion; do not substitute `membershipVersion` for it.
8. Add a durable fence keyed by `(project_id,user_id,membership_version)`. Provide a transactional recorder seam that writes Inbox, fence, and durable cleanup job before a future listener ACK; do not claim a Rabbit listener is wired. Assignment changes serialize against the fence; a retryable worker clears only the matching generation and emits `MemberAssignmentsCleaned` through Outbox after cleanup. Neither the cleanup job nor its completion gates Redis revoke completion.
9. Keep Board/Column ownership validation as an explicit integration seam; do not add an unprotected internal REST call. Document the trusted Board-scope source required before enabling create-card end-to-end.
10. Add nullable `project_id` to NotificationEntity while preserving `user_id` recipient and optional `board_id`/`card_id` context.
11. Retire the deprecated duplicate `domain.Card`/`domain.CardComment` JPA mappings after confirming no callers, so they cannot continue creating the obsolete `cards.assignee_id` column alongside `CardEntity`.
12. Retire the deprecated duplicate `services/notification-service/src/main/java/dev/platform/notification/domain/Notification.java` JPA mapping after confirming no callers; only NotificationEntity maps `notifications`.
13. Align JPA entity scanning and repository scanning for all new entities.

**Expected:** Java skeleton expresses the approved target schema and event fields; source services expose transaction-bound durable Redis sync seams; Task checks snapshots on every protected Card/Comment route and reads both Membership versions from Redis, using `membershipVersion` for assignments. Board Project/Membership mutation wiring, Rabbit listener/ACK wiring, migration strategy and cross-service Board validation remain explicit integration TODOs.

## Task 5: Self-review, static consistency checks and compile

**Files:** No new source files; inspect the changed files and repository-wide references.

1. Run repository-wide searches for `DELETING`, Account/Project deletion event names, `PROJECT_CASCADE`, `board_write_locks`, `assignee_id`, old Task routes, old Invitation inbox routes and `aggregateType`; classify any remaining hit as K2 history, deprecated code intentionally removed, or an active mismatch.
2. Check links to Shared and account-deletion guide resolve; verify each service database document describes its own DB and no cross-database FK.
3. Review full changed files and lifecycle: membership active → removed → Redis apply → cleanup job retry → rejoin with newer version; Account deletion is K2-only; Notification context remains attached to recipient.
4. Run `mvn -DskipTests package` from repository root to verify compile-ready modules. Do not run test suites in this task.
5. Check changed text files for trailing whitespace and final LF; use formatting/lint commands available in the repository if configured.
6. Run the required quick agent scan of the completed diff and grep every newly introduced route, event name, DB column and enum value across the repository; fix any [Critical] inconsistency before reporting.

**Expected:** A build result, concrete grep evidence and self-review findings accompany the final summary. Any runtime behavior not exercised must be called out as an integration TODO.
