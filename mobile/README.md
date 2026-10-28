# Dev-Platform Mobile Client

**Trạng thái:** Flutter prototype có HTTP client và các màn Auth/Task cơ bản; chưa tích hợp với API Kanban Project-first. Tham chiếu [chỉ mục tài liệu](../docs/README.md), [API mục tiêu](../docs/api/kanban-api.md) và [quyết định kiến trúc](../docs/project/architecture-decisions.md).

Code hiện gọi **/auth/register**, **/auth/login**, **/users/me** và **/todo/tasks**. Backend hiện không có route **/todo/tasks**; đăng ký là route legacy, còn backend Kỳ 1 mục tiêu chưa chốt route Admin tạo Account. Vì vậy cấu hình API base URL không đồng nghĩa app đã tích hợp end-to-end.

## Chay local

```bash
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
```

Android emulator dùng `10.0.2.2` để trỏ về máy host. iOS simulator có thể dùng
`http://localhost:8080/api/v1`.

## Cau truc

- `lib/core`: cấu hình và HTTP client dùng chung.
- `lib/features/auth`: register, login, token storage.
- `lib/features/tasks`: model, repository và task list.

Client gửi Bearer token nếu token đã lưu, nhưng không có bằng chứng đây là luồng JWT được Gateway hiện tại xác thực. Chỉ đánh dấu tích hợp sau khi cập nhật client theo Project-first contract và chạy xác nhận qua Gateway.
