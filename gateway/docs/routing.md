# Gateway routing reference

Đây là route table được khai báo trong `gateway/src/main/resources/application.yml`; URI upstream, thứ tự và các route chưa có được mô tả theo source hiện tại.

## HTTP/WebSocket route map

| Order | Route ID | Path predicate | Upstream | Ghi chú |
|---:|---|---|---|---|
| 1 | `user-service` | `/api/v1/users/**`, `/api/v1/auth/**` | `http://localhost:8081` | User profile và Auth controller mappings |
| 2 | `task-service` | `/api/v1/cards/**`, `/api/v1/boards/**/cards/**` | `http://localhost:8083` | Specific Card routes; phải đứng trước route Board tổng quát |
| 3 | `board-service` | `/api/v1/boards/**` | `http://localhost:8082` | Board-first runtime routes |
| 4 | `task-service-ws` | `/ws/**` | `http://localhost:8083` | Có route declaration; upstream chưa đăng ký WebSocket handler |
| 5 | `notification-service` | `/api/v1/notifications/**` | `http://localhost:8084` | Notification client routes |

Gateway không rewrite path trong config hiện tại; path được chuyển tiếp nguyên dạng. Upstream URI hiện là localhost hard-coded, không tách theo profile/env trong file này.

## Route order

`/api/v1/boards/{boardId}/cards/**` thuộc task-service, còn các route Board/Column thuộc board-service. Nếu chuyển route `/api/v1/boards/**` lên trước route task-specific, request Card có thể bị route Board tổng quát bắt trước. Giữ thứ tự trong bảng đến khi thay predicates bằng route strategy khác.

## Chưa được định tuyến

- `/api/v1/projects/**`: target thuộc board-service nhưng chưa có Gateway route.
- `/api/v1/users/me/project-invitations`: API target thuộc board-service; phải đặt route exact/specific trước `/api/v1/users/**` để không bị user-service bắt nhầm.
- `/api/v1/users?query=...` và `/api/v1/users/profiles/**`: API directory/bulk profile đề xuất thuộc user-service; chưa có API controller target.
- Các invitation accept/reject/cancel routes lồng dưới `/api/v1/projects/**`: target thuộc board-service.
- `/api/v1/admin/accounts/**` và `/api/v1/admin/deletion-operations/**`: target thuộc user-service; route/payload/status operation mới ở mức đề xuất và chưa có Gateway route.
- `/internal/v1/notifications/**`: internal controller route hiện có trong notification-service nhưng Gateway không expose route này.
- Redis snapshot vẫn thuộc mục tiêu kiến trúc Kỳ 1 nhưng không phải HTTP route. Source Gateway hiện chưa tích hợp Redis; Gateway không sở hữu Authorization Snapshot. Phần route này chỉ mô tả cấu hình định tuyến.

## Security và lỗi upstream

Route config không khai báo filter xác thực JWT hoặc authorization, cũng không loại bỏ/ghi đè `X-User-Id`. Các service hiện có controller stub đọc header này; không để client tự quyết định identity khi đưa hệ thống vượt khỏi local demo. Security contract và hardening Gateway cần theo architecture decision trước khi coi đây là trust boundary.

### Thứ tự route mục tiêu Project-first

Khi cấu hình target, ưu tiên route cụ thể trước route prefix rộng:

1. `/api/v1/users/me/project-invitations` → board-service (nằm dưới prefix `/users/**` hiện route user-service).
2. `/api/v1/cards/**` và `/api/v1/boards/**/cards/**` → task-service.
3. `/api/v1/projects/**` và `/api/v1/boards/**` → board-service.
4. `/api/v1/admin/**`, `/api/v1/users/**` (gồm directory/profile), `/api/v1/auth/**` → user-service; admin routes chỉ bật khi Gateway xác thực System Admin.
5. `/api/v1/notifications/**` → notification-service.

Đây là route design, không phải cấu hình đã có. API paths/status còn mở được liệt kê tại [Kanban API contract](../../docs/api/kanban-api.md).

Không có response envelope Gateway riêng trong source. Khi upstream không khả dụng hoặc route không match, không suy diễn status/body nghiệp vụ từ API contract của service; cần quan sát runtime Gateway và upstream để xác định response thực tế.

## Cấu hình và dependency

- Port: `8080`.
- Framework: Spring Cloud Gateway Server WebFlux; không dùng Servlet MVC.
- Dependency Actuator có trong `gateway/pom.xml`; endpoint expose/access policy không được cấu hình trong route file này.
- Không có datasource, repository hoặc migration trong Gateway.
