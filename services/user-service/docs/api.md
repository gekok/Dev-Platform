# Auth / User API (user-service, qua gateway `http://localhost:8080/api/v1`)

`userId` lấy từ JWT `sub`. Client không tự truyền `userId` trong body.

## 1.1 Đăng ký

`POST /api/v1/auth/register`

Request:

```json
{
  "email": "sv@fpt.edu.vn",
  "password": "123456",
  "fullName": "Nguyen Van A"
}
```

Response `201 Created`:

```json
{
  "id": "c0a8b1e2-0000-4000-8000-000000000001",
  "email": "sv@fpt.edu.vn",
  "fullName": "Nguyen Van A"
}
```

## 1.2 Đăng nhập

`POST /api/v1/auth/login`

Request:

```json
{
  "email": "sv@fpt.edu.vn",
  "password": "123456"
}
```

Response `200 OK`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer"
}
```

## 1.3 Lấy hồ sơ hiện tại

`GET /api/v1/users/me`

Header: `Authorization: Bearer <jwt>`.

Response `200 OK`: `id`, `email`, `fullName` của `sub` trong token.

## Lỗi

| Status | Code | Khi nào |
|---|---|---|
| 400 | `BAD_REQUEST` | Thiếu field, email sai, password quá ngắn |
| 401 | `UNAUTHORIZED` | Thiếu/sai JWT khi gọi `/users/me` |
| 409 | `EMAIL_EXISTS` | Trùng email đăng ký |
