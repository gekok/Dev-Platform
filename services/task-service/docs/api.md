# Card API (task-service, qua gateway `http://localhost:8080/api/v1`)

`userId` lấy từ JWT `sub`. Kỳ này đọc stub từ header `X-User-Id`.

## 3.1 Tạo thẻ

`POST /api/v1/boards/{boardId}/cards`

Request:

```json
{
  "columnId": 10,
  "title": "Dựng skeleton Dev-Platform",
  "description": "pom + gateway + api.md",
  "assigneeId": "c0a8b1e2-0000-4000-8000-000000000001",
  "dueDate": "2026-10-15T17:00:00Z"
}
```

Response `201 Created`: `id`, `boardId`, `columnId`, `title`,
`status`, `position`, `assigneeId`.

## 3.2 Lấy thẻ theo bảng

`GET /api/v1/boards/{boardId}/cards?columnId=10`

`columnId` tùy chọn để lọc. Response `200 OK`: mảng thẻ theo `position`.

## 3.3 Kéo thẻ qua cột (move)

`PATCH /api/v1/cards/{cardId}/move`

Request:

```json
{ "toColumnId": 11, "toPosition": 0 }
```

Response `200 OK`: thẻ sau chuyển, kèm `columnId` + `position` mới.

## 3.4 Gán người làm

`PATCH /api/v1/cards/{cardId}/assignee`

Request:

```json
{ "assigneeId": "c0a8b1e2-0000-4000-8000-000000000001" }
```

Khi gán mới bắn event cho notification-service (kỳ 1).

## 3.5 Bình luận

`POST /api/v1/cards/{cardId}/comments`

Request:

```json
{
  "content": "@an review giúp em đoạn move",
  "mentionedUserIds": ["c0a8b1e2-0000-4000-8000-000000000002"]
}
```

Response `201 Created`: bình luận vừa tạo.

## 5. Real-time

Kênh WS theo bảng:

```text
WS /ws/boards/{boardId}
```

Event server bắn xuống (kỳ 1 qua `CardEventPublisherPort`):

```json
{ "event": "card.moved", "cardId": 100, "toColumnId": 11, "toPosition": 0 }
```

```json
{ "event": "card.created", "cardId": 101, "columnId": 10 }
```

```json
{ "event": "comment.added", "cardId": 100, "commentId": 900 }
```

## Lỗi

| Status | Khi nào |
|---|---|
| 400 | Thiếu field, `position` âm, title trống |
| 404 | Không thấy thẻ |
