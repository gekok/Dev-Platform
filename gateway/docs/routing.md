# Gateway routing reference

Route table này được đối chiếu với gateway/src/main/resources/application.yml. Gateway chạy WebFlux trên port 8080, giữ nguyên path khi proxy và hiện dùng upstream localhost cố định.

## Route khai báo trong source

| Thứ tự | Route ID | Path | Upstream | Ghi chú |
|---:|---|---|---|---|
| 1 | user-service | /api/v1/users/**, /api/v1/auth/** | http://localhost:8081 | User/Auth runtime |
| 2 | task-service | /api/v1/projects/*/boards/*/cards/**, /api/v1/projects/*/cards/** | http://localhost:8083 | Card routes Project-scoped; phải đứng trước route rộng của board-service |
| 3 | board-service | /api/v1/projects/**, /api/v1/boards/** | http://localhost:8082 | Project target và Board-first runtime |
| 4 | task-service-ws | /ws/** | http://localhost:8083 | Route có khai báo; upstream chưa đăng ký WebSocket handler |
| 5 | notification-service | /api/v1/notifications/** | http://localhost:8084 | Notification client routes |

Path được proxy nguyên dạng, không rewrite. Project-scoped Card route được khai báo cụ thể trước /api/v1/projects/** để Task nhận đúng request.

## Routes chưa được khai báo

- /api/v1/admin/**: Account administration thuộc user-service; chưa có Gateway route/controller hoàn chỉnh.
- /api/v1/users?query=... và /api/v1/users/profiles/**: directory/bulk profile là API mục tiêu, chưa có controller target.
- /internal/v1/notifications/**: notification-service có controller nội bộ, nhưng Gateway không expose route này; controller hiện chưa có service-token enforcement.
- Các route Invitation/Membership được bao phủ bởi /api/v1/projects/** trong cấu hình, nhưng board-service chưa có Project/Membership controller runtime.

## Identity và authorization

Cấu hình hiện tại không khai báo JWT authentication, authorization filter, header sanitization, rate limit hoặc retry policy. Các controller vẫn có identity header stub; không coi route proxy là bằng chứng identity được xác thực.

Target: Gateway xác thực Account và chuyển tiếp identity tin cậy, không tin X-User-Id/role do client gửi. Snapshot Authentication thuộc user-service; snapshot Project/Membership thuộc board-service. Task đọc snapshot Redis và từ chối khi snapshot thiếu/lỗi. Gateway không sở hữu Authorization Snapshot. Internal REST giữa service chưa triển khai vì chưa có bảo mật service-to-service.

Account phải ở trạng thái nghiệp vụ ACTIVE để được xác thực/cấp quyền. Kỳ 1 không có Account deletion endpoint/status hoặc cascade route. System Admin chỉ quản trị Account, không mặc định có quyền Project.

## Route priority mục tiêu

Khi bổ sung route:

1. /api/v1/projects/*/boards/*/cards/** và /api/v1/projects/*/cards/** → task-service.
2. /api/v1/projects/** và /api/v1/boards/** → board-service.
3. /api/v1/admin/**, /api/v1/users/**, /api/v1/auth/** → user-service.
4. /api/v1/notifications/** → notification-service.

Đây là route design; chỉ các route trong bảng trên đã hiện diện trong YAML. Khi upstream lỗi hoặc route không match, không suy diễn response nghiệp vụ từ contract của service.

## Dependency

- Spring Cloud Gateway Server WebFlux; không dùng Servlet MVC.
- Gateway không có datasource, repository hoặc migration.
- Actuator có trong pom.xml; exposure/access policy chưa cấu hình tại routing YAML.
- Xem [Kanban API](../../docs/api/kanban-api.md) và [architecture decisions](../../docs/project/architecture-decisions.md).
