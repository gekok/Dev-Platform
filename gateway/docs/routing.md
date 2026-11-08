# Gateway routing reference

Route table này được đối chiếu với gateway/src/main/resources/application.yml. Gateway chạy WebFlux trên port 8080, giữ nguyên path khi proxy; upstream mặc định dùng localhost và có thể cấu hình qua `gateway.upstream.user`, `gateway.upstream.board`, `gateway.upstream.task`, `gateway.upstream.task-websocket`, `gateway.upstream.notification`.

## Route khai báo trong source

| Thứ tự | Route ID | Path | Upstream | Ghi chú |
|---:|---|---|---|---|
| 1 | user-service | /api/v1/users/**, /api/v1/auth/**, /api/v1/admin/** | http://localhost:8081 | User/Auth/Admin runtime; Admin controller cũng kiểm tra `SYSTEM_ADMIN` |
| 2 | task-service | /api/v1/projects/*/boards/*/cards/**, /api/v1/projects/*/cards/** | http://localhost:8083 | Card routes Project-scoped; phải đứng trước route rộng của board-service |
| 3 | board-service | /api/v1/projects/**, /api/v1/boards/** | http://localhost:8082 | Project target và Board-first runtime |
| 4 | task-service-ws | /ws/** | ws://localhost:8083 | WebSocket upstream riêng; handshake dùng ticket một lần do Gateway cấp và xác minh. Task runtime vẫn có readiness gates mặc định tắt. |
| 5 | notification-service | /api/v1/notifications/** | http://localhost:8084 | Notification client routes |

Path được proxy nguyên dạng, không rewrite. Project-scoped Card route được khai báo cụ thể trước /api/v1/projects/** để Task nhận đúng request.

## Routes chưa được khai báo

- /internal/v1/notifications/**: notification-service có controller nội bộ, nhưng Gateway không expose route này; controller hiện chưa có service-token enforcement.
- Các route Invitation/Membership được bao phủ bởi /api/v1/projects/** trong cấu hình, nhưng board-service chưa có Project/Membership controller runtime.

## Identity và authorization

Gateway có WebFilter xác thực JWT RSA256 bằng public key lấy từ user-service, kiểm tra issuer, `exp`, `iat` hiện diện và UUID `sub`. Public key được cache đến khi process restart. `POST /api/v1/auth/login`, `GET /api/v1/auth/public-key` và `GET /actuator/health` là các route public chính xác theo method/path; route khác yêu cầu Bearer token.

Sau khi xác thực token, Gateway đọc `auth:account:{accountId}` từ Redis và chỉ cho Account `ACTIVE` đi tiếp. Token thiếu/sai trả `401`; Account không ACTIVE trả `403`; snapshot thiếu/hỏng hoặc Redis không sẵn sàng trả `503`. Gateway gỡ `X-User-Id` client gửi, đặt lại từ JWT `sub`, giữ nguyên `Authorization` khi proxy. JWT có `email`, `fullName`, `roles` và `authVersion`, nhưng Gateway không dùng claim role để cấp Project permission.

Gateway xác thực Account và chuyển identity tin cậy qua `X-User-Id`, không dùng role claim để cấp quyền Project. Snapshot Authentication thuộc user-service; snapshot Project/Membership thuộc board-service. Task đọc snapshot Redis và từ chối khi snapshot thiếu/lỗi. Với WebSocket, Gateway chỉ cấp ticket sau xác thực JWT và Account snapshot; Task kiểm tra Membership khi handshake, định kỳ và trước khi gửi event. Gateway không sở hữu Authorization Snapshot.

Browser lấy ticket bằng `POST /api/v1/realtime/boards/{boardId}/ws-ticket` với Bearer token, rồi mở `ws(s)://<gateway>/ws/boards/{boardId}?ticket=<ticket>`. Ticket gắn với Account và Board, mặc định hết hạn sau 30 giây, chỉ dùng một lần và được tiêu thụ nguyên tử qua Redis. `GATEWAY_WS_TICKET_TTL` phải từ 1 giây trở lên vì response biểu diễn thời hạn bằng giây. Gateway yêu cầu một `Origin` khớp chính xác allowlist `GATEWAY_WS_ALLOWED_ORIGINS` (mặc định `http://localhost:5173`), bỏ query ticket cùng các credential/header bảo mật client gửi, rồi đặt lại `X-User-Id` từ ticket đã xác minh. Ticket thiếu, sai Board, hết hạn hoặc đã dùng trả `401`; Origin không hợp lệ trả `403`; Redis lỗi trả `503`.

Task có `HttpBoardStateAdapter` gọi `GET /internal/boards/{boardId}` với `X-Service-Token`, và WebSocket route đã dùng upstream `ws://`. Tuy nhiên bốn readiness flags của Task mặc định `false`; direct-service ingress cũng chưa bị chặn. Vì vậy source đã nối các thành phần nhưng runtime #24 chưa được nghiệm thu end-to-end.

Target #24 đã duyệt: fanout best-effort theo Board trong cùng Task instance, chỉ sau commit; message không bền vững, không retry/replay và không bảo đảm thứ tự/nhận đủ. Client gọi lại REST sau reconnect.

Account phải ở trạng thái nghiệp vụ ACTIVE để được xác thực/cấp quyền. User-service snapshot-sync worker mặc định tắt (`USER_AUTH_SNAPSHOT_SYNC_ENABLED=false`); khi snapshot Redis chưa tồn tại hoặc không xác minh được, Gateway fail-closed với `503`. Kỳ 1 không có Account deletion endpoint/status hoặc cascade route. System Admin chỉ quản trị Account, không mặc định có quyền Project.

Gateway chỉ bảo vệ request đi qua Gateway. Direct-service ingress chưa bị chặn theo phạm vi hiện tại; không bật Task trusted-identity gate khi client vẫn có thể gọi trực tiếp service. CORS chưa được cấu hình. Public key cache không tự refresh khi user-service đổi key; cần restart Gateway cho tới khi có rotation policy.

## Route priority mục tiêu

Khi bổ sung route:

1. /api/v1/projects/*/boards/*/cards/** và /api/v1/projects/*/cards/** → task-service.
2. /api/v1/projects/** và /api/v1/boards/** → board-service.
3. /api/v1/admin/**, /api/v1/users/**, /api/v1/auth/** → user-service.
4. /api/v1/notifications/** → notification-service.

Các route HTTP và WebSocket trong bảng đã hiện diện trong YAML; ticket endpoint là controller nội bộ của Gateway. `GatewayApplicationTest` có test source cho authentication, header overwrite, Redis fail-closed, ticket one-time và WebSocket proxy bằng upstream giả; đây không phải kiểm tra E2E với user-service/Redis/RabbitMQ thật hoặc direct-service ingress. Khi upstream lỗi hoặc route không match, không suy diễn response nghiệp vụ từ contract của service.

## Dependency

- Spring Cloud Gateway Server WebFlux; không dùng Servlet MVC.
- Gateway không có datasource, repository hoặc migration.
- Actuator có trong pom.xml; exposure/access policy chưa cấu hình tại routing YAML.
- Xem [Kanban API](../../docs/api/kanban-api.md) và [architecture decisions](../../docs/project/architecture-decisions.md).
