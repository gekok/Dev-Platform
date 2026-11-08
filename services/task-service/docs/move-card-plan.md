# Kế hoạch issue #21

Thiết kế: `move-card-design.md`.

1. Tạo port xác minh Column và adapter mặc định fail closed.
2. Triển khai move trong transaction, khóa theo Project/Board và sắp xếp lại hai Column.
3. Nối controller qua port hiện có và cập nhật tài liệu runtime.
4. Kiểm tra SQL Server, quyền và đồng thời trước khi nghiệm thu; tích hợp xác minh thật phụ thuộc #32.
