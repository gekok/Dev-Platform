# API Gateway

Gateway là cổng HTTP của Dev-Platform, dùng Spring Cloud Gateway Server WebFlux trên port 8080. Route đang khai báo được ghi ở [routing reference](docs/routing.md).

## Trách nhiệm và ranh giới

- Proxy request theo route; Card path cụ thể của task-service đứng trước Project prefix rộng của board-service.
- Không sở hữu domain data, JPA entity hay database.
- Gateway hiện xác thực JWT trên các request bảo vệ: RS256, issuer, hạn dùng, `iat` hiện diện và UUID subject. Gateway lấy public key từ user-service, cache key thành công tới khi restart.
- Sau JWT, Gateway đọc Account snapshot `auth:account:{accountId}` từ Redis. Snapshot phải đúng Account, có `authVersion` hợp lệ và `status=ACTIVE`; thiếu/lỗi snapshot trả `503`. Gateway không sở hữu/cache snapshot và không chuyển Project role.
- Gateway gỡ `X-User-Id` do client gửi rồi đặt lại từ JWT `sub`. Bearer Authorization được giữ nguyên để user-service tiếp tục xác thực `/users/me`; `email` vẫn nằm trong payload JWT được forward, nhưng Gateway không tạo profile header riêng. JWT được ký, không mã hóa.
- Token được phát trước khi `iss` được thêm vào JWT sẽ bị từ chối bởi issuer validation; đăng nhập lại để lấy token mới.
- `401`: token thiếu/sai; `403`: Account snapshot không ACTIVE; `503`: public key hoặc snapshot không thể xác minh. Public routes là `POST /api/v1/auth/login`, `GET /api/v1/auth/public-key` và `GET /actuator/health`; các route khác yêu cầu token.
- Account create/status/role mutation của user-service ghi Outbox và snapshot-sync job cùng transaction; worker mặc định tắt (`USER_AUTH_SNAPSHOT_SYNC_ENABLED=false`). Vì vậy protected request fail-closed bằng `503` cho tới khi snapshot được apply vào Redis. Gateway JWT code không đồng nghĩa Auth đã tích hợp end-to-end.
- Các service vẫn có thể bị gọi trực tiếp qua port hiện tại, bỏ qua Gateway filter; chặn network ingress là điều kiện triển khai thật và chưa thuộc thay đổi này. HTTP CORS allowlist chưa cấu hình; WebSocket ticket handshake dùng allowlist Origin riêng.
- System Admin quản trị Account nhưng không tự có quyền Project. Kỳ 1 không có Account deletion endpoint/status hoặc Project cascade.
- Project/Membership/Invitation routes hiện được route tới board-service nhưng controller target chưa có. `/api/v1/admin/**` được route tới user-service; `AdminUserController` tự kiểm tra role `SYSTEM_ADMIN`. Internal route chưa được cấu hình.
- `POST /api/v1/realtime/boards/{boardId}/ws-ticket` yêu cầu Bearer token đã xác thực và cấp ticket gắn với Account/Board, dùng một lần, TTL mặc định 30 giây. Browser dùng ticket tại `WS /ws/boards/{boardId}?ticket=...`; Gateway kiểm tra `Origin`, bỏ ticket/credential client gửi và chuyển `X-User-Id` đã xác minh. Query ticket có thể lọt vào access log ở ingress/reverse proxy; cấu hình logging cần che giá trị `ticket`.
- `/ws/**` dùng `ws://localhost:8083` làm upstream và có integration test với upstream giả. Task WebSocket handler và secured Board lookup adapter đã có source nhưng 4 readiness flags mặc định tắt; chưa có E2E với Task/Board/Redis thật. Direct-service ingress vẫn chưa bị chặn và phải được bảo vệ trước khi bật Task trusted-identity gate.

## Chạy local

Yêu cầu Java 21 và Maven. Từ root repo:

    mvn -pl gateway spring-boot:run

Gateway nghe tại http://localhost:8080. Request chỉ thành công nếu upstream tương ứng chạy. Upstream mặc định là localhost các cổng 8081–8084; Task WebSocket dùng riêng `GATEWAY_UPSTREAM_TASK_WEBSOCKET` (mặc định `ws://localhost:8083`). Có thể cấu hình user upstream bằng `GATEWAY_UPSTREAM_USER` trong `.env`; Redis dùng `REDIS_HOST` và `REDIS_PORT`; Gateway và user-service phải dùng chung `USER_JWT_ISSUER` (mặc định `dev-platform-user-service`). Web origin dùng cho ticket cấu hình bằng `GATEWAY_WS_ALLOWED_ORIGINS`, mặc định `http://localhost:5173`; TTL ticket cấu hình qua `GATEWAY_WS_TICKET_TTL` và phải ít nhất 1 giây.

Chạy `mvn -pl gateway test` từ root để kiểm tra public routes, JWT, Redis snapshot guard, header spoofing, cấp/tiêu thụ ticket và WebSocket proxy với upstream giả. Test không chứng minh Redis thật, Task/Board service hay direct-service ingress đã tích hợp.

gateway/pom.xml dùng Spring Cloud Gateway Server WebFlux và Actuator. Gateway không sở hữu API nghiệp vụ; controller ticket WebSocket là endpoint kỹ thuật cục bộ của Gateway.

## Tài liệu liên quan

- [Route matrix và giới hạn hiện tại](docs/routing.md)
- [API mục tiêu Project-first](../docs/api/kanban-api.md)
- [Quyết định kiến trúc và authorization boundary](../docs/project/architecture-decisions.md)
