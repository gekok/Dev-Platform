# Issue #20: Card pagination design

## Mục tiêu và giới hạn

Thực hiện GET Card trong phạm vi Project và Board, hỗ trợ phân trang keyset ổn định theo Column. Redis authorization guard phải chạy trước khi đọc. Khi tài liệu này được viết, internal REST bảo mật chưa có nên POST được giữ fail closed. Source hiện đã nối `ColumnRelationshipValidator` với Board lookup có `X-Service-Token`; POST vẫn fail closed khi Task identity gate mặc định tắt hoặc Board lookup không xác minh được. Các port khác không thuộc issue này.

## Phương án

Đã chọn triển khai pagination độc lập, không chờ toàn bộ contract và integration. Trạng thái ban đầu giữ POST fail closed; sau đó source nối validator vào secured Board lookup. Cursor và response bên dưới là contract tạm, không phải quyết định kiến trúc đã duyệt.

## Thiết kế

- GET route nhận `columnId` bắt buộc để phân trang riêng từng Column, `limit` mặc định 5 trong khoảng 1–20 và `cursor` tùy chọn. Trả `{items, nextCursor}`; `nextCursor` là null ở trang cuối.
- Cursor opaque Base64 URL chứa phiên bản, projectId, boardId, columnId, tên thứ tự `position_desc_id_desc`, position và id. Giới hạn kích thước và kiểm tra nghiêm ngặt định dạng, scope và giá trị; sai trả 400 `BAD_REQUEST` theo handler hiện có. Cursor không phải bằng chứng phân quyền.
- Query lọc projectId và boardId persisted, thêm columnId khi được cung cấp; `ORDER BY position DESC, id DESC`, trang tiếp lọc `position < lastPosition OR (position = lastPosition AND id < lastId)`. Lấy limit + 1 và tạo cursor từ phần tử cuối được trả. Không dùng offset. Card thay đổi vị trí giữa các trang có thể gây lặp hoặc thiếu; client dedupe và reload khi cần.
- Triển khai inbound port đọc bằng service/application port và adapter persistence; giữ mapping assignee theo lô. Redis guard hiện có tiếp tục fail closed. `ColumnRelationshipValidator` dùng Board lookup để xác minh quan hệ; lookup lỗi trả 503, không tin dữ liệu route làm bằng chứng.
- Không thêm foreign key liên service hay migration. Cập nhật docs API, schema và task sheet, đánh dấu contract tạm cùng các giới hạn Gateway và integration. Source đã có validator; chưa coi tích hợp hoàn tất trước khi nghiệm thu Gateway/Redis/Board end-to-end.

## Kiểm tra

Kiểm tra các trang cùng position, giới hạn, trang cuối, cursor sai scope/định dạng, project isolation, Redis thiếu/lỗi, membership bị từ chối và POST không ghi dữ liệu. Chạy kiểm tra Maven phù hợp; ghi rõ nếu SQL Server/Redis thực tế hoặc E2E không sẵn có.
