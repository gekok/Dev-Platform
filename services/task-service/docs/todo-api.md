# Todo Task API

Base URL qua gateway: `http://localhost:8080/api/v1`

Tất cả endpoint Todo yêu cầu `Authorization: Bearer <accessToken>`.

## Create task

`POST /todo/tasks`

```json
{
  "title": "Hoan thien mobile auth",
  "description": "Ket noi Flutter voi gateway",
  "projectId": 1,
  "startDate": "2026-10-11T09:00:00Z",
  "deadline": "2026-10-15T17:00:00Z"
}
```

Task mới có `status=TODO` và `priority=MEDIUM`.

## List tasks

`GET /todo/tasks`

Chỉ trả về task thuộc user trong JWT `sub`, sắp theo deadline rồi ngày tạo.

## Detail, update, delete

- `GET /todo/tasks/{taskId}`
- `PATCH /todo/tasks/{taskId}`
- `DELETE /todo/tasks/{taskId}`

Status hợp lệ: `TODO`, `IN_PROGRESS`, `DONE`, `CANCELLED`.

Priority hợp lệ: `LOW`, `MEDIUM`, `HIGH`, `URGENT`.
