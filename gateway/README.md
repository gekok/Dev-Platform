# API Gateway

Gateway là cổng HTTP hiện tại của Dev-Platform, chạy Spring Cloud Gateway Server WebFlux trên port `8080`. Nó định tuyến request đến bốn service theo route table trong [`docs/routing.md`](docs/routing.md).

## Trách nhiệm và ranh giới

- Proxy các route được khai báo; route cụ thể của Card được đặt trước route tổng quát của Board.
- Không sở hữu domain data, JPA entity hay database.
- Route config hiện không khai báo JWT filter, authorization filter, header sanitization, rate limit hoặc retry policy. Vì vậy không coi Gateway hiện tại là điểm xác thực/ủy quyền đã hoàn thiện.
- Project-first target (`/api/v1/projects/**`), Invitation, Admin Account routes và `/internal/**` chưa được khai báo trong route table.
- WebSocket route `/ws/**` đã được khai báo tới task-service, nhưng task-service chưa đăng ký WebSocket handler; route declaration không chứng minh realtime đang hoạt động.

## Chạy local

Yêu cầu Java 21 và Maven. Từ root repo:

```bash
mvn -pl gateway spring-boot:run
```

Gateway nghe tại `http://localhost:8080`. Bản thân Gateway không cần DB; request chỉ thành công khi upstream service tương ứng đang chạy ở port được cấu hình. Các upstream hiện dùng `localhost` hard-coded trong `application.yml`.

`gateway/pom.xml` dùng Spring Cloud Gateway Server WebFlux và Actuator. Cấu hình route nằm tại [`src/main/resources/application.yml`](src/main/resources/application.yml); Gateway không có API/domain controller riêng.

## Tài liệu liên quan

- [Route matrix và giới hạn hiện tại](docs/routing.md)
- [API mục tiêu Project-first](../docs/api/kanban-api.md)
- [Quyết định kiến trúc và auth boundary](../docs/project/architecture-decisions.md)
