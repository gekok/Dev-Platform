# Issue 01 — [user-service] Ruột Auth + JWT

Service: `user-service`
Cổng: `8081`
Kho: `user_db` (Postgres `5432`)
Hợp đồng: `docs/api/kanban-api.md` mục 1 (1.1 register, 1.2 login, 1.3 me)
Tài liệu riêng: `services/user-service/docs/api.md`, `docs/database.md`

Người nhận: Người 1 (auth + user)
Nhãn: `service/user-service`

## Files sở hữu

```
services/user-service/src/main/java/dev/platform/user/
├── domain/aggregate/UserAggregate.java
├── application/command/RegisterCommand.java, LoginCommand.java
├── application/port/in/RegisterUserPort.java, LoginUserPort.java, GetCurrentUserPort.java
├── application/port/out/SaveUserPort.java, PasswordEncoderPort.java
├── infrastructure/adapter/SaveUserAdapter.java
├── infrastructure/persistence/entity/UserEntity.java
├── infrastructure/security/BcryptPasswordAdapter.java
└── api/UserController.java + dto + exception
```

## Việc làm từ đầu đến cuối

- [ ] Viết `@Service` cho `RegisterUserPort` (check email chứa `@`, trùng email → `409`)
- [ ] Viết `@Service` cho `LoginUserPort` (sai pass → `401`, đúng → cấp JWT)
- [ ] Viết `@Service` cho `GetCurrentUserPort` (đọc `sub` từ JWT)
- [ ] Thay `BcryptPasswordAdapter` stub raw bằng `BCryptPasswordEncoder` thật
- [ ] Thay stub `X-User-Id` bằng parse `Authorization: Bearer <jwt>` → `sub`
- [ ] Giữ luật: client không tự truyền `userId` trong body
- [ ] Cập nhật `docs/api.md` nếu field đổi (API-first: sửa doc trước)

## Định nghĩa xong

- `mvn -pl services/user-service compile` xanh
- `POST /api/v1/auth/register` → `201` đúng `UserResponse`
- `POST /api/v1/auth/login` → `200` đúng `TokenResponse`
- `GET /api/v1/users/me` với JWT → `200` đúng chủ token
- Không còn đọc `X-User-Id` trong code mới

## Bàn giao tích hợp

- Cấp JWT cho Người 5 test gateway
- Chốt claim `sub` = UUID user để 4 service còn lại đọc
- Không đổi path `/api/v1/auth/**`, `/api/v1/users/**`
