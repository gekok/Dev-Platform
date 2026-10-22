# Issue 06 — Tích hợp Kanban end-to-end (sau khi 5 service xong)

Loại: tích hợp, chạy sau Issue 01 → 05
Người nhận: Người 5 chủ trì, Người 1 → 4 phối hợp
Nhãn: `service/gateway`

## Điều kiện vào

- [ ] Issue 01 user xong JWT (`sub` UUID)
- [ ] Issue 02 board xong `boardId` + `columnId`
- [ ] Issue 03 task xong move + WS 3 event
- [ ] Issue 04 notify xong internal + list + read
- [ ] Issue 05 gateway xong route + docker 4 DB

## Kịch bản test

- [ ] `docker compose up -d` rồi bật 5 tiến trình (4 service + gateway)
- [ ] Register → login → me qua `http://localhost:8080/api/v1`
- [ ] Tạo board → thêm cột → reorder qua gateway
- [ ] Tạo thẻ → move → gán → comment mention qua gateway
- [ ] WS `/ws/boards/{boardId}` nhận `card.moved`, `card.created`, `comment.added`
- [ ] Notify list + mark read đúng chủ, sai chủ → `403`
- [ ] Mất mạng rồi nối lại: client `GET` lại bảng + thẻ, server là chuẩn cuối

## Định nghĩa xong

- Toàn bộ curl mẫu chạy qua `8080` xanh
- Không còn gọi thẳng `8081` → `8084` từ client
- Không còn `X-User-Id` stub ở luồng chính
- Ghi log lỗi theo format mục 6 (`code,message,timestamp,path`)

## Sau tích hợp

- Khóa field `kanban-api.md` kỳ 1
- Mở nhánh kỳ 2: `repo-service`, `workflow-service` (chưa làm)
