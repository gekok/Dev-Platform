# Issue 05 — [gateway] Route + Docker + Tích hợp

Service: `gateway`
Cổng: `8080`
Kho: không (chỉ route, xem `gateway/src/main/resources/application.yml`)
Hợp đồng: `docs/api/kanban-api.md` base `http://localhost:8080/api/v1` + mục 5 WS

Người nhận: Người 5 (gateway + tích hợp)
Nhãn: `service/gateway`

## Files sở hữu

```
gateway/src/main/java/dev/platform/gateway/GatewayApplication.java
gateway/src/main/resources/application.yml
docker-compose.yml (4 DB 5432 → 5435)
pom.xml (parent Java 21, Boot 4.1.1)
README.md (mục 4 chạy dự án)
```

## Việc làm từ đầu đến cuối

- [ ] Giữ thứ tự route: `task-service` (`/cards/**`, `/boards/**/cards/**`) trước `board-service` (`/boards/**`)
- [ ] Giữ route WS `/ws/**` → `8083`, không để rơi sang notify `8084`
- [ ] Giữ route user `/users/**`, `/auth/**` → `8081`, notify `/notifications/**` → `8084`
- [ ] Chuẩn hóa JWT ở gateway (parse `Authorization`, forward `sub`, TODO hiện tại là stub)
- [ ] Dựng `docker compose up -d` 4 DB chạy ổn (5432 user, 5433 board, 5434 task, 5435 notify)
- [ ] Viết checklist tích hợp cho Issue 06 (thứ tự bật service, curl mẫu)
- [ ] Khóa tiền tố `/api/v1` để mobile cũ còn chạy khi lên `v2`

## Định nghĩa xong

- `mvn -pl gateway compile` xanh
- Route qua gateway trả đúng 4 service + WS
- `docker compose ps` 4 DB `healthy` hoặc `running`
- Có file chạy mẫu từ gateway tới từng service

## Bàn giao tích hợp

- Là đầu mối Issue 06, gom kết quả Người 1 → 4
- Không đổi path public `/api/v1/**`, `/ws/**`
- Mọi đổi route phải báo cả 4 người trước
