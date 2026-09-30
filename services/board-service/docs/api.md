# API — Board (kanban-api mục 2)

Thuộc `board-service` (`:8082`). Giữ bảng + cột, không trả thẻ chi tiết.

Auth kỳ 1: stub đọc `X-User-Id` (UUID). TODO parse JWT `sub` ở gateway/service.

## 2.1 Tạo bảng

`POST /api/v1/boards` → `201 Created`.

Request:

```json
{
  "name": "MSS301 Project",
  "key": "MSS",
  "columns": ["To Do", "In Progress", "Done"]
}
```

- `name` bắt buộc, không rỗng.
- `key` bắt buộc, `2-20` ký tự hoa/số (`[A-Z0-9]{2,20}`), unique.
- Thiếu `columns` hoặc rỗng → server tự tạo 3 cột mặc định trên.

Response `201` như kanban-api 2.1 (`id`, `name`, `key`, `ownerId`, `columns[{id,name,position}]`).

## 2.2 Lấy danh sách bảng của tôi

`GET /api/v1/boards` → `200 OK`, mảng bảng mà `X-User-Id` là owner.

## 2.3 Lấy chi tiết bảng kèm cột

`GET /api/v1/boards/{boardId}` → `200 OK`, body như 2.1. Không trả thẻ.

## 2.4 Thêm cột

`POST /api/v1/boards/{boardId}/columns` → `201 Created`.

Request `{ "name": "Review" }` (`name` bắt buộc). Response `{ "id": 13, "name": "Review", "position": 3 }` (cột mới xếp cuối).

## 2.5 Đổi thứ tự cột

`PATCH /api/v1/boards/{boardId}/columns/order` → `200 OK`.

Request `{ "orderedColumnIds": [10, 13, 11, 12] }` (đủ id, không trùng, đều thuộc bảng). Response danh sách cột sau khi xếp.

## Lỗi

Format chuẩn `{code,message,timestamp,path}` (xem kanban-api mục 6):

- `400 BAD_REQUEST` — thiếu field, `key` sai format, `orderedColumnIds` rỗng/trùng/thiếu, cột không thuộc bảng.
- `401` — thiếu `X-User-Id` (kỳ sau: thiếu/sai JWT).
- `403` — không phải member bảng (kỳ sau, hiện controller giữ chỗ check).
- `404 BOARD_NOT_FOUND` — không thấy bảng.
- `409 BOARD_KEY_CONFLICT` — trùng `key` bảng.
