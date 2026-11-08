# Issue #58 — WP0 Baseline

Ngày kiểm tra: 2026-11-08

Baseline này ghi lại trạng thái trước khi triển khai các work package frontend. Nó không xác nhận backend đã tích hợp end-to-end.

## Git checkout

- Branch: `feature/web-demo-issue-58`
- HEAD: `9f08b79afd45f774cca38d84de3cfadc881312d9`
- Commit: `Merge remote-tracking branch 'origin/pr/53' into feature/web-demo-issue-58`
- Upstream: `origin/main`
- Quan hệ với `origin/main`: ahead 2, behind 0 (`git rev-list --left-right --count origin/main...HEAD` trả `0 2`).
- Working tree sạch trước khi chạy WP0 và sau khi cài dependency/chạy checks, trước khi thêm file baseline này.
- PR #53 đã được merge vào nhánh làm việc; trạng thái PR trên GitHub không bị thay đổi bởi thao tác này.

## Môi trường frontend

- Node.js `v22.22.2`; npm `10.9.7`.
- `web-demo/node_modules` ban đầu chưa có.
- `npm ci` cài thành công đúng dependency lockfile: 379 packages added, 380 audited. Không sửa `package.json` hoặc `package-lock.json`.
- npm báo 5 vulnerability advisories: 3 moderate và 2 high. Chưa chạy `npm audit` để phân tích từng advisory.
- `node_modules/` và `dist/` là output bị Git ignore; sau kiểm tra working tree vẫn sạch.

## Kết quả kiểm tra

| Lệnh | Kết quả | Ghi chú |
|---|---|---|
| `npm ci` | Pass | Dùng lockfile hiện có; không cập nhật package manifest/lockfile. |
| `npm test` | **Pass: 82; fail: 0; skipped: 0** | Chạy lại ngoài sandbox sau khi sandbox chặn worker subprocess bằng `EPERM`. |
| `npm run lint` | Pass | Exit code 0. |
| `npm run build` | Pass | Vite 5.4.21; 2,516 modules transformed; build thành công. Lần chạy trong sandbox bị `EPERM` khi esbuild spawn worker; lần chạy được cấp quyền ngoài sandbox thành công. |

`npm test` phát hiện 13 file test; cả 13 file đều pass (82 tests tổng cộng, 0 fail, 0 skipped):

- `src/shared/query/queryKeys.test.js`
- `src/mocks/handlers/projects.test.js`
- `src/mocks/handlers/preferences.test.js`
- `src/mocks/handlers/notifications.test.js`
- `src/mocks/handlers/cards.test.js`
- `src/mocks/handlers/boards.test.js`
- `src/mocks/handlers/auth.test.js`
- `src/mocks/data/databaseRepository.test.js`
- `src/features/projects/projectEntry.test.js`
- `src/features/boards/boardSearch.test.js`
- `src/features/boards/preferenceNavigation.test.js`
- `src/features/auth/roles.test.js`
- `src/features/cards/cardIds.test.js`

Các test này kiểm tra frontend/MSW/demo behavior. Chúng không chứng minh request đã đi qua Gateway hoặc backend runtime.

## Blocker đã biết trước implementation

- Backend mode chưa tồn tại: [main.jsx](../src/main.jsx) khởi động MSW vô điều kiện; [axios.js](../src/shared/services/axios.js) dùng base URL tương đối `/api/v1`, không có cấu hình Gateway URL.
- Frontend Project/Board/Card đang dựa vào mock; Project/Membership/Invitation và Board Project-scoped backend APIs chưa có implementation runtime đủ để tích hợp.
- Card API client dùng route Board-first và kỳ vọng response array; Task source dùng Project-scoped route, yêu cầu `columnId` và trả `{items, nextCursor}`. Frontend chưa có per-Column infinite scroll. Cursor wire/error details còn đang mở.
- Task Card routes còn fail-closed theo readiness mặc định; Create/Move cần nguồn xác minh Board/Column thật. Không thay dependency này bằng mock trong Backend mode.
- UI Membership/Invitation chưa có. Comment API path của frontend chưa theo Project-scoped route.
- Notification list/read UI và API client đã có; việc tạo Notification end-to-end còn phụ thuộc Task Outbox và Notification consumer/broker integration.
- Admin UI là phần kế thừa PR #53; quản trị Account nằm ngoài scope Issue #58.

## Quy tắc dùng baseline

- Không sửa package manifest chỉ để làm xanh baseline.
- Mock tests chỉ xác nhận Demo/MSW mode; không dùng chúng làm bằng chứng backend mode.
- Cập nhật kết quả baseline sau khi dependency hoặc source thay đổi có chủ đích.
