# Issue #20: Card pagination design

## Mục tiêu và giới hạn

Thực hiện GET Card trong phạm vi Project và Board, hỗ trợ phân trang keyset ổn định theo Column. Redis authorization guard phải chạy trước khi đọc. Việc tạo Card chỉ được bật khi có nguồn đáng tin cậy xác minh Board và Column thuộc Project; internal REST bảo mật chưa có, vì vậy POST fail closed và không ghi dữ liệu. Các port khác không thuộc issue này.

## Phương án

Đã chọn triển khai pagination độc lập và giữ POST fail closed. Phương án chờ toàn bộ contract và integration được chốt sẽ trì hoãn pagination. Cursor và response bên dưới là contract tạm, không phải quyết định kiến trúc đã duyệt.

## Thiết kế

- GET route nhận `columnId` bắt buộc để phân trang riêng từng Column, `limit` mặc định 5 trong khoảng 1–20 và `cursor` tùy chọn. Trả `{items, nextCursor}`; `nextCursor` là null ở trang cuối.
- Cursor opaque Base64 URL chứa phiên bản, projectId, boardId, columnId, tên thứ tự `position_desc_id_desc`, position và id. Giới hạn kích thước và kiểm tra nghiêm ngặt định dạng, scope và giá trị; sai trả 400 `BAD_REQUEST` theo handler hiện có. Cursor không phải bằng chứng phân quyền.
- Query lọc projectId và boardId persisted, thêm columnId khi được cung cấp; `ORDER BY position DESC, id DESC`, trang tiếp lọc `position < lastPosition OR (position = lastPosition AND id < lastId)`. Lấy limit + 1 và tạo cursor từ phần tử cuối được trả. Không dùng offset. Card thay đổi vị trí giữa các trang có thể gây lặp hoặc thiếu; client dedupe và reload khi cần.
- Triển khai inbound port đọc bằng service/application port và adapter persistence; giữ mapping assignee theo lô. Redis guard hiện có tiếp tục fail closed. POST sau guard trả 503 với mã riêng cho dependency chưa sẵn sàng, không tin dữ liệu route làm bằng chứng quan hệ.
- Không thêm foreign key liên service hay migration. Cập nhật docs API, schema và task sheet, đánh dấu contract tạm cùng các giới hạn Gateway và integration. Không tuyên bố issue hoàn tất khi phụ thuộc xác minh quan hệ chưa có.

## Kiểm tra

Kiểm tra các trang cùng position, giới hạn, trang cuối, cursor sai scope/định dạng, project isolation, Redis thiếu/lỗi, membership bị từ chối và POST không ghi dữ liệu. Chạy kiểm tra Maven phù hợp; ghi rõ nếu SQL Server/Redis thực tế hoặc E2E không sẵn có.
