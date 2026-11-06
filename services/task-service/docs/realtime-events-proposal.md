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

**Quyết định:** Task producer và contract payload đã được triển khai theo Outbox + RabbitMQ + Inbox hiện có. Producer ghi vào Outbox trong transaction Card/Comment; Notification consumer, broker acceptance và E2E delivery vẫn thuộc task tích hợp riêng. Việc có producer code không khẳng định event đã được publish hoặc consume.

#### Catalog và recipient rules

| Wire event | Khi phát | Recipient | Payload trong `EventEnvelope.payload` |
|---|---|---|---|
| `card.assigned` | Tạo Card với assignee khác actor hoặc đổi assignee sang user khác. Không phát khi unassign, tự gán, request không đổi recipient hoặc chỉ đổi membership generation. | Assignee mới; không gửi cho actor. | `projectId`, `boardId`, `cardId`, `recipientUserId` |
| `comment.mentioned` | Sau khi Comment và Outbox cùng commit, nếu còn recipient sau xác minh. Chỉ dùng `mentionedUserIds` tường minh từ request. | Recipient IDs duy nhất đã xác minh có Account ACTIVE và Project Membership ACTIVE; loại tác giả. Danh sách được đóng băng tại thời điểm ghi Comment. | `projectId`, `boardId`, `cardId`, `commentId`, `recipientUserIds` |

Notification ánh xạ `card.assigned` thành `ASSIGNED` và `comment.mentioned` thành `MENTIONED`, tạo một Notification cho mỗi recipient. Notification tự tạo title từ event type/context. Không phát `MOVED`; Task không gửi actor ID, membership version, Card title hoặc Comment body. Mention không được lưu thành bảng/lịch sử riêng.

Ví dụ payload thực tế trong envelope:

```json
{
  "eventId": "d063e5a4-2d39-486e-a722-52b5f721a74e",
  "eventType": "card.assigned",
  "schemaVersion": 1,
  "aggregateType": "TASK_NOTIFICATION_INTENT",
  "aggregateId": "d063e5a4-2d39-486e-a722-52b5f721a74e",
  "aggregateVersion": 1,
  "occurredAt": "2026-11-07T10:30:00Z",
  "payload": {
    "projectId": 1,
    "boardId": 2,
    "cardId": 42,
    "recipientUserId": "2d79f300-b673-4e15-a184-7ee3b58d67df"
  }
}
```

```json
{
  "eventId": "51fc560e-f027-4dc3-b1ae-2d020810cde3",
  "eventType": "comment.mentioned",
  "schemaVersion": 1,
  "aggregateType": "TASK_NOTIFICATION_INTENT",
  "aggregateId": "51fc560e-f027-4dc3-b1ae-2d020810cde3",
  "aggregateVersion": 1,
  "occurredAt": "2026-11-07T10:31:00Z",
  "payload": {
    "projectId": 1,
    "boardId": 2,
    "cardId": 42,
    "commentId": 900,
    "recipientUserIds": ["2d79f300-b673-4e15-a184-7ee3b58d67df"]
  }
}
```

#### Envelope, schema và transaction

- Cả hai payload có `schemaVersion=1`.
- Dùng `aggregateType=TASK_NOTIFICATION_INTENT`, `aggregateId=eventId`, `aggregateVersion=1`. Mỗi event là một intent độc lập; không có thứ tự hoặc gap detection theo Card. Consumer dedup theo `(consumerName,eventId)`.
- `EventEnvelope.aggregateVersion`, `outbox_events.aggregate_version`, `inbox_events`, `OutboxEventWriter` và `OutboxRelay` đã có. Không thêm `cards.aggregate_version`, cột Outbox hoặc migration theo contract này.
- `OutboxEventWriter.append()` yêu cầu transaction đang mở; lỗi serialize/ghi Outbox làm rollback thay đổi Card/Comment. Retry relay giữ nguyên event envelope và ID.
- Producer assignment vẫn bị gate bởi trusted Gateway identity. Comment có mention bị từ chối với 503 khi trusted identity chưa bật; không ghi Comment/Outbox trong trường hợp đó. `X-User-Id` không được xem là identity đáng tin cậy trước khi Gateway hardening hoàn tất.

#### Ranh giới tích hợp và bằng chứng còn thiếu

Task không gọi internal Notification REST. Consumer cần ghi Inbox và mọi Notification tạo ra trong cùng transaction, rồi ACK sau commit. Consumer, queue/binding, retry/DLQ, xác minh schema SQL Server vật lý và kiểm tra publish/consume/rollback/redelivery thuộc task tích hợp. Messaging và Outbox relay mặc định tắt; shared relay hiện retry mỗi 5 giây nhưng chưa có giới hạn lần thử hoặc DLQ. Không bật delivery production trước khi task tích hợp chốt các chính sách này.

Bootstrap SQL tạo sẵn `outbox_events.aggregate_version` và `inbox_events` cho database rỗng; `ddl-auto=none` không cập nhật database hiện hữu. Đối chiếu schema vật lý theo [database docs](database.md) trước khi bật relay. #24 WebSocket vẫn là kênh ephemeral, tách biệt khỏi các event bền vững này.
