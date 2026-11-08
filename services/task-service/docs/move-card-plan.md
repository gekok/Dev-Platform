# Kế hoạch issue #21

Thiết kế: `move-card-design.md`.

**Trạng thái:** Các bước triển khai source đã hoàn tất. File này lưu kế hoạch ban đầu; nghiệm thu SQL Server, Gateway/Redis và Board lookup end-to-end vẫn phụ thuộc #32.

1. [x] Tạo port xác minh Column, nối với Board lookup có `X-Service-Token` và fail closed nếu lookup không xác minh được.
2. [x] Triển khai move trong transaction, khóa theo Project/Board và sắp xếp lại hai Column.
3. [x] Nối controller qua port và cập nhật tài liệu runtime.
4. [ ] Kiểm tra SQL Server, quyền và đồng thời; nghiệm thu tích hợp thật phụ thuộc #32.
