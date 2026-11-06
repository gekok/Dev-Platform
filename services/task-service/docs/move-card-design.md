# Thiết kế issue #21: Move Card

## Phạm vi

Di chuyển Card trong Project/Board và cập nhật position source/target nguyên tử; không phát event chưa được duyệt, không gọi internal REST chưa bảo mật. Khi chưa thể xác minh Board/Column, API phải từ chối và không ghi dữ liệu. Không thêm mock.

## Phương án

Phương án được chọn: tách kiểm tra quan hệ Board/Column thành port; adapter mặc định từ chối khi chưa có nguồn tin cậy. Thuật toán sắp xếp và persistence được triển khai trong task-service, rồi nối adapter thật khi dependency #32 sẵn sàng. Phương án chờ toàn bộ #32 trước khi viết thuật toán trì hoãn phần việc độc lập.

## Thiết kế dự kiến

- Kiểm tra actor qua guard hiện có. Tìm Card theo projectId/cardId; Card không tồn tại hoặc sai Project trả 404. Kiểm tra source và destination thuộc Board/Project của Card qua port; chưa có adapter thật thì trả 503 và không ghi.
- Dùng transaction SQL Server cho toàn bộ thay đổi. Serialize các lần reorder cùng Project/Board bằng khóa giao dịch SQL Server `sp_getapplock` theo Project/Board, xử lý thất bại lấy lock như lỗi và rollback. Mọi đường ghi position trong task-service phải dùng cùng khóa trước khi bật move; issue #20 tạo Card hiện đang fail closed và cần áp dụng khóa khi triển khai.
- Sắp các Card theo `position DESC, id DESC`; `toPosition` là chỉ số hiển thị bắt đầu từ 0, sau khi gỡ Card đang di chuyển; chỉ chấp nhận 0 đến số Card đích còn lại. Chèn Card vào chỉ số đó, gán lại position không âm, duy nhất theo thứ tự giảm dần cho mỗi Column bị ảnh hưởng. Reorder cùng Column xử lý một danh sách. Ghi và trả Card cùng transaction; lỗi rollback toàn bộ.
- Không phát domain event đến khi catalog và Outbox contract được duyệt. Ghi rõ cursor có thể stale sau move. Không thay đổi schema và không thêm FK giữa service.

## Kiểm tra và giới hạn

Kiểm tra move cùng/khác Column, vị trí biên, position trùng, Card sai Project, destination không hợp lệ, actor không có quyền, rollback và concurrent move bằng SQL Server. Issue chưa thể nghiệm thu E2E khi thiếu adapter xác minh quan hệ, Redis hoặc Gateway identity đáng tin cậy. Quy ước `toPosition` là chỉ số hiển thị là đề xuất cần xác nhận vì tài liệu cũ nêu position bắt đầu từ 0 trong khi list đã chốt DESC.
