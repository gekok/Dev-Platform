# user-service API và ranh giới triển khai

**Vai trò mục tiêu:** sở hữu Account, thông tin đăng nhập và trạng thái vòng đời tài khoản. Quyết định chung: [architecture decisions](../../../docs/project/architecture-decisions.md); route Project-first mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

## Controller hiện có

`POST /api/v1/auth/register` khai báo đăng ký công khai, `POST /api/v1/auth/login` khai báo đăng nhập, `GET /api/v1/users/me` khai báo lấy hồ sơ. Đây là các route được `UserController` ánh xạ; route declaration không chứng minh use case đã chạy hoàn chỉnh.

- `/auth/register` nhận `email`, `password`, `fullName` và gọi `RegisterUserPort`.
- `/auth/login` gọi `LoginUserPort`; hiện chưa có implementation của port này.
- `/users/me` hiện dùng `X-User-Id` để lấy Account; `Authorization` được đọc nhưng JWT chưa được parse.
- Repo chưa có route Admin tạo/xóa Account. Chính sách public registration, route tạo Account và route xóa Account phải theo quyết định/hợp đồng Admin; không lấy route register cũ làm bằng chứng đã chốt nghiệp vụ.

## Thiết kế mục tiêu đã thống nhất

- System Admin quản lý vòng đời Account; Admin không có quyền nghiệp vụ trong Project.
- Owner duy nhất của Project không bị biến thành “admin hệ thống”. Account được mời chỉ thành Member sau khi chấp nhận Invitation.
- Khi yêu cầu xóa Account được ghi nhận, user-service đặt trạng thái `DELETING`, tạo operation `operationId` và ghi `ACCOUNT_DELETION_REQUESTED` vào Outbox trong cùng transaction.
- API khởi tạo dự kiến trả `202 Accepted` sau khi request và Outbox commit. Route, status response và lỗi chi tiết còn mở.
- user-service chỉ hoàn tất operation sau khi board-service xác nhận Project/Membership cleanup; chi tiết consumer/wiring chưa được triển khai.

## Chưa triển khai hoặc cần chốt

- Login, đăng ký/provisioning Account và phát token chưa đủ để coi Auth hoàn chỉnh.
- `AccountAccessGuard` chưa được nối vào login/controller; trạng thái `DELETING` hiện chưa chặn đăng nhập ở runtime.
- Chưa có Admin controller, authorization cho Admin, deletion status endpoint, Rabbit listener cho completion hoặc gateway auth.
- Xem [hướng dẫn cascade skeleton](../../../docs/project/account-deletion-skeleton-guide.md) để tiếp tục theo các TODO đúng service.

## Nguồn code

`UserController`, `AccountDeletionApplicationService`, `AccountDeletionCompletionHandler`, `AccountDeletionOperationEntity` và `UserEntity` trong `services/user-service/src/main/java`.
