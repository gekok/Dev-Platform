# Thiết kế issue #22: Assignment và cleanup

## Mục tiêu và phạm vi

Cho phép gán/bỏ gán Card trong Project, lưu membership generation và giữ fence ngăn assignment của generation bị thu hồi. Cleanup phải bền vững, idempotent theo Inbox và không ảnh hưởng Comment hoặc assignment của generation mới.

## Phương án

Đã chọn tận dụng `TaskAssignee`, Redis guard, membership lock, Inbox/fence/job và Outbox hiện có; hoàn thiện các use case và transaction trong task-service trước. Phương án chờ toàn bộ integration sẽ trì hoãn nghiệp vụ nội bộ. Không giả định Gateway, Redis, RabbitMQ hoặc board-service đã tích hợp end-to-end.

## Assignment

- Controller giữ guard actor và lấy `membershipVersion` của assignee ACTIVE từ snapshot; body không nhận version hay actor. Snapshot thiếu/hỏng/lỗi từ chối; identity `X-User-Id` chỉ đáng tin khi Gateway hoàn thành hardening.
- Application service xử lý `AssignCardPort`: tìm Card theo projectId/cardId; không thấy trả 404; thực hiện assign/unassign và lưu qua persistence port trong transaction. Null assignee là bỏ gán; assignment giống hệt là idempotent. Runtime service chỉ được bật khi trusted Gateway identity đã sẵn sàng; trước đó endpoint dùng fallback fail-closed.
- Persistence phải serialize theo Project/User và kiểm tra fence trước khi ghi assignment generation tương ứng. Giữ attribution người gán và generation; không ghi đè Card cũ làm mất cập nhật move đồng thời. Xác định thứ tự lock nhất quán giữa assignment và cleanup để tránh deadlock.
- Cleanup Membership chỉ phát completion event đã có trong catalog; không phát `card.assigned` hoặc `comment.mentioned`. Hai Notification intent của #25 được ghi bởi các use case Card/Comment riêng.

## Cleanup và retry

- Event cleanup Membership đã có contract trong catalog chung. Task listener bind durable queue theo routing key đã duyệt và chỉ ACK sau khi recorder transaction commit. Listener cần `MESSAGING_ENABLED`, `MESSAGING_CONSUMERS_ENABLED` và `MEMBER_ASSIGNMENT_CLEANUP_RETRY_DLQ_READY=true`; cờ retry/DLQ mặc định false. Khi xử lý lỗi hiện NACK/requeue; chưa bật cho tới khi có bounded retry/backoff và DLQ tương thích với queue.
- Worker chỉ xóa assignment cùng project/user/membershipVersion và board scope. Trước khi hoàn tất phải có fence, kể cả boardIds rỗng. Comment và author không bị sửa.
- Hiện worker bắt lỗi ngay trong transaction sau khi có thể đã xóa assignment hoặc ghi completion. Điều chỉnh để một lỗi sau mutation rollback toàn bộ batch và Outbox; cập nhật retry ở transaction riêng sau rollback. Completion chỉ được commit cùng kết quả batch; giữ source aggregate version và eventId ổn định theo Outbox retry.
- Kiểm tra xử lý cùng lúc, duplicate, rejoin và retry. Không chặn revoke completion chờ cleanup.

## Bằng chứng và giới hạn

Kiểm tra SQL Server transaction/locking và HTTP success/failure khi môi trường cho phép; kiểm tra generation cũ/mới, rollback và cleanup retry. Rabbit listener source đã có nhưng còn cần broker redelivery/ACK E2E và retry/DLQ policy. Board producer, Redis/Gateway thật vẫn là phụ thuộc tích hợp; không tuyên bố issue Done hoặc event end-to-end đã chạy chỉ từ compile hay unit test. Tài liệu thay đổi đặt trong `services/task-service/docs/`.
