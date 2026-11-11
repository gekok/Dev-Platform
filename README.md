# Dev-Platform

Nền tảng quản lý công việc theo Project, với bảng Kanban, thành viên, Card, Comment và Notification. Backend gồm API Gateway và bốn service; giao diện web có cả chế độ demo độc lập và chế độ kết nối backend.

## Mục lục

- [Bắt đầu chạy local](#bắt-đầu-chạy-local)
- [Kiến trúc và cấu trúc repo](#kiến-trúc-và-cấu-trúc-repo)
- [Phạm vi và giới hạn](#phạm-vi-và-giới-hạn)
- [Kiểm tra và xử lý lỗi](#kiểm-tra-và-xử-lý-lỗi)
- [Tài liệu và đóng góp](#tài-liệu-và-đóng-góp)

## Bắt đầu chạy local

### 1. Chuẩn bị

- Java 21 và Maven.
- SQL Server và SSMS hoặc `sqlcmd` để khởi tạo database.
- Docker Compose để chạy RabbitMQ và Redis.
- Node.js 18+ và npm nếu chạy web.

Các lệnh dưới đây bắt đầu từ thư mục gốc **Dev-Platform**. Không cần dùng đúng đường dẫn trên máy người khác.

### 2. Tạo cấu hình dùng chung

```powershell
Copy-Item .env.example .env
```

Chỉ chạy lệnh copy lần đầu; nếu đã có `.env`, sửa file hiện có để giữ cấu hình máy. `.env` không được commit.

| Biến | Cần cấu hình |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | SQL Server Authentication của máy chạy DB |
| `USER_DB_URL`, `BOARD_DB_URL`, `TASK_DB_URL`, `NOTIFICATION_DB_URL` | JDBC URL tới bốn database tương ứng |
| `INTERNAL_SERVICE_TOKEN` | Giá trị không rỗng, dùng chung cho các service gọi API nội bộ |
| `GATEWAY_HMAC_SECRET` | Giá trị Board yêu cầu khi khởi động; ký và kiểm tra HMAC chưa hoạt động trong runtime hiện tại |
| `REDIS_HOST`, `REDIS_PORT` | Mặc định `localhost:6379` |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | Mặc định `localhost:5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | Mặc định phát triển `devplatform` / `devplatform` |

**Chú ý URL User DB:** file mẫu trỏ tới SQL Server local instance `PHUC`. Hãy đổi instance trong cả bốn URL cho phù hợp với máy của anh. Ví dụ cho User DB:

```properties
USER_DB_URL=jdbc:sqlserver://localhost;instanceName=PHUC;databaseName=kanban_user_db;encrypt=true;trustServerCertificate=true
```

Ba DB còn lại có tên `kanban_board_db`, `kanban_task_db`, `kanban_notification_db`. Cùng các biến `DB_USERNAME`/`DB_PASSWORD` được dùng bởi bốn service.

Các cờ tích hợp mặc định tắt trong service. [`.env.example`](.env.example) đã gom cấu hình bật cho bộ local đầy đủ, có chú thích kiểu dữ liệu và ví dụ trên dòng trước mỗi nhóm biến. Điền các giá trị còn trống; nếu đang dùng `.env` cũ, bổ sung các cờ sau để chạy tích hợp RabbitMQ, snapshot Redis và Task trên máy local:

```properties
MESSAGING_ENABLED=true
OUTBOX_RELAY_ENABLED=true
MESSAGING_CONSUMERS_ENABLED=true
USER_AUTH_SNAPSHOT_SYNC_ENABLED=true
PROJECT_AUTH_SNAPSHOT_SYNC_ENABLED=true
TASK_TRUSTED_GATEWAY_IDENTITY_ENABLED=true
TASK_BOARD_PROJECT_LOOKUP_READY=true
TASK_GATEWAY_WEBSOCKET_PROXY_READY=true
TASK_BOARD_WEBSOCKET_ENABLED=true
MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY=true
MEMBER_ASSIGNMENT_CLEANUP_ENABLED=true
server.address=127.0.0.1
```

Các cờ trên bật thành phần tích hợp, không thay thế việc kiểm tra luồng thực tế. Giữ service trên loopback khi dùng chế độ tin identity từ Gateway ở local.

Bốn service đọc `../../.env` khi chạy từ `services/<service>`; Gateway đọc `../.env` khi chạy từ `gateway`. File `.env` ngay trong thư mục chạy có thể ghi đè file chung; tránh tạo thêm nếu không cần. Biến môi trường của process ưu tiên hơn file. Dùng cú pháp `KEY=value`, không có `export` hoặc dấu nháy bao ngoài; backslash phải escape theo Java properties. Compose cũng đọc file này nên tránh giá trị có quy tắc escape khác nhau giữa hai trình đọc.

### 3. Khởi tạo database một lần

**Cách chính — bốn DB trên SQL Server local:** dùng [sql/local-sqlserver-bootstrap.sql](sql/local-sqlserver-bootstrap.sql). Script tạo DB nếu thiếu, tạo schema và dữ liệu mẫu; Kỳ 1 chưa bắt buộc migration. `DB_DDL_AUTO` mặc định là `none`.

> Chỉ chạy script trên bốn database trống. Máy đã có schema không cần nạp lại; script không phải công cụ nâng cấp database.

```powershell
sqlcmd -S 'localhost\PHUC' -U sa -C -b -f 65001 -i sql/local-sqlserver-bootstrap.sql
```

Đổi server/user theo máy; `sqlcmd` hỏi mật khẩu, không tự đọc `.env`. Chỉ chạy trên **bốn database trống**, bằng tài khoản có quyền tạo DB/bảng. Có thể mở script trong SSMS; dừng ngay khi có lỗi vì SSMS có thể tiếp tục batch sau. Không chạy lại bootstrap để nâng cấp DB đã có dữ liệu.

Dữ liệu mẫu của script toàn hệ thống:

| Account | Mục đích |
|---|---|
| `admin@demo.local` | Quản trị Account |
| `owner@demo.local` | Owner Project mẫu |
| `member@demo.local` | Member Project mẫu |

Mật khẩu mẫu: `DemoPass123!`, chỉ dùng local. Nếu DB cũ thiếu tên constraint email chuẩn, xem [script cập nhật constraint](sql/upgrade-user-email-constraint.sql), không nạp lại bootstrap.

<details>
<summary>Lựa chọn riêng: User DB chạy trong Docker</summary>

Compose có thêm `user-sqlserver` ở cổng `11433`, với named volume `user_sqlserver_data`. Đặt `DB_PASSWORD` đáp ứng yêu cầu SQL Server và đổi `USER_DB_URL` trong `.env` sang URL Docker được ghi trong `.env.example`:

```powershell
docker compose up -d user-sqlserver
docker compose ps
```

Khi container healthy, khởi tạo User DB trống:

```powershell
docker compose exec user-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -i /tmp/user-service-local-bootstrap.sql
```

Script riêng tạo User DB, ba Account mẫu trong bảng trên và snapshot-sync jobs; không tạo Project/Card. Ba DB nghiệp vụ còn lại vẫn cần được chuẩn bị trên SQL Server local. Không chạy cả hai bootstrap vào cùng User DB đã có bảng.

</details>

### 4. Khởi động RabbitMQ và Redis

```powershell
docker compose up -d rabbitmq redis
docker compose ps
docker compose exec redis redis-cli ping
docker compose exec rabbitmq rabbitmq-diagnostics -q check_port_connectivity
```

Chờ hai container `healthy`; Redis trả `PONG`, RabbitMQ check trả exit code `0`. Đây là kiểm tra hạ tầng, chưa xác nhận publish/consume hay đồng bộ snapshot.

- RabbitMQ Management: **http://localhost:15672**, dùng credential trong `.env`.
- Redis Insight: kết nối **127.0.0.1:6379**, không username/password ở cấu hình local.
- Nếu đổi port trong `.env`, dùng port tương ứng. Các cổng Compose chỉ bind loopback.
- Đổi `RABBITMQ_USERNAME/PASSWORD` sau khi broker đã khởi tạo không tự đổi tài khoản trong broker.
- Dùng `docker compose stop` / `start` để giữ container. Redis/RabbitMQ hiện dùng anonymous volume; sau `down` rồi `up`, volume cũ không tự gắn lại. Tránh `down -v` nếu cần giữ dữ liệu; Redis mất snapshot có thể khiến request trả `503` đến khi snapshot được dựng lại.

### 5. Build và chạy backend

Chạy một lần ở root, và chạy lại sau khi thay đổi module shared:

```powershell
mvn -DskipTests install
```

Lệnh build và cài dependency shared vào Maven local; **không chạy test**. Sau đó mở **năm terminal riêng**, mỗi terminal bắt đầu tại root repo:

| Terminal | Chuyển thư mục | Lệnh chạy | Cổng |
|---|---|---|---|
| User | `cd services/user-service` | `mvn spring-boot:run` | 8081 |
| Board | `cd services/board-service` | `mvn spring-boot:run` | 8082 |
| Task | `cd services/task-service` | `mvn spring-boot:run` | 8083 |
| Notification | `cd services/notification-service` | `mvn spring-boot:run` | 8084 |
| Gateway | `cd gateway` | `mvn spring-boot:run` | 8080 |

Client gọi API qua **http://localhost:8080/api/v1**. Nhấn `Ctrl+C` trong từng terminal để dừng service. Khởi động lại process sau khi đổi `.env`.

### 6. Chạy web

```powershell
cd web-demo
npm ci
npm run dev:backend
```

Mở URL Vite in ra (thường **http://localhost:5173**). Backend mode proxy `/api/v1` tới Gateway; đăng nhập bằng Account trong DB. Muốn thử giao diện độc lập, dùng `npm run dev` để bật MSW demo với bộ Account riêng. Xem [Web README](web-demo/README.md) về hai chế độ và cấu hình proxy; không dùng credential MSW để đăng nhập backend.

## Kiến trúc và cấu trúc repo

```text
Browser → API Gateway → User / Board / Task / Notification
                           ↕ Redis snapshots / RabbitMQ events
                        SQL Server: DB riêng theo service
```

| Module | Trách nhiệm | Database |
|---|---|---|
| `gateway/` | Xác thực JWT, kiểm tra Account snapshot, định tuyến HTTP/WS | Không sở hữu DB |
| `services/user-service/` | Account, login, role hệ thống, nguồn chuẩn Authentication snapshot | `kanban_user_db` |
| `services/board-service/` | Project, Membership, Invitation, Board, Column, Authorization snapshot | `kanban_board_db` |
| `services/task-service/` | Card, assignment, Comment, realtime Board, cleanup assignment | `kanban_task_db` |
| `services/notification-service/` | Inbox theo Account và consumer tạo Notification | `kanban_notification_db` |
| `shared/` | Library event contracts và messaging support; không phải service chạy riêng | Bảng Outbox/Inbox nằm trong DB service sử dụng |
| `web-demo/` | React/Vite; MSW demo hoặc backend mode | Dữ liệu mock chỉ trong browser |
| `mobile/` | Flutter client | Xem hướng dẫn module |
| `sql/`, `docs/` | Bootstrap local, kiến trúc, API và tài liệu từng service | — |

Project là ranh giới membership và quyền truy cập: **Project → Board → Column → Card → Comment**. Project nằm trong Board Service, không có Project Service riêng. Không tạo foreign key xuyên database.

Gateway xác minh JWT và đặt `X-User-Id`; service nghiệp vụ kiểm tra quyền theo Project. User/Board là nguồn chuẩn cho snapshot Redis. RabbitMQ truyền event với Outbox/Inbox; chính sách consumer chung retry tối đa 3 lần, chờ 5 giây giữa các lần rồi chuyển DLQ. Cấu hình và cách dùng library nằm trong [Shared README](shared/README.md).

## Phạm vi và giới hạn

Source hiện có login/current Account, quản trị Account; Project/Membership/Invitation và Board/Column; Card/Comment; Notification consumer; Gateway WS ticket/proxy và Task Board WebSocket. Card create/move đã nối validator kiểm tra Board/Column qua Board Service.

- Một Project có một Owner; Invitation được chấp nhận mới tạo Membership. Role quản trị hệ thống không tự cấp quyền Project.
- Backend có WebSocket không đồng nghĩa web đã tự cập nhật: client phải kết nối, subscribe và xử lý event/reconnect. Không coi việc bật cờ WS là bằng chứng realtime end-to-end.
- Board preference mặc định trong demo chưa có backend contract được chốt.
- Xóa Account/cascade Project được hoãn sang Kỳ 2. Kỳ 2 dự kiến mở rộng Git core/Workflow; chi tiết cần quyết định riêng.
- Local startup hoặc issue `Done` không thay thế bằng chứng kiểm thử trên commit phát hành. Không suy ra mọi luồng đã E2E chỉ từ danh sách thành phần có source.
- Cấu hình local không phải cấu hình triển khai thật: cần bảo vệ ingress service, credential và kết nối trước khi đưa ra môi trường dùng chung.

## Kiểm tra và xử lý lỗi

| Hiện tượng | Kiểm tra trước |
|---|---|
| Không kết nối SQL Server | URL/instance/port, SQL Authentication, schema đã khởi tạo; `.env` có được đọc từ đúng thư mục không |
| Gateway trả `401` | Token còn hạn và issuer đúng; thử đăng nhập lại |
| Gateway trả `503` | User-service/public key, Redis và Account snapshot; worker snapshot đã bật chưa |
| Không tạo/di chuyển Card | Membership snapshot, Task identity gate, Board lookup và credential nội bộ |
| Mention không có Notification | Ba cờ messaging trên cả producer/consumer, Outbox, queue binding và DLQ |
| UI không tự cập nhật | Client có kết nối WS và xử lý event không; Task WS flags và Origin allowlist |
| Sau merge vẫn chạy controller cũ | Dừng service, chạy `mvn clean install -DskipTests` ở root rồi khởi động lại |

Các lệnh kiểm tra khi cần nghiệm thu thay đổi:

```powershell
# Root: backend
mvn test

# Trong web-demo: frontend
npm test
npm run lint
npm run build:backend
```

Đây là lệnh hướng dẫn, không phải báo cáo kết quả. Kịch bản liên service nằm trong [integration checklist](docs/project/06-integration.md).

## Tài liệu và đóng góp

| Cần đọc | Tài liệu |
|---|---|
| Quyết định đã chốt và mục còn mở | [Architecture decisions](docs/project/architecture-decisions.md) |
| Tìm tài liệu theo module | [Chỉ mục tài liệu](docs/README.md) |
| Hợp đồng HTTP mục tiêu | [Kanban API](docs/api/kanban-api.md) |
| Routing, JWT, WS tại Gateway | [Gateway README](gateway/README.md) |
| Event, Outbox/Inbox, retry/DLQ và cách dùng shared | [Shared README](shared/README.md) |
| API và schema per service | [User](services/user-service/docs/api.md) · [Board](services/board-service/docs/api.md) · [Task](services/task-service/docs/api.md) · [Notification](services/notification-service/docs/api.md); mỗi thư mục có `database.md` |
| Sơ đồ DB và communication | [Thiết kế draw.io](docs/project/dev-platform-system-design.drawio) |
| Quy trình đóng góp/nghiệm thu | [AGENTS.md](AGENTS.md) · [Issue workflow](docs/project/github-issue-workflow.md) |

Phân biệt **quyết định thiết kế**, **source đã triển khai** và **kết quả nghiệm thu runtime** khi đọc tài liệu. Các trang chi tiết có thể còn ghi trạng thái của lần cập nhật trước; đối chiếu source/config và bằng chứng PR trước khi kết luận một luồng đang hoạt động.

PR cần mô tả phạm vi, bằng chứng kiểm tra, liên kết issue và cập nhật tài liệu liên quan. Không commit `.env`, credential thật hoặc dữ liệu cá nhân.
