# Dev-Platform

Nền tảng quản lý công việc theo Project, phát triển theo nhiều kỳ. Kỳ 1 cung cấp workspace Kanban cho web và mobile.

> **Trạng thái quan trọng:** Thiết kế backend Kỳ 1 là Project-first. Entity Project/Membership/Invitation đã có; use case và API Project-first chưa được nối hoàn chỉnh. Xem [chỉ mục tài liệu](docs/README.md) để phân biệt thiết kế, code và runtime hiện tại.

## 1. Mô hình sản phẩm và phạm vi

Kỳ 1 lấy **Project** làm ranh giới sở hữu, membership và quyền truy cập:

```text
Project (một Owner, các Member)
└── Boards
    └── Columns
        └── Cards
            └── Comments
```

- Một Project có một Owner. Owner mời tài khoản đã có trong hệ thống; chỉ khi người nhận chấp nhận Invitation `PENDING` thì Membership mới được tạo.
- Tạo Project yêu cầu Account nghiệp vụ `ACTIVE` được Gateway xác thực; người tạo thành Owner trong cùng transaction. Quyền System Admin chỉ dành cho quản trị Account và tự nó không cấp quyền Project.
- Owner và Member cùng thao tác công việc trong Project: tạo Card/Column, chuyển trạng thái Card và comment. Owner có thêm quyền tạo Board, quản lý trạng thái Project/Board và thành viên.
- Kỳ 1 giữ Gateway và bốn service: `user-service`, `board-service`, `task-service`, `notification-service`. Project, Membership, Board và Column thuộc `board-service` và `board_db`; không có deployable `project-service` riêng.
- Kỳ 1 chưa triển khai xóa Account hoặc cascade Project. Hướng xử lý soft delete và chuyển Owner sang Member đủ điều kiện tham gia sớm nhất được giữ cho Kỳ 2; nếu không có Member kế nhiệm thì chặn xóa.

Các quyết định đầy đủ và phần còn mở nằm trong [architecture-decisions.md](docs/project/architecture-decisions.md).

## 2. Kỳ 2

Kỳ 2 dự kiến mở rộng năng lực Git core và Workflow. Ranh giới service, dữ liệu, API và cách tích hợp cụ thể sẽ được chốt khi bắt đầu thiết kế Kỳ 2. Không dùng các mô tả cũ `Board → Issues` hoặc `Task → PR` làm quyết định kiến trúc hiện hành.

## 3. Cấu trúc repo

```text
Dev-Platform/
├── pom.xml
├── docker-compose.yml             # RabbitMQ và Redis cho môi trường local
├── gateway/                       # cổng vào HTTP; docs nội bộ ở gateway/README.md
├── services/
│   ├── user-service/              # tài khoản và xác thực
│   ├── board-service/             # mục tiêu: Project, Membership, Board, Column
│   ├── task-service/              # Card, Comment, realtime
│   └── notification-service/      # Notification
├── shared/                        # README.md giải thích library, Outbox/Inbox và cách dùng
│   ├── event-contracts/           # envelope và DTO event dùng chung
│   └── messaging-support/         # nền Outbox/Inbox và Outbox Relay
├── web-demo/                      # React demo dùng MSW, không phải backend thật
└── docs/
    ├── README.md                  # thứ tự đọc và trạng thái tài liệu
    ├── api/kanban-api.md          # API mục tiêu và tham chiếu API cũ
    └── project/                   # quyết định, hướng dẫn và checklist tích hợp
```

Mỗi service giữ database riêng. `Project`, `Membership`, `Board` và `Column` dùng chung `board_db`; các ID được tham chiếu giữa service dưới dạng giá trị, không tạo JPA foreign key xuyên database. RabbitMQ và Redis có trong Compose; messaging và Outbox Relay không tự bật theo cấu hình mặc định.

## 4. Quy ước tài liệu và triển khai

1. Đọc [chỉ mục tài liệu](docs/README.md) để phân biệt quyết định mục tiêu, API mục tiêu, tài liệu runtime cũ và mock demo.
2. `docs/project/architecture-decisions.md` là nguồn quyết định backend đã thống nhất; mục “Cần bàn tiếp” chưa phải hợp đồng.
3. `docs/api/kanban-api.md` là bản thiết kế HTTP Project-first theo service; nội dung đánh dấu đề xuất/còn mở chưa phải OpenAPI đã duyệt. API Board-first cũ chỉ để tham khảo runtime.
4. Task sheets 01–06 và `services/*/docs` phân biệt runtime hiện tại với ownership, API và database schema của từng service. Entity và script khởi tạo local không đồng nghĩa use case đã hoàn chỉnh; `shared/README.md` và `gateway/README.md` mô tả hai module nền.

## 5. Mục tiêu và hiện trạng runtime

**Mục tiêu kiến trúc:** Gateway xác thực và định tuyến; `board-service` là nguồn chuẩn về Project/Membership; các service còn lại sở hữu dữ liệu riêng theo ranh giới trên.

**Runtime hiện tại:** `board-service` vẫn khai báo route Board-first `/api/v1/boards` và còn controller dùng `X-User-Id`. Project/Membership/Invitation đã có entity, nhưng use case và API Project-first chưa nối hoàn chỉnh; adapter Board-first từ chối `save` và `findByKey` vì thiếu Project context. `user-service` có login/current-account, directory/profile và System Admin create/status/role handlers. Gateway route `/api/v1/auth/**`, `/api/v1/users/**` và `/api/v1/admin/**` tới user-service; xác thực JWT RS256/issuer/expiry/subject, đọc Account snapshot Redis và ghi đè `X-User-Id`. Public chỉ có `POST /api/v1/auth/login`, `GET /api/v1/auth/public-key` và `GET /actuator/health`. User-service ghi Outbox và snapshot-sync job cùng transaction với Account mutation, nhưng worker mặc định tắt nên request bảo vệ trả `503` nếu Redis chưa có snapshot. Direct-service ingress vẫn mở, vì vậy gọi thẳng service có thể bỏ qua Gateway; đây là điều kiện triển khai thật. Account deletion/cascade không thuộc runtime Kỳ 1.

Web demo trong `web-demo/` dùng HTTP API được MSW giả lập. Demo là tài liệu tham khảo cho giao diện và một phần route/payload; không đồng nghĩa backend hiện tại đã cung cấp các API đó.

## 6. Chạy môi trường local

### SQL Server riêng cho user-service

`.env.example` đặt `DB_USERNAME=sa`, `USER_DB_URL=jdbc:sqlserver://localhost:11433;...`; hãy đặt mật khẩu mạnh cho `DB_PASSWORD` trong `.env` ở root. Chỉ khởi động SQL cho user-service bằng `docker compose up -d user-sqlserver`; container chỉ bind `localhost:11433` và không kéo theo RabbitMQ/Redis. Chờ container healthy rồi nạp schema vào database mới/trống; `sqlcmd` sẽ hỏi mật khẩu:

```sh
docker compose exec user-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -i /tmp/user-service-local-bootstrap.sql
```

`sql/user-service-local-bootstrap.sql` chỉ tạo `kanban_user_db` và tài khoản demo local `admin@demo.local` (`DemoPass123!`). Không chạy lại nếu database đã có bảng. Sau đó chạy `mvn spring-boot:run` từ `services/user-service`.

Yêu cầu: Java 21, Maven, SQL Server local hoặc SQL Server container dành cho user-service, và Docker Compose cho hạ tầng local. Các service còn lại có thể tiếp tục dùng instance `localhost\PHUC` bằng SQL Server Authentication. Script khởi tạo toàn dự án tạo bốn database Kanban nếu chưa tồn tại; tài khoản chạy script cần quyền tạo database và bảng. Cấu hình này chỉ dành cho local: `trustServerCertificate=true` chấp nhận chứng chỉ server không được xác minh.

Sao chép `.env.example` thành `.env` ở thư mục gốc và điền `DB_USERNAME`, `DB_PASSWORD` của SQL Server local. Bốn service tự đọc file khi chạy từ thư mục gốc hoặc từ `services/<service>`; `.env` được Git bỏ qua. File dùng cú pháp Java properties `KEY=value`, không dùng `export` hay đặt giá trị trong dấu nháy; ký tự đặc biệt như backslash phải được escape theo cú pháp properties. Biến môi trường của hệ điều hành vẫn được ưu tiên. Có thể override URL bằng `USER_DB_URL`, `BOARD_DB_URL`, `TASK_DB_URL`, `NOTIFICATION_DB_URL` tương ứng mỗi service. Schema tự động mặc định tắt (`DB_DDL_AUTO=none`). Với bốn database local hoàn toàn trống, chạy một lần [script khởi tạo và dữ liệu mẫu](sql/local-sqlserver-bootstrap.sql) bằng `sqlcmd -b` hoặc SSMS thông thường. Kiểm tra trước cả bốn database không có bảng; với SSMS, lỗi trong một batch có thể không dừng các batch tiếp theo, vì vậy không chạy script trên database đã có dữ liệu; không chạy file SQL mẫu bên ngoài repo vì chưa khớp mapping. Kỳ 1 chưa dùng migration.

Ví dụ chạy script bằng PowerShell: `sqlcmd -S 'localhost\PHUC' -U sa -C -b -f 65001 -i sql/local-sqlserver-bootstrap.sql`, rồi nhập mật khẩu khi được hỏi; đổi instance và tên đăng nhập theo máy. Spring đọc `.env` cho service, nhưng file này không tự đặt biến môi trường của terminal cho `sqlcmd`. Script chỉ dành cho local, tạo bảng vận hành rỗng và chứa dữ liệu Account demo; đăng nhập chưa nối end-to-end.

Nếu `kanban_user_db` đã được tạo từ bootstrap cũ, chạy `sql/upgrade-user-email-constraint.sql` bằng `sqlcmd -b` hoặc SSMS để đặt tên constraint email là `uq_users_email`; script giữ nguyên dữ liệu và không chạy lại bootstrap trên database đang có bảng.

Các đường dẫn `.env` được tìm theo thư mục làm việc: `../../.env` và `.env` ở thư mục hiện tại, trong đó `.env` hiện tại được ưu tiên. Khi chạy từ thư mục service, tránh tạo thêm `.env` trong service nếu muốn dùng cấu hình chung; khi chạy từ root, `../../.env` có thể trỏ ra ngoài repo. Chỉ chạy từ thư mục làm việc tin cậy.

Chạy một lần từ thư mục gốc để build dự án và cài các module `shared/event-contracts`, `shared/messaging-support` vào Maven local, giúp từng service tìm được dependency:

```powershell
cd C:\full\MSS301\project\Dev-Platform
mvn -DskipTests install
```

Chạy `docker compose up -d` tại thư mục gốc để khởi động RabbitMQ (`localhost:5672`, giao diện quản trị `localhost:15672`) và Redis (`localhost:6379`). SQL Server chạy riêng trên máy. Kiểm tra bằng `docker compose ps` và `docker compose exec redis redis-cli ping` (mong đợi `PONG`); dừng container bằng `docker compose stop`, chạy lại bằng `docker compose start`. Nếu cổng đã bị chiếm, giải phóng cổng hoặc thay đổi cấu hình tương ứng. Redis chưa có volume riêng: dữ liệu snapshot có thể mất khi container bị xóa, và cơ chế rebuild snapshot chưa hoàn thiện; không coi cấu hình này là lưu trữ production. RabbitMQ cũng chưa có volume được khai báo. Container chạy không chứng minh luồng event hay snapshot đã nối end-to-end.

Mở một terminal PowerShell riêng cho mỗi service. Đổi đường dẫn nếu dự án nằm ở vị trí khác:

**Terminal 1 — user-service (8081)**

```powershell
cd C:\full\MSS301\project\Dev-Platform\services\user-service
mvn spring-boot:run
```

**Terminal 2 — board-service (8082)**

```powershell
cd C:\full\MSS301\project\Dev-Platform\services\board-service
mvn spring-boot:run
```

**Terminal 3 — task-service (8083)**

```powershell
cd C:\full\MSS301\project\Dev-Platform\services\task-service
mvn spring-boot:run
```

**Terminal 4 — notification-service (8084)**

```powershell
cd C:\full\MSS301\project\Dev-Platform\services\notification-service
mvn spring-boot:run
```

Nhấn `Ctrl+C` trong terminal để dừng service. Gateway có thể được chạy riêng từ thư mục `gateway` bằng `mvn spring-boot:run`.

**Giới hạn hiện tại:** Một số use case nghiệp vụ và API Project-first vẫn là skeleton hoặc chưa nối giữa các service. Service khởi động thành công không đồng nghĩa các luồng nghiệp vụ đã chạy end-to-end.

Gateway dự kiến là cổng vào `http://localhost:8080/api/v1`. Tình trạng API và xác thực hiện tại được phân biệt trong [API contract](docs/api/kanban-api.md); không suy ra rằng các route Project-first đã được triển khai chỉ từ việc service khởi động được.
