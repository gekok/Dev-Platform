# Kế hoạch issue #23

Thiết kế: `comment-design.md`.

1. Thêm port đọc/ghi Comment, service kiểm tra nội dung, Card scope và membership của mention.
2. Thêm adapter JPA và route GET; giữ guard cho cả GET/POST và không phát event chưa chốt.
3. Cập nhật tài liệu service và ghi rõ giới hạn identity Gateway, Redis và tích hợp.
