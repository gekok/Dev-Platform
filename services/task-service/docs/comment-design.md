# Thiết kế issue #23: Comment theo Project

## Mục tiêu và phạm vi

Cho phép thành viên Project tạo và đọc Comment của Card thuộc Project. Giữ nguyên author và nội dung khi membership bị thu hồi. Không triển khai cascade xóa Comment hoặc gọi internal REST chưa bảo mật.

## Phương án

Đã chọn triển khai lưu/đọc Comment trong task-service, dùng authorization snapshot hiện có; tích hợp identity đáng tin cậy tại Gateway thực hiện khi dependency sẵn sàng. Phương án chờ toàn bộ Gateway và integration khiến nghiệp vụ độc lập bị trì hoãn.

## Thiết kế

- POST và GET `/api/v1/projects/{projectId}/cards/{cardId}/comments` kiểm tra actor là member qua `ProjectAuthorizationGuard`, sau đó xác minh Card có `project_id` khớp route. Card không tồn tại hoặc sai Project trả cùng một lỗi 404, không tiết lộ Card ngoài Project.
- Identity lấy từ `X-User-Id` như controller hiện có; không nhận author trong body. Header hiện chưa đáng tin nếu gọi trực tiếp hoặc Gateway chưa ghi đè, vì vậy không tuyên bố yêu cầu authenticated identity đã được nghiệm thu cho tới khi #29 hoàn thành.
- POST từ chối content null hoặc chỉ whitespace, lưu content, author UUID, timestamp và Card trong transaction. `mentionedUserIds` null được coi là rỗng; từ chối phần tử null và kiểm tra từng UUID có Account ACTIVE cùng Project Membership ACTIVE qua snapshot. Snapshot thiếu, hỏng hoặc Redis lỗi trả 503; membership không active bị từ chối. Khi trusted Gateway identity sẵn sàng, recipient IDs được khử trùng lặp, tác giả bị loại và `comment.mentioned` ghi vào Outbox cùng transaction; Mention IDs không được lưu riêng và Comment body không đi vào event.
- GET trả danh sách Comment theo id tăng dần trong đúng Project/Card; response gồm id, cardId, authorId, content, createdAt. Không thêm phân trang mới khi contract chưa quy định.
- Controller gọi application ports; application service dùng output port cho persistence; JPA adapter thực hiện query có ràng buộc projectId và cardId. Không làm thay đổi cleanup assignment hoặc xóa Comment.
- WebSocket `comment.added` của #24 vẫn best-effort và tách biệt với durable Notification intent `comment.mentioned` của #25. Không thêm migration trong Kỳ 1. Cập nhật API/database docs trong task-service, phân biệt code với tích hợp thật.

## Kiểm tra và giới hạn

Kiểm tra tạo/đọc đúng scope, Card sai Project, nội dung invalid, mention sai membership, snapshot thiếu/lỗi, từ chối actor và bảo toàn attribution. Kiểm tra transaction/persistence khi có SQL Server; ghi rõ phần chưa kiểm tra được với Redis/Gateway và yêu cầu bằng chứng tích hợp trước khi đóng issue.
