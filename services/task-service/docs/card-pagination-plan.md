# Kế hoạch issue #20

Thiết kế: `card-pagination-design.md`. Phạm vi thay đổi chỉ trong task-service.

- [ ] Bổ sung kiểm tra tích hợp pagination, scope cursor và fail closed với SQL Server và Redis.
- [x] Thực hiện cursor, truy vấn keyset và adapter đọc Card.
- [x] Bảo vệ POST khi chưa xác minh được quan hệ Board/Column.
- [ ] Cập nhật tài liệu runtime và hoàn tất kiểm tra tích hợp.
