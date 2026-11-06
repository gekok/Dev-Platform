# Quyết định và kế hoạch Realtime/Event — issues #24 và #25

**Rà soát:** 2026-11-06

## Issue #24 — Board WebSocket

**Trạng thái:** Kiến trúc đã được chủ dự án duyệt. Phần task-service đã có handler, authorization lifecycle và fanout best-effort sau commit, nhưng runtime mặc định tắt. Chỉ bật sau khi Gateway identity, secured Board lookup và Gateway WebSocket proxy được xác nhận sẵn sàng.

Giữ route và payload đã duyệt:

```text
WS /ws/boards/{boardId}
```

```json
{"event":"card.moved","cardId":100,"toColumnId":11,"toPosition":0}
{"event":"card.created","cardId":101,"columnId":10}
{"event":"comment.added","cardId":100,"commentId":900}
```

Gateway xác thực handshake và chuyển identity tin cậy tới Task; không dùng identity hoặc `projectId` do client tự gửi. Task phân giải `boardId` thành Project từ dữ liệu Board đã lưu, sau đó kiểm tra Account và membership qua authorization snapshot. Lifecycle khi runtime được bật: kiểm tra tại handshake, định kỳ và ngay trước khi gửi; đóng socket nếu membership bị thu hồi, Board đổi Project hoặc không thể xác minh quyền.

Phân phối chỉ giữa các session cùng Board trong một Task instance. Event chỉ gửi sau commit của thao tác Card/Comment. Fanout best-effort: không lưu bền, không retry/replay, không bảo đảm nhận đủ hoặc thứ tự. Client refetch REST sau khi reconnect. Không gửi Card title hoặc Comment body.

### Điều kiện bật runtime và phần tích hợp còn lại

1. **Gateway identity (#34):** xác thực handshake và chuyển tiếp principal tin cậy; loại bỏ identity header từ client. Chốt cách truyền identity tương thích với browser WebSocket.
2. **Board lookup:** board-service cung cấp Board→Project lookup đã xác thực service-to-service. Không dùng Project ID từ caller làm bằng chứng và không tạo mock.
3. **Gateway proxy (#31):** xác nhận route dùng WebSocket upstream và kiểm tra handshake/frame qua Gateway. Source hiện khai báo `/ws/**` với `http://localhost:8083`, nên chưa coi proxy WebSocket sẵn sàng.
4. **Task runtime:** handler chỉ được đăng ký khi cả bốn cờ readiness trong `services/task-service/src/main/resources/application.yml` đều là `true`; mặc định tất cả đều `false`. Cần có adapter `BoardProjectLookupPort` truy vấn Board đã lưu. Cờ cấu hình không tự chứng minh các phụ thuộc đã sẵn sàng.
5. **Acceptance (#35):** kiểm tra member/non-member, revoke khi đang kết nối, Board lookup/snapshot lỗi, event sau commit và reconnect/refetch qua Gateway.

Task phân phối event chỉ giữa các session trong cùng instance, sau commit và best-effort; lỗi fanout không rollback thao tác Card/Comment. Không lưu bền, retry, replay hoặc cam kết thứ tự/đủ sự kiện. Client cần refetch REST sau reconnect. Không đánh dấu #24 hoàn tất chỉ dựa trên handler đã có trong source; xác nhận qua Gateway và các phụ thuộc thuộc integration task.

## Issue #25 — Durable Task Events

**Trạng thái:** Chưa duyệt producer Card/Comment. Không thêm Card/Comment event type cho #25 hoặc thay đổi schema. Producer Outbox completion của assignment cleanup #22 là luồng riêng đã có trong code.

Giữ hướng Outbox + RabbitMQ + Inbox, tái sử dụng `shared/messaging-support`:

- `EventEnvelope<T>` đã có field `aggregateVersion`.
- `outbox_events.aggregate_version` đã có trong JPA mapping và local bootstrap SQL; `OutboxEventWriter` lưu giá trị từ envelope. Không cần thêm field vào envelope hoặc cột chung vào Outbox.
- Shared `InboxEventRecorder` và `OutboxRelay` có trong source; không tạo relay/Inbox riêng cho Task. Việc có class/mapping không chứng minh RabbitMQ wiring, ACK/retry hay luồng Notification đã chạy E2E.
- `EventTypes` hiện chưa có Card/Comment event type; event assignment-cleanup completion hiện có thuộc #22, không phải #25.
- `CardEntity`/bảng `cards` hiện không có version field riêng. Đây không phải lý do để thêm cột khi contract chưa yêu cầu version state trên Card.

Không đề xuất thêm `cards.aggregate_version` ở thời điểm này. Dùng field `aggregateVersion` đã có trong envelope theo contract chung. Chỉ cần thống nhất cách tạo giá trị per-aggregate nếu Notification contract thực sự yêu cầu ordering/gap detection theo Card; nếu không có yêu cầu đó, không thêm state vào Card chỉ để lấp field generic.

### Các quyết định còn phải chốt trước khi triển khai

1. Event catalog, payload và schema version với Notification và owner của #29.
2. Recipient semantics: ai nhận từng sự kiện, nguồn xác định recipient, và hành vi khi assignment/mention thay đổi.
3. Notification integration contract: routing/binding, Inbox, ACK/retry và các yêu cầu ordering thực sự cần. Dùng `eventId`/Inbox cho duplicate; dùng field `aggregateVersion` đã có theo contract được chốt.

Trước acceptance, đối chiếu `outbox_events`/`inbox_events` trong SQL Server `kanban_task_db` với [local bootstrap SQL](../../../sql/local-sqlserver-bootstrap.sql). Script đã tạo `outbox_events.aggregate_version` và `inbox_events`; đây chưa phải versioned migration và `ddl-auto=none` không cập nhật database đã tồn tại. Với hạ tầng shared hiện tại, chưa xác định schema change hoặc migration mới nào cần cho #25.

Sau khi contract được duyệt, Task mới nối producer vào transaction nghiệp vụ bằng `OutboxEventWriter.append()`. Kiểm tra rollback cùng Outbox, retry giữ nguyên event, consumer Inbox trước ACK và recipient thực tế. Không phát Notification-facing event từ WebSocket; các message #24 vẫn là ephemeral, riêng biệt.
