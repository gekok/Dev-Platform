# Quy trình tạo và nghiệm thu GitHub Issue

Tài liệu này quy định cách đưa công việc vào [GitHub Issues](https://github.com/gekok/Dev-Platform/issues), giữ issue khớp thiết kế Project-first và ghi rõ bằng chứng trước khi chuyển sang Done.

## Nguồn chuẩn

Đọc theo thứ tự sau trước khi tạo hoặc sửa issue:

1. [Architecture decisions](architecture-decisions.md): quyết định đã chốt và điểm còn mở.
2. [Kanban API](../api/kanban-api.md): route, payload, response và trạng thái contract.
3. Task sheet service `docs/project/01-*.md` đến `05-*.md` và checklist tích hợp `docs/project/06-integration.md`: ownership, trạng thái runtime và việc còn lại.
4. `services/<service>/docs/` và source code: schema/API runtime thực tế.

Demo React/MSW là tham chiếu UX và mock API. Không dùng nó làm bằng chứng backend đã triển khai. Nếu issue mâu thuẫn với quyết định mới, sửa issue và liên kết quyết định; không tự biến đề xuất thành contract.

## Phân loại và phạm vi issue

- Chọn đúng phase: `Kỳ 1 — đã duyệt`, `Kỳ 2 — đã duyệt`, `Đề xuất/chưa duyệt`, hoặc `Chưa xác định`. Chỉ ghi Kỳ 2 khi tài liệu kiến trúc đã chốt phạm vi đó.
- Đặt prefix theo ownership: `[user-service]`, `[board-service]`, `[task-service]`, `[notification-service]`, `[gateway]`, `[integration]`, `[web-demo]` hoặc `[mobile]`.
- Một issue con mô tả một deliverable có thể review độc lập. Issue cha tóm tắt outcome, dependency và link tới issue con; không lặp acceptance criteria của chúng.
- Ghi rõ kết quả cần đạt, in-scope, out-of-scope, dependency và các quyết định đang chặn. Không tạo implementation task có hợp đồng mơ hồ.
- Mỗi issue phải link tài liệu nguồn và gọi rõ API/schema/event liên quan nếu có. Với thay đổi liên service, ghi producer, consumer, owner của dữ liệu và thứ tự commit/ACK cần kiểm chứng.

## Điều kiện sẵn sàng để làm

Issue chỉ chuyển sang `Todo` khi phase đã rõ, owner và dependency đã xác định, contract đủ dùng, và từng acceptance criterion đều quan sát/kiểm tra được. Nếu còn quyết định kiến trúc, để issue ở `BackLog` hoặc tạo issue loại `design decision`; ghi câu hỏi cụ thể và người cần chốt.

## Acceptance criteria và bằng chứng

Viết criteria dưới dạng hành vi có kết quả quan sát được, mỗi checkbox một điều kiện. Tránh câu như “hoàn thiện API”, “compile xanh” hoặc “xử lý edge case” nếu không nêu chính xác kết quả mong đợi.

Ví dụ cho API: nêu request, status code, các field/side effect cần có và một trường hợp bị từ chối. Với persistence, nêu constraint hoặc hành vi sau khi đọc lại dữ liệu. Với event flow, kiểm chứng event envelope/version, commit Outbox/Inbox, redelivery và kết quả idempotent theo đúng contract. Với UI, nêu thao tác và trạng thái hiển thị.

Trước khi nghiệm thu, người review kiểm tra:

- Tất cả acceptance criteria đã đạt; ghi lệnh chạy và kết quả cụ thể, request/response, log, CI run hoặc ảnh/snapshot phù hợp.
- Có kiểm tra đường thành công và các lỗi nghiệp vụ/quyền có trong scope.
- API, database, Gateway, event và hướng dẫn vận hành liên quan đã được cập nhật cùng implementation.
- PR/commit được liên kết; không còn blocker hoặc tiêu chí chưa được kiểm chứng.

Chỉ chuyển sang `In Review` khi có thay đổi và bằng chứng để review. Chỉ chuyển sang `Done` và đóng issue sau khi criteria được xác nhận và thay đổi đã được merge. Không đóng issue chỉ vì đã sửa tài liệu hoặc đổi trạng thái trong board.

## Diễn giải trạng thái board

| Trạng thái | Ý nghĩa |
|---|---|
| `BackLog` | Chưa duyệt phase, còn quyết định/dependency hoặc chưa đủ điều kiện bắt đầu |
| `Todo` | Đã sẵn sàng, scope và acceptance criteria rõ |
| `In Progress` | Có người đang thực hiện |
| `In Review` | Có PR hoặc thay đổi cụ thể đang được review/nghiệm thu |
| `Done` | Đã merge, toàn bộ criteria có bằng chứng và được chấp nhận |

Khi thay thế hoặc gộp issue, giữ link từ issue cũ sang issue mới. Không đổi trạng thái tiến độ nếu chưa kiểm tra với người đang làm.

## Issue forms

Tạo implementation task bằng form `Công việc triển khai`, bug bằng `Báo lỗi`, và điểm kiến trúc chưa chốt bằng `Quyết định thiết kế`. Các form nằm trong `.github/ISSUE_TEMPLATE/`; dùng checklist nghiệm thu ở trên trước khi đóng issue.
