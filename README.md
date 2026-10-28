# Dev-Platform

Nền tảng quản lý công việc theo Project, phát triển theo nhiều kỳ. Kỳ 1 cung cấp workspace Kanban cho web và mobile.

> **Trạng thái quan trọng:** Thiết kế backend mục tiêu hiện là Project-first. Backend trong repo vẫn chủ yếu theo API Board-first; Project/Membership chưa được triển khai. Xem [chỉ mục tài liệu](docs/README.md) để biết tài liệu nào mô tả quyết định mới, API mục tiêu và runtime hiện tại.

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
- Owner và Member cùng thao tác công việc trong Project: tạo Card/Column, chuyển trạng thái Card và comment. Owner có thêm quyền tạo Board, quản lý trạng thái Project/Board và thành viên.
- Kỳ 1 giữ Gateway và bốn service: `user-service`, `board-service`, `task-service`, `notification-service`. Project, Membership, Board và Column thuộc `board-service` và `board_db`; không có deployable `project-service` riêng.
- Account deletion là quy trình bất đồng bộ có operation tracking, Outbox/Inbox và xử lý theo service. Scaffold hiện có chưa phải luồng chạy end-to-end; xem [hướng dẫn skeleton](docs/project/account-deletion-skeleton-guide.md).

Các quyết định đầy đủ và phần còn mở nằm trong [architecture-decisions.md](docs/project/architecture-decisions.md).

## 2. Kỳ 2

Kỳ 2 dự kiến mở rộng năng lực Git core và Workflow. Ranh giới service, dữ liệu, API và cách tích hợp cụ thể sẽ được chốt khi bắt đầu thiết kế Kỳ 2. Không dùng các mô tả cũ `Board → Issues` hoặc `Task → PR` làm quyết định kiến trúc hiện hành.

## 3. Cấu trúc repo

```text
Dev-Platform/
├── pom.xml
├── docker-compose.yml             # 4 PostgreSQL + RabbitMQ cho môi trường local
├── gateway/                       # cổng vào HTTP
├── services/
│   ├── user-service/              # tài khoản và xác thực
│   ├── board-service/             # mục tiêu: Project, Membership, Board, Column
│   ├── task-service/              # Card, Comment, realtime
│   └── notification-service/      # Notification
├── shared/
│   ├── event-contracts/           # envelope và DTO event dùng chung
│   └── messaging-support/         # nền Outbox/Inbox và Outbox Relay
├── web-demo/                      # React demo dùng MSW, không phải backend thật
└── docs/
    ├── README.md                  # thứ tự đọc và trạng thái tài liệu
    ├── api/kanban-api.md          # API mục tiêu và tham chiếu API cũ
    └── project/                   # quyết định, hướng dẫn và checklist tích hợp
```

Mỗi service giữ database riêng. `Project`, `Membership`, `Board` và `Column` dùng chung `board_db`; các ID được tham chiếu giữa service dưới dạng giá trị, không tạo JPA foreign key xuyên database. RabbitMQ có trong Compose; messaging và Outbox Relay không tự bật theo cấu hình mặc định.

## 4. Quy ước tài liệu và triển khai

1. Đọc [chỉ mục tài liệu](docs/README.md) để phân biệt quyết định mục tiêu, API mục tiêu, tài liệu runtime cũ và mock demo.
2. `docs/project/architecture-decisions.md` là nguồn quyết định backend đã thống nhất; mục “Cần bàn tiếp” chưa phải hợp đồng.
3. `docs/api/kanban-api.md` ghi route Project-first ở mức đã thống nhất và giữ API Board-first cũ trong phần tham khảo. Chưa implement API mới từ các payload hoặc hành vi đang đánh dấu mở.
4. Task sheets 01–05 và tài liệu riêng trong `services/*/docs` đã được đồng bộ theo Project-first, đồng thời vẫn mô tả rõ code/schema runtime hiện tại. Các payload/error/operation contract còn mở phải được chốt trước khi dùng tài liệu làm tiêu chí implement.

## 5. Mục tiêu và hiện trạng runtime

**Mục tiêu kiến trúc:** Gateway xác thực và định tuyến; `board-service` là nguồn chuẩn về Project/Membership; các service còn lại sở hữu dữ liệu riêng theo ranh giới trên.

**Runtime hiện tại:** `board-service` vẫn có route Board-first `/api/v1/boards` và stub `X-User-Id`; Project/Membership chưa có entity/adapter thật. Auth cũng chưa hoàn chỉnh: `GET /users/me` còn dùng `X-User-Id`, `LoginUserPort` chưa có implementation và Gateway chưa xác thực JWT. Skeleton xóa Account mới có các biên persistence/use-case và messaging dùng chung, chưa có API Admin hay Rabbit listener/worker end-to-end. Vì vậy README và API mục tiêu không khẳng định backend đã đạt kiến trúc đích.

Web demo trong `web-demo/` dùng HTTP API được MSW giả lập. Demo là tài liệu tham khảo cho giao diện và một phần route/payload; không đồng nghĩa backend hiện tại đã cung cấp các API đó.

## 6. Chạy môi trường local

Yêu cầu: Java 21, Maven và Docker.

```bash
# Dựng database và RabbitMQ cho các service
docker compose up -d

# Chạy từng service (mỗi lệnh trong một terminal)
cd services/user-service && mvn spring-boot:run
cd services/board-service && mvn spring-boot:run
cd services/task-service && mvn spring-boot:run
cd services/notification-service && mvn spring-boot:run

# Chạy Gateway
cd gateway && mvn spring-boot:run
```

Gateway dự kiến là cổng vào `http://localhost:8080/api/v1`. Tình trạng API và xác thực hiện tại được phân biệt trong [API contract](docs/api/kanban-api.md); không suy ra rằng các route Project-first đã được triển khai chỉ từ việc service khởi động được.
