# Dev-Platform

Nền tảng quản lý dự án kiểu GitHub thu gọn, xây theo microservices.
Một bếp phục vụ hai phòng ăn: web React và app Flutter.

## 1. Chia 2 kỳ thế nào?

Tưởng tượng mở chợ theo quầy, không ôm cả chợ một lúc.

Kỳ 1 chỉ dựng quầy Kanban (4 service). Đủ xài như Trello thu gọn:
đăng nhập, mở bảng, kéo thẻ, bình luận, nhận thông báo.

Kỳ 2 mở rộng thành Dev Platform (70% GitHub theo luồng anh vẽ):
Issues, Git (Branch/Commit/PR/Diff/Review), Workflow (Agent/Actions).

```
Kỳ 1: User + Board + Task + Notify = Kanban chạy được
                ↓ tái dùng trực tiếp
Kỳ 2: Board → Issues, Task → PR, Notify thêm type mới
```

Ranh giới đã chốt để kỳ 2 khỏi đập:

- `Board` thành nền cho `Issues` (mỗi issue là một card đặc biệt).
- `Task` thành nền cho `Branch/Commit/PR` (mỗi PR gắn một `cardId`).
- `Notification` giữ nguyên, thêm `type` mới (như `PR_REVIEWED`).
- Gateway thêm route `/api/v1/repos/**`, `/api/v1/workflows/**`.
- Giữ tiền tố `/api/v1` để mobile cũ còn chạy khi API lên `v2`.

## 2. Cấu trúc repo

```text
Dev-Platform/
├── pom.xml                     # parent: Java 21, Boot 4.1.1, Cloud 2025.0.0
├── docker-compose.yml          # 4 DB Postgres riêng, mỗi service một kho
├── gateway/                    # cổng 8080, gom route /api/v1/**
├── services/
│   ├── user-service/           # cổng 8081, kho user_db: auth + JWT
│   ├── board-service/          # cổng 8082, kho board_db: bảng + cột
│   ├── task-service/           # cổng 8083, kho task_db: thẻ + comment + WS
│   └── notification-service/   # cổng 8084, kho notification_db
└── docs/
    └── api/
        └── kanban-api.md       # hợp đồng API kỳ 1 (API-first)
```

Mỗi service giữ kho riêng (database per service). ID liên service chỉ giữ
dạng số (`Long`/`UUID`), không tạo FK JPA chéo database. Ví dụ `Card`
giữ `boardId`, `columnId`, `assigneeId` dạng số thuần.

## 3. Ba quy ước bắt buộc

### 3.1 API-first: hợp đồng đi trước, code đi sau

File `docs/api/kanban-api.md` là thực đơn chung cho cả React và Flutter.
Muốn thêm/sửa API thì sửa `api.md` trước, review xong mới viết code.

Controller mới dựng chỉ là khung giữ chỗ (stub) trả `200`/`201` rỗng.
Ruột viết sau, nhưng đường và tên field phải khớp `api.md`.

### 3.2 DDD: nói tiếng miền, không nói tiếng bảng

Mỗi service có một thứ tiếng riêng (ubiquitous language):
`user`, `board`, `column`, `card`, `comment`, `notification`.

Cụm gốc (aggregate root): `User`, `Board`, `Card`, `Notification`.
Con sống chết theo gốc (cascade ALL + orphanRemoval): `BoardColumn`
theo `Board`, `CardComment` theo `Card`.

Luật nghiệp vụ nằm ở miền (domain), không nằm ở controller.
Ví dụ: move thẻ phải dồn `position` trong cùng transaction;
server là chuẩn cuối cùng, client chỉ vẽ lại.

### 3.3 Auth: vé JWT là chuẩn duy nhất

`user-service` cấp vé (JWT). Các service khác chỉ đọc `sub` trong vé
để biết ai đang gọi. Client không được tự gửi `userId` trong body.

Mobile gọi qua gateway giống hệt web. Không gọi thẳng service.
Không tin bất kỳ `userId` nào client gửi lên.

## 4. Chạy dự án

Yêu cầu: Java 21, Maven, Docker.

```bash
# 1. Dựng 4 DB
docker compose up -d

# 2. Chạy từng service (mỗi lệnh một terminal)
cd services/user-service && mvn spring-boot:run
cd services/board-service && mvn spring-boot:run
cd services/task-service && mvn spring-boot:run
cd services/notification-service && mvn spring-boot:run

# 3. Chạy gateway
cd gateway && mvn spring-boot:run
```

Cổng vào duy nhất: `http://localhost:8080/api/v1`.
Hợp đồng chi tiết xem `docs/api/kanban-api.md`.

WebSocket bảng Kanban: `WS /ws/boards/{boardId}` (mục 5 trong api.md).
Mobile nhận gán việc và mention qua đẩy (push FCM), không giữ socket.

## 5. Lộ trình kỳ 1

1. Chốt `kanban-api.md` (đã có khung, review và khóa field).
2. Viết ruột `user-service`: register, login cấp JWT, me.
3. Viết ruột `board-service`: bảng + cột + reorder.
4. Viết ruột `task-service`: thẻ + move + comment + WebSocket.
5. Viết ruột `notification-service`: event + list + đánh dấu đọc.
6. Web React tối thiểu: bảng, kéo thẻ, xem thông báo.
7. App Flutter tối thiểu: đăng nhập, xem bảng, nhận push.

## 6. Lộ trình kỳ 2 (chưa làm, chỉ giữ chỗ)

- Thêm `repo-service`: Branch, Commit, PR, Diff, Code Review.
- Thêm `workflow-service`: trigger, Agent, Actions.
- `Board`/`Task` tái dùng, không viết lại.
- Frontend thêm 2 màn: Git và Workflow.
