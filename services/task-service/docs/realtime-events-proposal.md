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

### Rà soát hợp đồng Task–Notification (đề xuất, chưa được duyệt)

Các dữ kiện đang có trong source/tài liệu:

- `EventTypes` chưa khai báo Card/Comment event. `TaskEventListener` phía Notification chỉ còn TODO cho assignment và mention.
- `NotificationAggregate.TypeValue` có `ASSIGNED`, `MENTIONED`, `MOVED`, nhưng enum không xác định khi nào phát, ai nhận hoặc nội dung Notification. `MOVED` không xuất hiện trong TODO hiện tại của listener.
- Phần Board-first cũ trong Kanban API ghi rằng assignment mới báo người được gán nhưng không báo actor; `mentionedUserIds` nhận thông báo mention. Phần này chỉ là tham khảo runtime cũ, không phải event contract Project-first đã duyệt.
- Task hiện kiểm tra `mentionedUserIds` nhưng không lưu mention. Comment row giữ author/content; việc có lưu lịch sử mention hay không vẫn chưa được quyết định.

Để review với owner Notification, phương án nhỏ nhất có thể là:

| Candidate wire event | Phát khi | Recipient đề xuất | Payload tối thiểu đề xuất |
|---|---|---|---|
| `card.assigned` | Card được tạo với assignee hoặc assignee thực sự đổi sang một user; không phát khi bỏ gán hay request không làm thay đổi assignment | Assignee mới; không gửi cho actor theo ghi chú API Board-first cũ | `projectId`, `boardId`, `cardId`, `assigneeId`, `assignedByUserId`, `membershipVersion` |
| `comment.mentioned` | Comment commit thành công và có danh sách mention đã validate | Mỗi `mentionedUserId` duy nhất; chính sách tự mention cần duyệt | `projectId`, `boardId`, `cardId`, `commentId`, `authorId`, `mentionedUserIds` |

Không đưa `card.moved` vào catalog đề xuất ban đầu: giá trị `MOVED` trong enum chưa có producer/consumer rule hoặc recipient đã duyệt. Cũng cần quyết định liệu event mention chỉ mang ID, hay Notification cần thêm Card title/comment excerpt để tạo `title`; không nên gửi nguyên comment body nếu không có nhu cầu đã duyệt.

**Version và schema đề xuất:** các notification intent này là sự kiện độc lập; Notification chỉ cần dedup bằng `(consumerName,eventId)` và không cần thứ tự/gap detection theo Card. Nếu owner đồng ý, dùng `aggregateType=TASK_NOTIFICATION_INTENT`, `aggregateId=eventId`, `aggregateVersion=1`; không thêm `cards.aggregate_version`. Đây là một cách gán semantics cho field bắt buộc trong `EventEnvelope`, cần được xác nhận trước producer. Nếu cần thứ tự theo Card thay vào đó, phải chọn version state bền vững và migration áp dụng cho mọi đường ghi Card; không lấy `membershipVersion`, `position` hoặc `commentId` làm Card version.

Với phương án event-intent, không cần thay đổi schema Task: `EventEnvelope.aggregateVersion`, `outbox_events.aggregate_version` và `inbox_events` đã có trong shared mapping/bootstrap SQL. Repo chưa chọn versioned migration tool và chưa có bằng chứng schema vật lý của mọi DB khớp bootstrap; nếu cần thêm bảng/cột, việc chọn migration và rollout phải được quyết định riêng trước khi code producer.

Notification nên consume RabbitMQ, ghi Inbox và toàn bộ Notification cho event trong cùng transaction, rồi ACK sau commit. Task không gọi `POST /internal/v1/notifications`: endpoint hiện không có service-to-service authentication và bị tắt mặc định. Queue/binding, consumer name, title generation, mention self-notification và retry/DLQ policy vẫn cần Notification/integration owner chốt. Shared Outbox Relay hiện retry mỗi 5 giây nhưng chưa có attempt limit hoặc DLQ; đây cũng là giới hạn vận hành cần chốt trước khi bật delivery.

Phần này là tài liệu review để đóng các quyết định của #25, chưa phải `EventTypes`, schema/payload contract hay source runtime được duyệt.
