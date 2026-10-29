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
├── docker-compose.yml             # 4 PostgreSQL + RabbitMQ cho môi trường local
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

Mỗi service giữ database riêng. `Project`, `Membership`, `Board` và `Column` dùng chung `board_db`; các ID được tham chiếu giữa service dưới dạng giá trị, không tạo JPA foreign key xuyên database. RabbitMQ có trong Compose; messaging và Outbox Relay không tự bật theo cấu hình mặc định.

## 4. Quy ước tài liệu và triển khai

1. Đọc [chỉ mục tài liệu](docs/README.md) để phân biệt quyết định mục tiêu, API mục tiêu, tài liệu runtime cũ và mock demo.
2. `docs/project/architecture-decisions.md` là nguồn quyết định backend đã thống nhất; mục “Cần bàn tiếp” chưa phải hợp đồng.
3. `docs/api/kanban-api.md` là bản thiết kế HTTP Project-first theo service; nội dung đánh dấu đề xuất/còn mở chưa phải OpenAPI đã duyệt. API Board-first cũ chỉ để tham khảo runtime.
4. Task sheets 01–06 và `services/*/docs` phân biệt runtime hiện tại với ownership, API và logical database schema mục tiêu của từng service. Schema mục tiêu chưa phải migration/entity đã triển khai; `shared/README.md` và `gateway/README.md` mô tả hai module nền.

## 5. Mục tiêu và hiện trạng runtime

**Mục tiêu kiến trúc:** Gateway xác thực và định tuyến; `board-service` là nguồn chuẩn về Project/Membership; các service còn lại sở hữu dữ liệu riêng theo ranh giới trên.

**Runtime hiện tại:** `board-service` vẫn có route Board-first `/api/v1/boards` và stub `X-User-Id`; Project/Membership chưa có entity/adapter thật. Auth cũng chưa hoàn chỉnh: `GET /users/me` còn dùng `X-User-Id`, `LoginUserPort` chưa có implementation và Gateway chưa xác thực JWT. Kỳ 1 đang bổ sung skeleton Redis snapshot và assignment cleanup; những integration chưa nối end-to-end được ghi rõ trong tài liệu. Account deletion/cascade không thuộc runtime Kỳ 1.

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
