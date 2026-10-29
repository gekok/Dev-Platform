# Repository Guidelines

## Project Structure
- `services/` holds four Spring Boot modules (`src/main/java/dev/platform`, per-service `docs/`).
- `gateway/` is the HTTP entry point; `shared/` contains event contracts and Outbox/Inbox.
- `web-demo/` is React/Vite with MSW; `mobile/` is Flutter; `docs/` holds shared architecture/API guidance.
- Each service owns its database; do not add cross-service foreign keys.

## Build, Test, and Development
- `docker compose up -d` starts PostgreSQL/RabbitMQ; K1 snapshots need Redis at `localhost:6379`, absent from Compose.
- At the root, use `mvn -DskipTests package` to build or `mvn test` to run backend tests.
- Run a service with `cd services/<service> && mvn spring-boot:run`; Gateway uses `cd gateway && mvn spring-boot:run`.
- Demo: `cd web-demo && npm ci && npm run dev`; checks: `npm test`, `npm run lint`, `npm run build`.
- Mobile: `cd mobile && flutter pub get`; check with `flutter analyze` and `flutter test`.

## Code Style and Tests
- Use Java 21, four spaces, and lower-case `dev.platform...` packages. Preserve `api` / `application` / `domain` / `infrastructure` boundaries: controllers delegate to application ports; persistence stays in `infrastructure`. Types are PascalCase; methods and fields are camelCase.
- In React, follow `web-demo/src/features`; feature `services/` call APIs through shared Axios. Use two-space JS/JSX, PascalCase components, and `use...` hook names.
- Name Java tests `*Test.java` under module `src/test/java`; demo tests `*.test.js` near the feature; Flutter tests go in `mobile/test` and follow `flutter_lints`. Cover behavior and authorization/error paths.

## Documentation Workflow
- Read `docs/README.md` first: architecture decisions record agreed/open design; `docs/api/kanban-api.md` describes target HTTP contracts; service docs distinguish runtime mappings from target schemas. Source code is runtime evidence.
- Keep docs with related code in the same PR. Update architecture decisions for decision changes; shared API and owning service API docs for routes/payloads; Gateway routing docs when routes change; service database docs and versioned migrations once migration tooling is selected; integration and shared docs/contracts for cross-service events or flows.
- Update task sheets/runtime descriptions as implementation status changes. Keep proposals marked open, never describe target design as shipped, and update `docs/README.md` when document ownership or navigation changes.

## Commits and Pull Requests
- Follow Conventional Commit prefixes in history (`feat:`, `fix:`, `docs:`, `refactor:`); keep commits focused.
- PRs must list scope, affected services/contracts, verification results, and API/schema impacts; include screenshots for UI changes.

## Security
- Never commit credentials; `.env` is ignored. Treat `X-User-Id` as untrusted until Gateway authentication and identity-header enforcement are implemented.
