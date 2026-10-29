# Account Deletion and Project Cascade Skeleton Implementation Plan

> **Superseded (2026-10-29):** This plan records the earlier Kỳ 1 cascade scaffold proposal and is retained only as history. Account deletion and Project cascade are Kỳ 2; do not execute these steps. The approved direction is recorded in [architecture decisions](../../project/architecture-decisions.md) and [Kỳ 2 note](../../project/account-deletion-skeleton-guide.md).

> **For agentic workers:** This plan scaffolds the approved flow across the existing services. It is not a production-ready deletion implementation.

> **Decision update (2026-10-28):** On Member removal/account deletion from a Project that remains, revoke Membership first and enqueue Task unassignment as background work. The original “unassign before removing Membership” checklist below has been updated to this decision. Account deletion still waits for the background cleanup before it reports the whole Account operation `COMPLETED`.

> **Authorization boundary (2026-10-29):** “Trusted Admin” in this plan means a Gateway-authenticated `SYSTEM_ADMIN` for Account lifecycle operations only; it grants no Project/Board permission. Whether the same Account can also use Projects remains open. Keep Admin routes disabled until Gateway identity and current Account status/revocation are verified, failing closed when they cannot be verified. See [current architecture decisions](../../project/architecture-decisions.md) and [Kanban API contract](../../api/kanban-api.md).

**Goal:** Add compile-ready service boundaries, persistence models, RabbitMQ contracts, and a per-service completion guide for account deletion and Project cascade cleanup.

**Architecture:** `user-service` owns the parent Account Deletion Operation; `board-service` coordinates child Project operations; `task-service` and `notification-service` process their own data using durable jobs and checkpoints. `event-contracts` contains only versioned DTOs; `messaging-support` contains the shared Outbox/Inbox persistence boundary and confirm-aware Outbox Relay. Admin routes and Rabbit consumers remain disabled by default until trusted Admin authorization, Project/Membership persistence, and write guards are connected.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring AMQP, Spring Data JPA, PostgreSQL, Docker Compose.

**Spec:** `docs/project/architecture-decisions.md`, section “Xóa Account và cascade Project”.

## Global Constraints

- Each service writes only to its own database; no cross-service database access or foreign keys.
- Account deletion starts with `DELETING`; account PII is removed only after Board Service confirms Project cleanup and Task Service confirms unassignment.
- `Project.status` and `Board.status` remain `ACTIVE`, `COMPLETED`, or `CANCELLED`; deletion progress uses a separate operation record.
- Task and Notification consumers receive `boardIds`; Task Service deletes Card/Comment, Notification Service deletes notifications, and Board Service owns Board/Column/Project deletion.
- Every event has a stable `eventId` across transport retries; `operationId` and `projectDeletionId` remain stable across their respective workflows.
- Event payload record fields in this skeleton are minimum provisional shapes, not a closed API/event contract; `architecture-decisions.md` remains authoritative until the response payloads and failure semantics are discussed and approved.
- The Account deletion route, Outbox Relay, and Rabbit consumers default to disabled. The route must not be enabled until the Gateway supplies a trusted Admin identity and the login path rejects `DELETING` accounts; Board consumers must also remain disabled until the Project/Membership adapter and write-lock barrier are implemented.
- Do not add or run automated tests in this skeleton task; use compile and configuration validation only.

## Review Focus

- Duplicate delivery: Inbox uniqueness is scoped by consumer and event ID; duplicate requests must not create duplicate deletion jobs.
- Commit succeeds but ACK is lost: the consumer must find the durable job/checkpoint on redelivery and ACK without starting duplicate side effects.
- Batch restart: workers resume from persisted checkpoints and emit completion only after all service-owned rows are removed.
- Concurrent writes during deletion: Task Service write-lock checks must be wired into every Card mutation before enabling the listener.
- Empty or large Project: empty `boardIds` completes as a no-op; large sets use bounded batches.
- Account is only a Member: preserve the Project and wait for Task Service to confirm assignee cleanup before final Account deletion.

---

### Task 1: Event Contracts and Local RabbitMQ Infrastructure

**Files:**
- Create: `shared/event-contracts/pom.xml`
- Create: `shared/event-contracts/src/main/java/dev/platform/contracts/EventEnvelope.java`
- Create: `shared/event-contracts/src/main/java/dev/platform/contracts/EventTypes.java`
- Create: payload records `AccountDeletionRequested`, `ProjectDeletionRequested`, `ProjectTaskDataDeleted`, `ProjectNotificationsDeleted`, `MemberAssignmentsCleanupRequested`, `MemberAssignmentsCleaned`, `ProjectCleanupCompleted`, and `AccountProjectCleanupCompleted` under `dev.platform.contracts.event`.
- Create: `shared/messaging-support/pom.xml` and common `OutboxEventEntity`, `InboxEventEntity`, repositories, transactional writer/recorder, conditional confirm-aware Relay, and Rabbit exchange configuration under `dev.platform.shared.messaging`.
- Modify: `pom.xml`
- Modify: `docker-compose.yml`
- Modify: the four service `pom.xml` files, application classes (JPA scan/import shared support), and `src/main/resources/application.yml` files.

**Interfaces:**
- `EventEnvelope<T>` carries `UUID eventId`, `String eventType`, `int schemaVersion`, `String aggregateType`, `String aggregateId`, `long aggregateVersion`, `OffsetDateTime occurredAt`, and `T payload`.
- `PROJECT_DELETION_REQUESTED` carries `operationId`, `projectDeletionId`, `projectId`, `boardIds`, and `reason`; the same IDs survive transport retries.
- Other payload record fields are provisional and must not be treated as a final contract; document their open status in the service guide.
- Each service uses its own copy of the shared `outbox_events` and `inbox_events` tables and common repositories; do not create service-specific duplicate Inbox/Outbox entities. Service-specific deletion job and operation tables remain in their own modules.
- Each service application explicitly scans its service root package plus `dev.platform.shared.messaging.persistence.entity` for entities, scans its service root plus `dev.platform.shared.messaging.persistence.repository` for repositories, and imports `MessagingSupportConfiguration`. Root scanning preserves the existing deprecated entities/repositories. The support config only creates Rabbit beans when `dev-platform.messaging.enabled=true`; the relay has its own false-by-default property.

- [x] Add the pure Java contracts module to the Maven reactor; keep Spring/JPA dependencies out of it.
- [x] Add Spring AMQP dependencies and environment-based RabbitMQ settings to each service.
- [x] Add a local RabbitMQ container to Compose with credentials supplied through environment variables.
- [x] Implement a scheduled Outbox Relay that marks an event SENT only after correlated publisher confirm and no publisher return; otherwise leave it retryable with a configurable delay. Relay default stays disabled.
- [x] Add `messaging-support` as a compile-time library in all four services; configure correlated confirms, publisher returns, and mandatory publishing.

### Task 2: User Service Parent Operation

**Files:**
- Create: `services/user-service/src/main/java/dev/platform/user/domain/AccountStatus.java`
- Create: `AccountDeletionOperationEntity`, `DeletedAccountTombstoneEntity`, their JPA repositories, command/response records, request/get input ports, persistence output port/adapter, transactional application service, and completion handler under the existing `user` package layout.
- Create: a minimal deleted-account tombstone entity/repository that stores the user ID and deletion timestamp without PII.
- Modify: `services/user-service/src/main/java/dev/platform/user/domain/aggregate/UserAggregate.java`, `infrastructure/adapter/SaveUserAdapter.java`, and `infrastructure/persistence/entity/UserEntity.java` so `AccountStatus` survives persistence and rehydration.
- Modify: `services/user-service/src/main/java/dev/platform/user/infrastructure/persistence/repository/UserJpaRepository.java`.

**Interfaces:**
- `RequestAccountDeletionPort.request(RequestAccountDeletionCommand)` returns an operation ID and current state; the implementation marks the Account `DELETING`, saves the operation and appends `ACCOUNT_DELETION_REQUESTED` in one transaction.
- `GetAccountDeletionOperationPort.get(UUID operationId)` returns persisted progress.
- `AccountAccessGuard.requireActive(UserAggregate)` is the login integration point. The current repository has no `LoginUserPort` implementation, so the guide must require wiring this guard before enabling Account deletion.
- Do not create an HTTP controller in this skeleton: exact Admin and operation-status routes remain an open contract decision. The guide must say the controller can only be added after trusted Admin identity and login status checks exist.

- [x] Add `ACTIVE`/`DELETING` Account state, preserve it through aggregate/entity mapping, and reject deletion transitions from any state other than `ACTIVE`.
- [x] Persist Account state, parent operation, and `ACCOUNT_DELETION_REQUESTED` Outbox row in one transaction.
- [x] Keep the request/get use-case ports ready for the later API contract; do not publish a route or claim a status URL in this skeleton.
- [x] Add an idempotent completion handler that writes Inbox, tombstone, PII removal, and operation completion in one DB transaction. Rabbit listener wiring remains TODO.

### Task 3: Board Service Project Operation Boundary

**Files:**
- Create: `ProjectDeletionOperationEntity`/repository, `ProjectDeletionDataPort`, and the typed `ProjectDeletionCoordinator` interface under `services/board-service/src/main/java/dev/platform/board/deletion/`.
- Modify: `services/board-service/pom.xml` and `src/main/resources/application.yml`.
- Modify: `BoardServiceApplication` to scan the `dev.platform.board` root and shared messaging entity/repository packages, and import `MessagingSupportConfiguration`.

**Interfaces:**
- `ProjectDeletionDataPort` exposes owned Projects, Member-only Projects, pending Invitations, each Project's `boardIds`, write-lock creation, and Project graph deletion; its adapter is an explicit TODO until the Project/Membership module is implemented.
- `ProjectDeletionCoordinator` is currently an interface only. Implement/register it with `dev-platform.deletion.board-consumer.enabled=true` only after a `ProjectDeletionDataPort` adapter exists. No Rabbit listener is registered by default.

- [x] Create the Board-owned child operation persistence shape keyed by `projectDeletionId` and the typed Project data/coordinator boundaries.
- [ ] Implement Inbox event handling, cancel pending Invitations, revoke Member-only Membership first, and enqueue idempotent assignment cleanup in the same Board DB transaction/Outbox flow.
- [ ] Implement one child operation per owned Project and establish the write barrier without changing `Project.status`.
- [ ] Publish child requests with `boardIds`; wait for both downstream confirmations before deleting Project, Board, Column, and Membership.
- [ ] Publish Account cleanup completion only after owned Project cascades and Member-only unassignments are confirmed.

### Task 4: Task Service Deletion Consumer

**Files:**
- Create: `TaskDeletionJobEntity`/repository, `BoardWriteLockEntity`/repository, the `TaskDeletionHandler` interface, and `TaskWriteGuard` under `services/task-service/src/main/java/dev/platform/task/deletion/`.
- The job shape is `jobId`, unique `deduplicationKey`, `jobType`, `operationId`, optional `projectDeletionId`, `projectId`, serialized `boardIds`, optional `targetUserId`, `status`, comment/card cursors, `attemptCount`, `lastError`, `createdAt`, and `updatedAt`.
- The bounded delete/unassign worker and Rabbit listener are explicit TODOs; this task creates the handler interface and durable checkpoint shape only.
- Modify: `services/task-service/pom.xml` and `src/main/resources/application.yml`.
- Modify: `TaskServiceApplication` to scan the `dev.platform.task` root and shared messaging entity/repository packages, and import `MessagingSupportConfiguration`.
- Extend: `CardJpaRepository` and `CardCommentJpaRepository` with bounded page queries by `boardIds` for the future worker.

**Interfaces:**
- The future consumer must persist Inbox plus a durable job before ACK; uniqueness by business operation key also catches duplicate commands published with a different event ID.
- Card write handlers must check the Board deletion lock before mutations; wire this guard into create, move, assign, and comment flows before enabling the consumer.

- [x] Create durable job/checkpoint and Board lock shapes, bounded query methods, and the typed handler boundary.
- [ ] Implement Inbox + job transaction and ACK only after persistence.
- [ ] Implement Comment-before-Card deletion and Member-only assignment cleanup in bounded batches after Membership revocation; preserve Comment/history.
- [ ] Resume unfinished jobs from the saved checkpoint after restart; treat already-absent rows as successfully deleted.
- [ ] Publish Project task-data completion only after all batches finish.

### Task 5: Notification Service Deletion Consumer

**Files:**
- Create: `NotificationDeletionJobEntity`/repository and the `NotificationDeletionHandler` interface under `services/notification-service/src/main/java/dev/platform/notification/deletion/`.
- The job shape is `jobId`, unique `projectDeletionId`, `projectId`, serialized `boardIds`, `status`, `lastProcessedNotificationId`, `attemptCount`, `lastError`, `createdAt`, and `updatedAt`.
- The bounded deletion worker and Rabbit listener are explicit TODOs; this task creates the handler interface and durable checkpoint shape only.
- Modify: `services/notification-service/pom.xml` and `src/main/resources/application.yml`.
- Modify: `NotificationServiceApplication` to scan the `dev.platform.notification` root and shared messaging entity/repository packages, and import `MessagingSupportConfiguration`.
- Extend: `NotificationJpaRepository` with bounded page queries by `boardIds` for the future worker.

- [x] Create durable notification cleanup job/checkpoint, repository, and typed handler boundary.
- [ ] Implement Inbox + job transaction and ACK only after persistence.
- [ ] Delete Project notifications using `boardIds` in resumable batches.
- [ ] Publish notification cleanup completion after all rows are deleted.
- [ ] Leave account-owned notification cleanup scope configurable/TODO until it is separately agreed.

### Task 6: Service Completion Guide

**Files:**
- Create: `docs/project/account-deletion-skeleton-guide.md`
- Modify: `docs/project/architecture-decisions.md` to link the guide and distinguish scaffolded code from remaining implementation work.

- [x] Document each service's responsibility, created classes, missing adapter, configuration flag, and order of completion.
- [x] Document the event flow, operation ID hierarchy, Outbox/Inbox ACK boundary, batch checkpoint behavior, and local RabbitMQ setup.
- [x] List remaining decisions: exact Admin/status routes, notification scope, write-lock barrier, retry/backoff limits, and DLQ/alert behavior.

### Final Verification

- [x] Run `mvn -DskipTests compile` from the repository root; expected result is a successful compile of the reactor without running tests.
- [x] Run `docker compose config`; expected result is valid Compose configuration.
- [x] Check modified text files for trailing whitespace and a final newline.
- [x] Review the service guide against the implementation and confirm all disabled integrations are clearly marked.
