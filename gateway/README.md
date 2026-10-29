# API Gateway

Gateway là cổng HTTP của Dev-Platform, dùng Spring Cloud Gateway Server WebFlux trên port 8080. Route đang khai báo được ghi ở [routing reference](docs/routing.md).

## Trách nhiệm và ranh giới

- Proxy request theo route; Card path cụ thể của task-service đứng trước Project prefix rộng của board-service.
- Không sở hữu domain data, JPA entity hay database.
- Runtime route config hiện chưa có JWT filter, authorization filter, identity header sanitization, rate limit hoặc retry. Các service cũng còn identity stub; không coi Gateway hiện tại là trust boundary đã hoàn chỉnh.
- Target: Gateway xác thực Account, loại bỏ/ghi đè X-User-Id do client gửi và chuyển identity đáng tin cậy. Account phải được xác minh ACTIVE. Gateway không sở hữu snapshot; user-service ghi Account snapshot, board-service ghi Project/Membership snapshot, Task đọc Redis và fail closed.
- System Admin quản trị Account nhưng không tự có quyền Project. Kỳ 1 không có Account deletion endpoint/status hoặc Project cascade.
- Project/Membership/Invitation routes hiện được route tới board-service nhưng controller target chưa có. Admin Account route và internal route chưa được cấu hình.
- /ws/** được route tới task-service; task-service chưa đăng ký WebSocket handler nên route chưa chứng minh realtime hoạt động.

## Chạy local

Yêu cầu Java 21 và Maven. Từ root repo:

    mvn -pl gateway spring-boot:run

Gateway nghe tại http://localhost:8080. Request chỉ thành công nếu upstream service tương ứng chạy đúng port. Upstream hiện hard-coded trong application.yml.

gateway/pom.xml dùng Spring Cloud Gateway Server WebFlux và Actuator. Gateway không có API/domain controller riêng.

## Tài liệu liên quan

- [Route matrix và giới hạn hiện tại](docs/routing.md)
- [API mục tiêu Project-first](../docs/api/kanban-api.md)
- [Quyết định kiến trúc và authorization boundary](../docs/project/architecture-decisions.md)
