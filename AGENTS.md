# Luôn Luôn Áp Dụng (Auto-Enforced)

## 1. Tối Ưu Hóa Token & Phản Hồi
- Không giải thích lại câu hỏi, không chào hỏi/kết thúc xã giao.
- Khi sửa mã nguồn: Chỉ xuất unified diff hoặc đúng khối code cần sửa kèm số dòng, tuyệt đối không in lại cả file.
- Khi đọc mã nguồn: Dùng grep/ripgrep để đọc các hàm/class liên quan, không đọc toàn bộ file vượt quá 100 dòng.
- Phản hồi bằng chữ tối đa 3-5 gạch đầu dòng ngắn gọn.

## 2. Quy Trình Làm Việc Kỷ Luật
- Với tác vụ sửa/thêm code: Tự động phác thảo danh sách file cần sửa và kế hoạch kiểm thử trước khi ghi file.
- Debug: Phân tích log/stack trace, tìm nguyên nhân gốc rễ (root cause) trước khi sửa, không sửa thử mò mẫm.
