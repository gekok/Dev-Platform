# WorkflowHub — Project Service (Project + Board)
## Đặc tả Use Case tổng hợp

| Thuộc tính | Giá trị |
| --- | --- |
| Phiên bản | 1.0 |
| Ngày | 07/11/2026 |
| Nguồn | `Kanban_MVP_Business_Requirements_v1.2.docx`, `Kanban_Board_Service_Use_Cases.docx` v1.0, `kanban_mssql_databases.sql` |
| Phạm vi | Toàn bộ use case của **Project Service**, gồm module Project, Membership, Invitation, Board, Column và các API nội bộ phục vụ service khác |
| Công nghệ | Java Spring Boot, SQL Server (`kanban_board_db`), Redis (snapshot quyền), RabbitMQ (Outbox/Inbox), Eureka |
| Quy ước | Điểm nào nguồn không quy định mà tài liệu phải chọn được đánh dấu **(đề xuất)** và tổng hợp ở mục 10 |

---

## Mục lục

1. [Phạm vi và vị trí của Project Service](#1-phạm-vi-và-vị-trí-của-project-service)
2. [Actor và phân quyền](#2-actor-và-phân-quyền)
3. [Danh mục Use Case](#3-danh-mục-use-case)
4. [Quy tắc nghiệp vụ](#4-quy-tắc-nghiệp-vụ)
5. [Chuỗi kiểm tra chuẩn](#5-chuỗi-kiểm-tra-chuẩn)
6. [Đặc tả Use Case — nhóm Project và Thành viên](#6-đặc-tả-use-case--nhóm-project-và-thành-viên)
7. [Đặc tả Use Case — nhóm Board và Column](#7-đặc-tả-use-case--nhóm-board-và-column)
8. [Luồng liên service và sự kiện](#8-luồng-liên-service-và-sự-kiện)
9. [Dữ liệu liên quan và tiêu chí nghiệm thu](#9-dữ-liệu-liên-quan-và-tiêu-chí-nghiệm-thu)
10. [Điểm đề xuất, rủi ro và điểm lệch giữa các tài liệu](#10-điểm-đề-xuất-rủi-ro-và-điểm-lệch-giữa-các-tài-liệu)

---

## 1. Phạm vi và vị trí của Project Service

Hệ thống có **4 service**: User, Project, Task, Notification. **Board không phải microservice riêng** mà là một module nằm trong Project Service, dùng chung database `kanban_board_db`.

Hệ quả:

- Kiểm tra trạng thái Project và membership khi thao tác Board/Column là **truy vấn cục bộ**, làm được trong cùng transaction.
- Không có FK xuyên database: Project Service chỉ tham chiếu scalar tới User (`created_by_user_id`, `user_id`) và **không biết gì về Task**. Mọi thứ liên quan Task đi qua API nội bộ hoặc sự kiện.
- Sự kiện gửi đi ghi vào `outbox_events` của `kanban_board_db`.

| | Thuộc Project Service | Không thuộc Project Service |
| --- | --- | --- |
| **Dữ liệu** | `projects`, `project_memberships`, `invitations`, `boards`, `board_columns`, `project_membership_versions`, `project_authorization_snapshot_sync_jobs`, `outbox_events`, `inbox_events` | Tài khoản, system role (User Service); Task, assignee, bình luận (Task Service); thông báo (Notification Service) |
| **Chức năng** | Tạo/đổi tên/đổi trạng thái Project; lời mời; loại/rời thành viên; chuyển Owner; Board; Column; đồng bộ snapshot membership; API nội bộ | Đăng ký/đăng nhập; tạo/di chuyển Task; gán Task; bình luận; thông báo |
| **Phụ thuộc ngoài** | Hỏi User Service (người được mời có hợp lệ không); hỏi Task Service (số Task trong Column) | — |

---

## 2. Actor và phân quyền

| Actor | Mô tả | Cách nhận diện |
| --- | --- | --- |
| **User** | Người đã đăng nhập, tài khoản ACTIVE. Tạo được Project. | Token hợp lệ, `auth_version` khớp snapshot |
| **Project Owner** | Quản trị Project, Board, Column, thành viên. Tối đa một Owner ACTIVE mỗi Project. | `project_memberships.role = OWNER`, `status = ACTIVE` |
| **Project Member** | Xem cấu trúc Project/Board/Column; được tự rời Project. | `role = MEMBER`, `status = ACTIVE` |
| **Invitee** | Người đang có lời mời PENDING; chưa là thành viên. | `invitations.user_id = actorId`, `status = PENDING` |
| **Người không phải thành viên** | Không được biết Project/Board tồn tại; luôn nhận 404. Gồm cả người đã `REMOVED`. | Không có membership ACTIVE |
| **System Admin** | **Không** có quyền đặc biệt trên Project/Board (BR-07). Chỉ có quyền khi là thành viên. | System role không được xét ở Project Service |
| **Task Service** | Gọi API nội bộ để xác minh Board/Column/membership; được Project Service gọi để đếm Task. | Service credential trên kênh nội bộ |
| **Hệ thống nền** | Worker đồng bộ snapshot sang Redis, publisher Outbox. | — |

### 2.1 Ma trận quyền (Project ACTIVE, Board ACTIVE)

| Hành động | Không phải thành viên | MEMBER | OWNER |
| --- | --- | --- | --- |
| Xem danh sách Project của mình, chi tiết Project | 404 | Có | Có |
| Xem danh sách thành viên | 404 | Có | Có |
| Đổi tên Project | 404 | 403 | Có |
| Đổi trạng thái / mở lại Project | 404 | 403 | Có |
| Mời, xem và hủy lời mời của Project | 404 | 403 | Có |
| Loại thành viên | 404 | 403 | Có (trừ chính Owner) |
| Tự rời Project | 404 | Có | Không (409 `OWNER_CANNOT_LEAVE`) |
| Chuyển quyền Owner | 404 | 403 | Có |
| Xem danh sách Board, chi tiết Board kèm Column | 404 | Có | Có |
| Tạo / đổi tên / đổi trạng thái Board | 404 | 403 | Có |
| Tạo / đổi tên / sắp xếp / xóa Column | 404 | 403 | Có |

Quyền của **Invitee** (xem hộp thư lời mời của mình, chấp nhận, từ chối) không phụ thuộc membership.

### 2.2 Điều kiện theo trạng thái Project và Board

| Project | Board | Xem | Đổi cấu trúc Board/Column | Đổi trạng thái Board | Thành viên / lời mời | Đổi tên Project | Đổi trạng thái Project |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ACTIVE | ACTIVE | Có | Có | Có | Có | Có | Có (→ COMPLETED/CANCELLED) |
| ACTIVE | COMPLETED / CANCELLED | Có | 409 `BOARD_NOT_ACTIVE` | Có (để mở lại) | Có | Có | Có |
| COMPLETED / CANCELLED | Bất kỳ | Có | 409 `PROJECT_NOT_ACTIVE` | 409 `PROJECT_NOT_ACTIVE` | 409 `PROJECT_NOT_ACTIVE` | 409 `PROJECT_NOT_ACTIVE` | Có (chỉ mở lại → ACTIVE) |

> Mở lại Project về ACTIVE **không** tự đổi trạng thái các Board bên trong. Board đang COMPLETED/CANCELLED vẫn chỉ đọc cho đến khi Owner mở lại từng Board.

### 2.3 Vòng đời trạng thái

**Project và Board** (cùng tập giá trị `ACTIVE`, `COMPLETED`, `CANCELLED`):

```
Từ \ Sang    ACTIVE     COMPLETED   CANCELLED
ACTIVE       (no-op)    cho phép    cho phép
COMPLETED    cho phép   (no-op)     KHÔNG  -> 409 INVALID_STATUS_TRANSITION
CANCELLED    cho phép   KHÔNG       (no-op)
```

**Membership:** `ACTIVE` ↔ `REMOVED` (dòng không bị xóa; được mời lại thì dùng lại dòng cũ, `removed_at = NULL`, tăng version).

**Invitation:** `PENDING` → `ACCEPTED` / `REJECTED` (Invitee) hoặc `CANCELLED` (Owner). Trạng thái cuối không đổi nữa.

---

## 3. Danh mục Use Case

### 3.1 Nhóm Project và Thành viên (mã `UC-P`)

| Mã | Tên Use Case | Actor | API | Liên hệ v1.2 |
| --- | --- | --- | --- | --- |
| UC-P01 | Tạo Project | User | `POST /api/v1/projects` | UC-05 |
| UC-P02 | Xem danh sách Project của tôi | User | `GET /api/v1/projects` | UC-06 |
| UC-P03 | Xem chi tiết Project | Owner, Member | `GET /api/v1/projects/{projectId}` | UC-06 |
| UC-P04 | Đổi tên Project | Owner | `PATCH /api/v1/projects/{projectId}` | UC-07 |
| UC-P05 | Đổi trạng thái / mở lại Project | Owner | `PATCH /api/v1/projects/{projectId}/status` | UC-07 |
| UC-P06 | Mời thành viên | Owner | `POST /api/v1/projects/{projectId}/invitations` | UC-08 |
| UC-P07 | Xem danh sách lời mời của Project | Owner | `GET /api/v1/projects/{projectId}/invitations` | Mục 9.2 |
| UC-P08 | Hủy lời mời | Owner | `POST /api/v1/projects/{projectId}/invitations/{invitationId}/cancel` | UC-10 |
| UC-P09 | Xem hộp thư lời mời của tôi | Invitee | `GET /api/v1/invitations/me` | UC-09 |
| UC-P10 | Chấp nhận lời mời | Invitee | `POST /api/v1/invitations/{invitationId}/accept` | UC-09 |
| UC-P11 | Từ chối lời mời | Invitee | `POST /api/v1/invitations/{invitationId}/reject` | UC-09 |
| UC-P12 | Xem danh sách thành viên | Owner, Member | `GET /api/v1/projects/{projectId}/members` | Mục 9.2 |
| UC-P13 | Loại thành viên | Owner | `DELETE /api/v1/projects/{projectId}/members/{userId}` | UC-11 |
| UC-P14 | Tự rời Project | Member | `POST /api/v1/projects/{projectId}/leave` | UC-11 |
| UC-P15 | Chuyển quyền Owner | Owner | `POST /api/v1/projects/{projectId}/transfer-ownership` | UC-12 |
| UC-P16 | Đồng bộ snapshot membership sang Redis | Hệ thống nền | (job nền, không có API) | UC-23 |
| UC-P17 | Xác minh membership cho Task Service | Task Service | `GET /internal/projects/{projectId}/members/{userId}` | Mục 9.6 |

### 3.2 Nhóm Board và Column (mã `UC-B`)

| Mã | Tên Use Case | Actor | API | Liên hệ v1.2 |
| --- | --- | --- | --- | --- |
| UC-B01 | Tạo Board | Owner | `POST /api/v1/projects/{projectId}/boards` | UC-13 |
| UC-B02 | Xem danh sách Board của Project | Owner, Member | `GET /api/v1/projects/{projectId}/boards` | Mới |
| UC-B03 | Xem chi tiết Board kèm Column | Owner, Member | `GET /api/v1/boards/{boardId}` | Mới |
| UC-B04 | Đổi tên Board | Owner | `PATCH /api/v1/boards/{boardId}` | UC-13 |
| UC-B05 | Đổi trạng thái Board | Owner | `PATCH /api/v1/boards/{boardId}/status` | UC-13 |
| UC-B06 | Tạo Column | Owner | `POST /api/v1/boards/{boardId}/columns` | UC-14 |
| UC-B07 | Đổi tên Column | Owner | `PATCH /api/v1/columns/{columnId}` | UC-14 |
| UC-B08 | Sắp xếp lại Column | Owner | `PUT /api/v1/boards/{boardId}/columns/order` | UC-14 |
| UC-B09 | Xóa Column | Owner | `DELETE /api/v1/columns/{columnId}` | UC-14 |
| UC-B10 | Xác minh Board và Column cho Task Service | Task Service | `GET /internal/boards/{boardId}` | Mục 9.6 |

> MVP **không có** use case xóa Board và xóa Project. Board không dùng nữa thì chuyển sang CANCELLED.

---

## 4. Quy tắc nghiệp vụ

### 4.1 Project và thành viên (từ tài liệu v1.2)

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-07 | Admin không mặc nhiên vào Project | SYSTEM_ADMIN chỉ có quyền khi là thành viên của Project đó. |
| BR-08 | Tạo Project | Mọi User ACTIVE được tạo. Trong **một transaction** tạo Project (ACTIVE), membership OWNER ACTIVE của creator và bản ghi version. Không được tồn tại Project không có Owner. |
| BR-09 | Một Owner | Tối đa một OWNER ACTIVE mỗi Project (unique index có điều kiện). Project role chỉ gồm OWNER, MEMBER. |
| BR-10 | Membership duy nhất | Mỗi cặp (project_id, user_id) một dòng. Rời/bị loại → `REMOVED` + `removed_at`; được thêm lại → dùng lại dòng, `ACTIVE`, `removed_at = NULL`, tăng version. |
| BR-11 | Thêm bằng lời mời | Chỉ vào Project qua lời mời. Chỉ Owner mời. Chỉ User ACTIVE đã tồn tại mới được mời. Tối đa một lời mời PENDING mỗi (project, user). |
| BR-12 | Phản hồi lời mời | Chỉ người được mời chấp nhận/từ chối; chỉ Owner hủy. Chấp nhận tạo hoặc kích hoạt lại membership role MEMBER. Trạng thái cuối không đổi. |
| BR-13 | Không mời trùng | Không mời người đang là thành viên ACTIVE hoặc đang có lời mời PENDING. Người từng REJECTED, CANCELLED, REMOVED được mời lại bằng lời mời mới. |
| BR-14 | Owner không rời | Owner không được rời hoặc bị loại; phải chuyển quyền Owner trước. |
| BR-15 | Chuyển Owner | Một transaction: hạ Owner cũ xuống MEMBER **trước**, nâng người nhận lên OWNER, tăng version của cả hai. Người nhận phải là MEMBER ACTIVE. |
| BR-16 | Rời / loại | Member tự rời, Owner loại Member; cả hai chuyển membership sang REMOVED và phát `MEMBER_REMOVED`. |
| BR-17 | Trạng thái Project | Chỉ khi Project ACTIVE mới được thay đổi dữ liệu. COMPLETED/CANCELLED chỉ đọc. Chỉ Owner đổi trạng thái Project; mở lại về ACTIVE là thao tác ghi duy nhất được phép khi Project không ACTIVE và phải phát `PROJECT_STATUS_CHANGED`. |
| BR-18 | Project chỉ có tên | `name` bắt buộc, tối đa 255 ký tự; chưa có mô tả. |

### 4.2 Board và Column

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-B01 | Quan hệ Board–Project | Mỗi Board thuộc đúng một Project; `project_id` không đổi. Backend luôn kiểm tra Board thuộc Project trong đường dẫn, không tin ID client khai. |
| BR-B02 | `board_key` | Duy nhất trong Project (`UQ_boards_project_key`), khác Project thì được trùng. Tối đa 20 ký tự. **(Đề xuất)** chữ thường, số, dấu gạch ngang, 2–20 ký tự, bất biến sau khi tạo. |
| BR-B03 | Trạng thái Board | ACTIVE, COMPLETED, CANCELLED. Chuyển hợp lệ: ACTIVE ↔ COMPLETED, ACTIVE ↔ CANCELLED. Không chuyển thẳng COMPLETED ↔ CANCELLED. Đặt lại đúng trạng thái hiện tại là no-op (200). |
| BR-B04 | Điều kiện được ghi | Mọi thao tác ghi cấu trúc cần **Project ACTIVE và Board ACTIVE**. Riêng đổi trạng thái Board chỉ cần Project ACTIVE. |
| BR-B05 | Chỉ Owner đổi cấu trúc | Tạo/sửa Board, đổi trạng thái Board và mọi thao tác Column chỉ dành cho Owner. |
| BR-B06 | Thứ tự Column | `position ≥ 0`, duy nhất trong Board. Sau mỗi thao tác, position liên tục từ 0 đến n−1. |
| BR-B07 | Đồng thời | Mọi thao tác thay đổi Column chạy trong một transaction và khóa dòng `boards` tương ứng. |
| BR-B08 | Xóa Column | Chỉ xóa được Column không còn Task. Số Task do Task Service trả lời; không hỏi được thì **từ chối xóa** (fail-closed). |
| BR-B09 | Không xóa Board | MVP không có DELETE trên `boards`; dùng CANCELLED. |
| BR-B10 | Che giấu tài nguyên | Không có membership ACTIVE → 404 (không phải 403). Member thiếu quyền → 403. |
| BR-B11 | Cột mặc định | Khi tạo Board có thể sinh sẵn 3 cột theo cấu hình (mặc định Todo, In Progress, Done), position 0, 1, 2. |
| BR-B12 | Thời gian | `created_at`, `updated_at` lưu UTC (`DATETIMEOFFSET`). `board_columns` không có cột thời gian. |
| BR-B13 | Idempotency | Tạo Board và tạo Column hỗ trợ `Idempotency-Key`. |

### 4.3 Phân quyền, sự kiện và nhất quán (dùng chung)

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-36 | Outbox / Inbox | Sự kiện ghi vào `outbox_events` trong cùng transaction với thay đổi dữ liệu. Consumer ghi `inbox_events` theo (consumer_name, event_id). Retry bằng `next_attempt_at`. |
| BR-38 | Backend quyết định | Project Service tự kiểm tra quyền; Gateway không phải lớp bảo vệ duy nhất. |
| BR-39 | Snapshot Redis có version | Quyền kiểm tra qua snapshot Redis. Không bao giờ ghi snapshot version thấp đè version cao. |
| BR-40 | Không tin ID từ client | `actorId` lấy từ token; không nhận `actorId`, `role` từ client. |
| BR-41 | Chống dò tài nguyên | Không có quyền xem → 404; có quyền xem nhưng thiếu quyền thao tác → 403. |
| BR-42 | Idempotency | API tạo mới có thể retry (lời mời, Board, Column) hỗ trợ `Idempotency-Key` hoặc phát hiện trùng. |

---

## 5. Chuỗi kiểm tra chuẩn

Mọi API công khai của Project Service chạy các bước sau theo thứ tự. Các use case bên dưới chỉ nhắc tên bước (C1…C6).

| Bước | Nội dung | Kết quả khi không đạt |
| --- | --- | --- |
| C1 | Gateway xác thực token; lấy `actorId` từ principal; kiểm tra `auth_version`. | 401 `UNAUTHENTICATED` |
| C2 | Nạp tài nguyên từ đường dẫn (Project, Board hoặc Column), suy ra Project, kiểm tra membership ACTIVE của actor. | 404 `RESOURCE_NOT_FOUND` (cả khi không tồn tại lẫn khi actor không được xem) |
| C3 | Kiểm tra role theo ma trận mục 2.1. | 403 `FORBIDDEN` |
| C4 | Kiểm tra trạng thái: Project ACTIVE; Board ACTIVE (nếu thao tác cần). | 409 `PROJECT_NOT_ACTIVE` / `BOARD_NOT_ACTIVE` |
| C5 | Validate dữ liệu đầu vào. | 400 `VALIDATION_ERROR` |
| C6 | Kiểm tra ràng buộc nghiệp vụ và duy nhất; thực hiện thay đổi trong transaction. | 409 / 422 / 503 theo từng use case |

Role và membership đọc từ **snapshot Redis**; nếu không có snapshot thì đọc trực tiếp `project_memberships` (cùng database nên không tốn thêm lời gọi liên service). Thao tác nhạy cảm có thể đọc thẳng database thay vì tin snapshot, vì snapshot có độ trễ ngắn.

Các use case dành cho **Invitee** (UC-P09, P10, P11) và **tạo Project** (UC-P01) và **danh sách Project của tôi** (UC-P02) chỉ chạy C1, C5, C6 cùng các kiểm tra riêng nêu trong từng use case.

Cấu trúc lỗi chuẩn:

```json
{
  "error": { "code": "PROJECT_NOT_ACTIVE", "message": "Project is not active and is read-only.",
             "details": [ { "field": "projectId", "reason": "Mutations are not allowed." } ] },
  "meta": { "correlationId": "1d0c...", "timestamp": "2026-11-07T03:50:00Z" }
}
```

---

## 6. Đặc tả Use Case — nhóm Project và Thành viên

### UC-P01 — Tạo Project

| | |
| --- | --- |
| **Actor** | User |
| **Mô tả** | User tạo không gian làm việc mới và tự thành Owner. |
| **Điều kiện trước** | Tài khoản ACTIVE, đã đăng nhập. |
| **Điều kiện sau** | Có Project ACTIVE, membership OWNER ACTIVE của creator, bản ghi version khởi tạo và job đồng bộ snapshot; hoặc rollback toàn bộ. |
| **Quy tắc** | BR-08, BR-09, BR-18, BR-B11 |
| **API** | `POST /api/v1/projects` → 201 Created, header `Location` |

**Luồng chính**

1. User nhập tên Project.
2. Hệ thống chạy C1; lấy `creatorId` từ token.
3. Validate `name` sau khi cắt khoảng trắng có 1–255 ký tự.
4. Mở **một transaction** và ghi:
   - `projects` (`status = ACTIVE`, `created_by_user_id`, `created_at = updated_at = now` UTC);
   - `project_memberships` (`role = OWNER`, `status = ACTIVE`, `joined_at = now`);
   - `project_membership_versions` (`membership_version = 1`, `authorization_version = 1`, `row_version = 0`);
   - `project_authorization_snapshot_sync_jobs` (kèm `snapshot_json`, `operation_id`).
5. Tùy cấu hình: tạo Board mặc định cùng các cột mặc định (cùng transaction).
6. Commit, trả Project kèm `myRole = OWNER`.

**Luồng thay thế**

- **Gửi lại cùng `Idempotency-Key`** (đề xuất): trả lại kết quả lần đầu, không tạo Project thứ hai.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Tên rỗng hoặc quá dài | 400 | `VALIDATION_ERROR` |
| Bất kỳ bước nào trong transaction lỗi | — | Rollback toàn bộ; không để Project không có Owner |

**Dữ liệu và sự kiện:** ghi `projects`, `project_memberships`, `project_membership_versions`, `project_authorization_snapshot_sync_jobs` (và `boards`, `board_columns` nếu tạo Board mặc định). Không phát sự kiện RabbitMQ.

```json
POST /api/v1/projects
{ "name": "Kanban MVP" }

201 Created   Location: /api/v1/projects/1
{ "data": { "id": 1, "name": "Kanban MVP", "status": "ACTIVE",
            "createdByUserId": "e7b3a2c1-3094-4f3b-910a-7e82845e0b2d",
            "myRole": "OWNER", "createdAt": "2026-11-07T03:50:00Z" },
  "meta": { "correlationId": "1d0c..." } }
```

---

### UC-P02 — Xem danh sách Project của tôi

| | |
| --- | --- |
| **Actor** | User |
| **Mô tả** | Chỉ thấy Project mà mình có membership ACTIVE. |
| **Điều kiện trước** | Đã đăng nhập. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **Quy tắc** | BR-07, BR-38, BR-41 |
| **API** | `GET /api/v1/projects?page=&size=` (có thể thêm `status=`, `sort=` — đề xuất) |

**Luồng chính**

1. Hệ thống chạy C1; lấy `userId` từ token.
2. Validate tham số phân trang (`size` không vượt giới hạn tối đa).
3. Truy vấn Project có membership ACTIVE của user (dùng được chỉ mục `IX_project_memberships_user_status_project`).
4. Trả mỗi Project gồm `id`, `name`, `status`, `myRole` cùng thông tin phân trang.

**Luồng thay thế**

- **Chưa tham gia Project nào:** trả danh sách rỗng (200).
- Project mà user đã bị loại (REMOVED) **không** xuất hiện.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| `page`, `size` hoặc `sort` không hợp lệ | 400 | `VALIDATION_ERROR` |

**Dữ liệu và sự kiện:** chỉ đọc `projects`, `project_memberships`. System Admin cũng chỉ thấy Project của chính mình.

---

### UC-P03 — Xem chi tiết Project

| | |
| --- | --- |
| **Actor** | Owner, Member |
| **Mô tả** | Xem thông tin Project, trạng thái và vai trò của mình. |
| **Điều kiện trước** | Membership ACTIVE. Project ở trạng thái nào cũng xem được. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **Quy tắc** | BR-38, BR-41 |
| **API** | `GET /api/v1/projects/{projectId}` |

**Luồng chính**

1. Hệ thống chạy C1–C2.
2. Trả `id`, `name`, `status`, `createdByUserId`, `createdAt`, `updatedAt`, `myRole`.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE (kể cả người đã bị loại) | 404 | `RESOURCE_NOT_FOUND` |

---

### UC-P04 — Đổi tên Project

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Owner sửa tên Project. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE. |
| **Điều kiện sau** | `projects.name`, `updated_at` được cập nhật. |
| **Quy tắc** | BR-17, BR-18, BR-38 |
| **API** | `PATCH /api/v1/projects/{projectId}` — body `{ "name": "..." }` |

**Luồng chính**

1. Hệ thống chạy C1–C4 (Owner, Project ACTIVE).
2. Validate `name` 1–255 ký tự sau khi cắt khoảng trắng.
3. Cập nhật `name`, `updated_at = now` (UTC); trả Project mới.

**Luồng thay thế**

- **Tên mới giống tên cũ:** coi như không đổi, vẫn 200 (đề xuất).
- Body chứa `status`, `createdByUserId`, `id`… (trường không được phép): 400 `VALIDATION_ERROR` (BR-03, BR-40).

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Tên rỗng/quá dài; body chứa trường không được sửa | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |

**Dữ liệu và sự kiện:** ghi `projects.name`, `projects.updated_at`. Không phát sự kiện.

---

### UC-P05 — Đổi trạng thái / mở lại Project

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Đóng Project (COMPLETED hoặc CANCELLED) hoặc mở lại về ACTIVE. |
| **Điều kiện trước** | Owner ACTIVE. Trạng thái nguồn hợp lệ theo mục 2.3. |
| **Điều kiện sau** | `projects.status`, `updated_at` được cập nhật; `PROJECT_STATUS_CHANGED` được ghi vào Outbox. Project không ACTIVE trở thành chỉ đọc cho mọi service. |
| **Quy tắc** | BR-17, BR-38 |
| **API** | `PATCH /api/v1/projects/{projectId}/status` — body `{ "status": "COMPLETED" }` |

**Luồng chính**

1. Hệ thống chạy C1–C3. **Không** áp C4 (đây chính là thao tác đổi trạng thái; mở lại là thao tác ghi duy nhất được phép khi Project không ACTIVE).
2. Validate `status` thuộc ACTIVE / COMPLETED / CANCELLED.
3. Kiểm tra chuyển trạng thái theo bảng ở mục 2.3.
4. Trong một transaction: cập nhật `status`, `updated_at`; ghi `outbox_events` loại `PROJECT_STATUS_CHANGED` (`projectId`, `status`, `previousStatus`).
5. Trả Project mới.

**Luồng thay thế**

- **Đặt đúng trạng thái hiện tại:** no-op, 200, không phát sự kiện (đề xuất, thống nhất với BR-B03).
- **Mở lại Project:** các Board bên trong **giữ nguyên** trạng thái; Owner mở lại từng Board khi cần (UC-B05).

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| `status` không thuộc tập giá trị | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Chuyển thẳng COMPLETED ↔ CANCELLED | 409 | `INVALID_STATUS_TRANSITION` |

**Dữ liệu và sự kiện:** ghi `projects.status`, `projects.updated_at`, `outbox_events` (`PROJECT_STATUS_CHANGED`, kể cả khi mở lại về ACTIVE). Task Service và Notification Service là bên nhận.

---

### UC-P06 — Mời thành viên

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Gửi lời mời cho một User đã tồn tại vào Project. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; người được mời tồn tại và đang hoạt động. |
| **Điều kiện sau** | Có một lời mời PENDING; Invitee thấy lời mời trong hộp thư. |
| **Quy tắc** | BR-11, BR-13, BR-42 |
| **API** | `POST /api/v1/projects/{projectId}/invitations` — body `{ "userId": "..." }` (hoặc `email`) → 201 |

**Luồng chính**

1. Owner chọn người dùng theo `userId` hoặc email.
2. Hệ thống chạy C1–C4.
3. Gọi User Service (`GET /internal/users/{userId}`; nếu nhập email thì tra ra userId trước) để xác minh tài khoản tồn tại và đang hoạt động.
4. Kiểm tra người đó chưa là thành viên ACTIVE và chưa có lời mời PENDING.
5. Tạo `invitations` (`status = PENDING`, `invited_by_user_id = actorId`, `created_at = updated_at = now`). Unique index `UX_invitations_pending_unique` chặn trùng PENDING kể cả khi hai request chạy đồng thời (bắt lỗi vi phạm từ DB để trả 409 thay vì 500).
6. (Tùy chọn) ghi Outbox `INVITATION_CREATED` để Notification Service tạo thông báo.
7. Trả lời mời vừa tạo.

**Luồng thay thế**

- **Mời lại người từng REJECTED, CANCELLED hoặc REMOVED:** được, bằng lời mời mới (BR-13).
- **Gửi lại cùng `Idempotency-Key`:** trả kết quả lần đầu.
- **Owner tự mời mình:** bị chặn bởi quy tắc "đã là thành viên ACTIVE".

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Thiếu/sai `userId`, `email` | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| Đã là thành viên ACTIVE | 409 | `MEMBERSHIP_ALREADY_EXISTS` |
| Đã có lời mời PENDING | 409 | `INVITATION_ALREADY_PENDING` |
| Người được mời không tồn tại hoặc bị khóa | 422 | `INVITEE_NOT_AVAILABLE` |
| Không hỏi được User Service (đề xuất, fail-closed) | 503 | `DEPENDENCY_UNAVAILABLE` |

**Dữ liệu và sự kiện:** ghi `invitations` (1 dòng), `outbox_events` (tùy chọn `INVITATION_CREATED`: `invitationId`, `projectId`, `inviteeUserId`, `invitedByUserId`).

---

### UC-P07 — Xem danh sách lời mời của Project

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Owner xem các lời mời đã gửi, lọc theo trạng thái. |
| **Điều kiện trước** | Owner ACTIVE. Project ở trạng thái nào cũng xem được. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **Quy tắc** | BR-12, BR-41 |
| **API** | `GET /api/v1/projects/{projectId}/invitations?status=&page=&size=` |

**Luồng chính**

1. Hệ thống chạy C1–C3.
2. Validate `status` thuộc PENDING / ACCEPTED / REJECTED / CANCELLED nếu có.
3. Truy vấn theo `project_id` (dùng index `IX_invitations_project_status_created`), sắp xếp mới nhất trước, phân trang.
4. Trả `id`, `userId`, `invitedByUserId`, `status`, `createdAt`, `respondedAt`.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Tham số không hợp lệ | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |

---

### UC-P08 — Hủy lời mời

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Thu hồi lời mời chưa được phản hồi. |
| **Điều kiện trước** | Owner ACTIVE; lời mời PENDING thuộc Project của Owner. |
| **Điều kiện sau** | Lời mời `CANCELLED`, `updated_at` cập nhật. |
| **Quy tắc** | BR-12 |
| **API** | `POST /api/v1/projects/{projectId}/invitations/{invitationId}/cancel` |

**Luồng chính**

1. Hệ thống chạy C1–C3 (có thể áp C4: Project ACTIVE — theo bảng 6.3 của v1.2, thao tác về lời mời bị chặn khi Project không ACTIVE).
2. Nạp lời mời theo `invitationId` **và** `projectId` trong đường dẫn (lời mời thuộc Project khác coi như không tồn tại).
3. Kiểm tra lời mời đang PENDING.
4. Đặt `CANCELLED`, ghi `updated_at`; trả lời mời.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Actor là Member | 403 | `FORBIDDEN` |
| Lời mời không tồn tại / không thuộc Project / actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| Lời mời không còn PENDING | 409 | `INVITATION_NOT_PENDING` |

---

### UC-P09 — Xem hộp thư lời mời của tôi

| | |
| --- | --- |
| **Actor** | Invitee (mọi User đã đăng nhập) |
| **Mô tả** | Xem các lời mời gửi cho mình. |
| **Điều kiện trước** | Đã đăng nhập. Không phụ thuộc membership. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **Quy tắc** | BR-12, BR-40 |
| **API** | `GET /api/v1/invitations/me?status=&page=&size=` |

**Luồng chính**

1. Hệ thống chạy C1; lấy `userId` từ token (không nhận userId từ client).
2. Validate `status` nếu có.
3. Truy vấn theo `user_id` (dùng index `IX_invitations_user_status_created`), mới nhất trước.
4. Trả `id`, `projectId`, tên Project, `invitedByUserId`, `status`, `createdAt`.

**Luồng thay thế:** không có lời mời → danh sách rỗng (200).

**Ngoại lệ:** tham số không hợp lệ → 400 `VALIDATION_ERROR`.

---

### UC-P10 — Chấp nhận lời mời

| | |
| --- | --- |
| **Actor** | Invitee |
| **Mô tả** | Tham gia Project với role MEMBER. |
| **Điều kiện trước** | Có lời mời PENDING gửi cho mình; Project ACTIVE; tài khoản hoạt động. |
| **Điều kiện sau** | Lời mời `ACCEPTED`; có membership MEMBER ACTIVE; version tăng; có job đồng bộ snapshot. |
| **Quy tắc** | BR-10, BR-12, BR-39 |
| **API** | `POST /api/v1/invitations/{invitationId}/accept` |

**Luồng chính**

1. Hệ thống chạy C1; nạp lời mời theo `invitationId` và kiểm tra `user_id` của lời mời bằng `actorId`.
2. Kiểm tra lời mời đang PENDING và Project ACTIVE.
3. Mở **một transaction**:
   1. Đặt lời mời `ACCEPTED`, ghi `responded_at`, `updated_at`.
   2. Tạo membership (`MEMBER`, `ACTIVE`) hoặc, nếu đã có dòng `REMOVED`, kích hoạt lại: `status = ACTIVE`, `removed_at = NULL`, `updated_at = now`.
   3. Tăng `membership_version`, `authorization_version` trong `project_membership_versions`, khóa lạc quan bằng `row_version`.
   4. Tạo job đồng bộ snapshot (`project_authorization_snapshot_sync_jobs`).
   5. Ghi Outbox `MEMBER_ADDED` (`projectId`, `userId`, `membershipVersion`).
4. Commit, trả membership mới.

**Luồng thay thế**

- **Hai request đồng thời:** chỉ một thành công (PK membership và `row_version`); request còn lại nhận 409.
- Sau khi snapshot Redis cập nhật, Member mới mới truy cập được Project (có độ trễ ngắn).

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Lời mời không phải của mình hoặc không tồn tại | 404 | `RESOURCE_NOT_FOUND` |
| Lời mời không còn PENDING | 409 | `INVITATION_NOT_PENDING` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| Xung đột đồng thời (`row_version`) | 409 | `VERSION_CONFLICT` |

**Dữ liệu và sự kiện:** ghi `invitations`, `project_memberships`, `project_membership_versions`, `project_authorization_snapshot_sync_jobs`, `outbox_events` (`MEMBER_ADDED`).

---

### UC-P11 — Từ chối lời mời

| | |
| --- | --- |
| **Actor** | Invitee |
| **Mô tả** | Từ chối tham gia Project. |
| **Điều kiện trước** | Có lời mời PENDING gửi cho mình; Project ACTIVE. |
| **Điều kiện sau** | Lời mời `REJECTED`, `responded_at` được ghi; không có membership. |
| **Quy tắc** | BR-12 |
| **API** | `POST /api/v1/invitations/{invitationId}/reject` |

**Luồng chính**

1. Hệ thống chạy C1; xác minh lời mời thuộc về actor.
2. Kiểm tra lời mời PENDING và Project ACTIVE.
3. Đặt `REJECTED`, ghi `responded_at`, `updated_at`; trả lời mời.

**Ngoại lệ:** giống UC-P10 (404 `RESOURCE_NOT_FOUND`, 409 `INVITATION_NOT_PENDING`, 409 `PROJECT_NOT_ACTIVE`). Không phát sự kiện, không đụng version.

---

### UC-P12 — Xem danh sách thành viên

| | |
| --- | --- |
| **Actor** | Owner, Member |
| **Mô tả** | Xem các thành viên hiện tại của Project. |
| **Điều kiện trước** | Membership ACTIVE. Project ở trạng thái nào cũng xem được. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **Quy tắc** | BR-41 |
| **API** | `GET /api/v1/projects/{projectId}/members?page=&size=` |

**Luồng chính**

1. Hệ thống chạy C1–C2.
2. Truy vấn `project_memberships` có `status = ACTIVE` của Project; phân trang.
3. Trả `userId`, `role`, `joinedAt`. Tên hiển thị lấy từ User Service qua `/internal/users/{userId}` (đề xuất; nếu không hỏi được thì trả thiếu tên, không làm hỏng cả danh sách).

**Ngoại lệ:** Project không tồn tại hoặc actor không phải thành viên ACTIVE → 404 `RESOURCE_NOT_FOUND`.

> Thành viên `REMOVED` không xuất hiện trong danh sách.

---

### UC-P13 — Loại thành viên

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Thu hồi quyền truy cập Project của một Member. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; target có membership ACTIVE và không phải Owner. |
| **Điều kiện sau** | Membership `REMOVED`; `MEMBER_REMOVED` được ghi Outbox; assignment được gỡ bất đồng bộ ở Task Service; Task và bình luận được giữ. |
| **Quy tắc** | BR-10, BR-14, BR-16, BR-30, BR-31 |
| **API** | `DELETE /api/v1/projects/{projectId}/members/{userId}` → 204 |

**Luồng chính**

1. Hệ thống chạy C1–C4.
2. Kiểm tra target có membership ACTIVE và không phải Owner.
3. Mở **một transaction**:
   1. Membership → `REMOVED`, ghi `removed_at`, `updated_at`.
   2. Tăng `membership_version`, `authorization_version` (khóa lạc quan `row_version`).
   3. Tạo job đồng bộ snapshot.
   4. Đọc danh sách **mọi Board của Project** từ bảng `boards` (cùng database) và ghi Outbox `MEMBER_REMOVED` (`projectId`, `userId`, `membershipVersion`, `boardIds`, `operationId`, `sourceAggregateVersion`).
4. Commit, trả 204. Sau khi snapshot cập nhật, request mới của target bị từ chối.
5. Task Service nhận sự kiện và dọn assignment (ngoài phạm vi tài liệu này).

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Actor là Member | 403 | `FORBIDDEN` |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Target không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Target là Owner | 409 | `OWNER_CANNOT_LEAVE` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| Xung đột đồng thời (`row_version`) | 409 | `VERSION_CONFLICT` |

**Dữ liệu và sự kiện:** ghi `project_memberships`, `project_membership_versions`, `project_authorization_snapshot_sync_jobs`, `outbox_events` (`MEMBER_REMOVED`). Rollback thì không có sự kiện (AC-23).

---

### UC-P14 — Tự rời Project

| | |
| --- | --- |
| **Actor** | Member |
| **Mô tả** | Member chủ động rời Project. |
| **Điều kiện trước** | Membership ACTIVE; Project ACTIVE; actor không phải Owner. |
| **Điều kiện sau** | Giống UC-P13 với target là chính actor. |
| **Quy tắc** | BR-10, BR-14, BR-16, BR-30, BR-31 |
| **API** | `POST /api/v1/projects/{projectId}/leave` → 204 |

**Luồng chính:** như UC-P13 (bước 3–5) nhưng target là `actorId` lấy từ token.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Actor là Owner (phải chuyển quyền trước) | 409 | `OWNER_CANNOT_LEAVE` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| Xung đột đồng thời | 409 | `VERSION_CONFLICT` |

---

### UC-P15 — Chuyển quyền Owner

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Trao quyền sở hữu Project cho một Member. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; người nhận là MEMBER ACTIVE. |
| **Điều kiện sau** | Người nhận là OWNER duy nhất; Owner cũ thành MEMBER; version của cả hai tăng; có job snapshot cho cả hai. |
| **Quy tắc** | BR-09, BR-15 |
| **API** | `POST /api/v1/projects/{projectId}/transfer-ownership` — body `{ "newOwnerUserId": "..." }` |

**Luồng chính**

1. Owner chọn người nhận và xác nhận.
2. Hệ thống chạy C1–C4.
3. Kiểm tra người nhận là MEMBER ACTIVE và khác chính Owner.
4. Mở **một transaction**, theo đúng thứ tự:
   1. Hạ Owner cũ xuống `MEMBER` **trước** (để không vi phạm `UX_project_memberships_one_owner`);
   2. Nâng người nhận lên `OWNER`;
   3. Tăng `membership_version`, `authorization_version` của cả hai (khóa lạc quan `row_version`);
   4. Tạo job đồng bộ snapshot cho cả hai người;
   5. Ghi Outbox `OWNER_TRANSFERRED` (`projectId`, `fromUserId`, `toUserId`).
5. Commit, trả Project với `myRole = MEMBER` của actor cũ.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Thiếu `newOwnerUserId`; trùng chính mình (đề xuất) | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| Người nhận không phải thành viên ACTIVE | 422 | `TARGET_NOT_PROJECT_MEMBER` |
| Xung đột đồng thời (`row_version`); client tải lại và thử lại | 409 | `VERSION_CONFLICT` |

---

### UC-P16 — Đồng bộ snapshot membership sang Redis

| | |
| --- | --- |
| **Actor** | Hệ thống nền (Project Service) |
| **Mô tả** | Đưa thay đổi quyền membership từ database sang Redis một cách tin cậy. |
| **Điều kiện trước** | Có dòng `project_authorization_snapshot_sync_jobs` đến hạn (`status`, `next_attempt_at`). |
| **Điều kiện sau** | Redis chứa snapshot đúng version mới nhất, hoặc job được hẹn thử lại. |
| **Quy tắc** | BR-39 |

**Luồng chính**

1. Worker quét job theo (`status`, `next_attempt_at`) — dùng index `IX_snapshot_sync_jobs_status_next_attempt`. Nếu chạy nhiều instance, dùng `UPDLOCK, READPAST` để không lấy trùng dòng (đề xuất).
2. Đọc `snapshot_json` đã lưu của job, ghi vào Redis **chỉ khi** `authorization_version` mới hơn version đang có.
3. Thành công: đánh dấu job hoàn tất.
4. Lỗi: tăng `attempt_count`, ghi `last_error`, đặt `next_attempt_at` theo backoff.

**Ngoại lệ**

- **Redis tạm thời lỗi:** job được thử lại; thay đổi quyền không bị mất.
- **Job chạy lại hoặc đến muộn:** không làm giảm version đã có trong Redis.
- **Redis không có snapshot khi kiểm tra quyền (miss/hết TTL):** gọi lại database của chính service rồi nạp lại Redis; lỗi thì từ chối (fail-closed) — đề xuất cho quyết định mở số 5.

**Dữ liệu:** đọc/ghi `project_authorization_snapshot_sync_jobs`; unique `operation_id` và (`project_id`, `user_id`, `authorization_version`) chống tạo job trùng.

---

### UC-P17 — Xác minh membership cho Task Service

| | |
| --- | --- |
| **Actor** | Task Service |
| **Mô tả** | Task Service xác minh một User có phải thành viên ACTIVE của Project (ví dụ khi gán Task) và lấy `membership_version` để ghi vào `task_assignees`. API **nội bộ**, không công khai qua Gateway. |
| **Điều kiện trước** | Lời gọi mang service credential hợp lệ. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **API** | `GET /internal/projects/{projectId}/members/{userId}` |

**Luồng chính**

1. Xác thực service credential.
2. Đọc `project_memberships`, `project_membership_versions`, `projects`.
3. Trả `role`, `status`, `membershipVersion`, `projectStatus`.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Thiếu hoặc sai service credential | 401 | `UNAUTHENTICATED` |
| Project hoặc membership không tồn tại | 404 | `RESOURCE_NOT_FOUND` |
| Project Service quá tải/không sẵn sàng | 503 | `DEPENDENCY_UNAVAILABLE` |

---

## 7. Đặc tả Use Case — nhóm Board và Column

### UC-B01 — Tạo Board

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Tạo Board mới trong Project để tổ chức công việc theo cột (ví dụ một Sprint). |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE. |
| **Điều kiện sau** | Một dòng `boards` (`status = ACTIVE`, `created_by_user_id = Owner`) và, nếu bật cột mặc định, 3 dòng `board_columns`, tất cả trong một transaction. |
| **Quy tắc** | BR-B01, B02, B04, B05, B11, B12, B13 |
| **API** | `POST /api/v1/projects/{projectId}/boards` → 201, header `Location` |

**Luồng chính**

1. Owner nhập tên Board, `boardKey` và chọn có sinh cột mặc định hay không (mặc định: có).
2. Hệ thống chạy C1–C4.
3. Validate: `name` sau khi cắt khoảng trắng có 1–255 ký tự; `boardKey` đúng định dạng (BR-B02).
4. Mở transaction: thêm `boards` (`status = ACTIVE`, `created_at = updated_at = now` UTC).
5. Nếu bật cột mặc định: thêm các cột theo cấu hình với position 0, 1, 2.
6. Commit, trả Board kèm danh sách Column đã tạo.

**Luồng thay thế**

- **Tắt cột mặc định:** Board không có cột; Owner tự tạo ở UC-B06. Board chưa có cột thì chưa tạo được Task.
- **Gửi lại cùng `Idempotency-Key`:** trả kết quả lần đầu, không tạo Board thứ hai.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Thiếu/sai `name` hoặc `boardKey` | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| `boardKey` đã có trong Project (kể cả khi hai request đồng thời: bắt lỗi vi phạm `UQ_boards_project_key` từ DB, không chỉ kiểm tra trước) | 409 | `BOARD_KEY_ALREADY_EXISTS` |

**Dữ liệu và sự kiện:** ghi `boards` (1 dòng), `board_columns` (0 hoặc 3 dòng). Không phát sự kiện.

```json
POST /api/v1/projects/1/boards
{ "name": "Sprint 2 - Hoàn thiện MVP", "boardKey": "sprint-2", "createDefaultColumns": true }

201 Created   Location: /api/v1/boards/2
{ "data": { "id": 2, "projectId": 1, "name": "Sprint 2 - Hoàn thiện MVP", "boardKey": "sprint-2",
            "status": "ACTIVE", "createdAt": "2026-11-07T03:50:00Z",
            "columns": [ { "id": 4, "name": "Todo", "position": 0 },
                         { "id": 5, "name": "In Progress", "position": 1 },
                         { "id": 6, "name": "Done", "position": 2 } ] } }
```

---

### UC-B02 — Xem danh sách Board của Project

| | |
| --- | --- |
| **Actor** | Owner, Member |
| **Mô tả** | Xem các Board của Project để chọn Board làm việc. |
| **Điều kiện trước** | Membership ACTIVE. Project ở trạng thái nào cũng xem được. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **Quy tắc** | BR-B10 |
| **API** | `GET /api/v1/projects/{projectId}/boards?status=&page=&size=&sort=` |

**Luồng chính**

1. Hệ thống chạy C1–C2.
2. Validate: `status` thuộc ACTIVE/COMPLETED/CANCELLED nếu có; `size` không vượt tối đa; `sort` thuộc tập cho phép (mặc định `createdAt,desc`).
3. Truy vấn `boards` theo `project_id`, lọc `status` nếu có, phân trang.
4. Trả mỗi Board gồm `id`, `name`, `boardKey`, `status`, `createdAt`, `columnCount` cùng thông tin phân trang.

**Luồng thay thế:** Project chưa có Board → danh sách rỗng (200).

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| `status`, `size` hoặc `sort` không hợp lệ | 400 | `VALIDATION_ERROR` |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE (kể cả người đã bị loại) | 404 | `RESOURCE_NOT_FOUND` |

---

### UC-B03 — Xem chi tiết Board kèm Column

| | |
| --- | --- |
| **Actor** | Owner, Member |
| **Mô tả** | Lấy cấu trúc Board (thông tin Board và các Column theo thứ tự) để dựng màn hình Kanban. Task thuộc Task Service nên **không** nằm trong phản hồi này. |
| **Điều kiện trước** | Membership ACTIVE trong Project chứa Board. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **Quy tắc** | BR-B01, B06, B10 |
| **API** | `GET /api/v1/boards/{boardId}` |

**Luồng chính**

1. Hệ thống nạp Board theo `boardId`, suy ra Project, chạy C1–C2.
2. Đọc các Column của Board, sắp xếp theo `position` tăng dần.
3. Tính cờ `writable = (Project ACTIVE và Board ACTIVE)` và `myRole` để giao diện biết nên ẩn/khóa nút nào.
4. Trả Board, `writable`, `myRole` và danh sách Column.
5. Frontend gọi tiếp Task Service (`GET /api/v1/boards/{boardId}/tasks`) và ghép Task vào từng Column theo `columnId`.

**Luồng thay thế**

- **Task Service lỗi hoặc chậm:** cấu trúc Board vẫn hiển thị; vùng Task báo lỗi và cho tải lại.
- **Task mang `columnId` không còn trong danh sách Column (dữ liệu mồ côi, rủi ro R1):** frontend không hiển thị Task đó và ghi log.

**Ngoại lệ:** Board không tồn tại hoặc actor không phải thành viên ACTIVE → 404 `RESOURCE_NOT_FOUND`.

```json
200 OK
{ "data": { "id": 1, "projectId": 1, "name": "Sprint 1 - Core Features", "boardKey": "sprint-1",
            "status": "ACTIVE", "writable": true, "myRole": "MEMBER",
            "columns": [ { "id": 1, "name": "Cần làm (Todo)", "position": 0 },
                         { "id": 2, "name": "Đang xử lý (In Progress)", "position": 1 },
                         { "id": 3, "name": "Hoàn thành (Done)", "position": 2 } ] } }
```

---

### UC-B04 — Đổi tên Board

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Sửa tên hiển thị của Board. `boardKey` không đổi. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; Board ACTIVE. |
| **Điều kiện sau** | `boards.name`, `updated_at` được cập nhật. |
| **Quy tắc** | BR-B02, B04, B05, B12 |
| **API** | `PATCH /api/v1/boards/{boardId}` — body `{ "name": "..." }` |

**Luồng chính**

1. Hệ thống chạy C1–C4.
2. Validate `name` sau khi cắt khoảng trắng có 1–255 ký tự.
3. Cập nhật `name`, `updated_at = now` (UTC); trả Board.

**Luồng thay thế:** tên mới giống tên cũ → coi như không đổi, vẫn 200.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Tên rỗng/quá dài; body chứa `boardKey` khác giá trị hiện tại | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| Board COMPLETED/CANCELLED | 409 | `BOARD_NOT_ACTIVE` |

---

### UC-B05 — Đổi trạng thái Board

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Đóng Board (COMPLETED/CANCELLED) khi xong việc/hủy, hoặc mở lại về ACTIVE. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; Board ở trạng thái nguồn hợp lệ. |
| **Điều kiện sau** | `boards.status`, `updated_at` được cập nhật. Board không ACTIVE chỉ đọc với cả Project Service và Task Service. Dữ liệu Task, Column giữ nguyên. |
| **Quy tắc** | BR-B03, B04, B05 |
| **API** | `PATCH /api/v1/boards/{boardId}/status` — body `{ "status": "COMPLETED" }` |

**Luồng chính**

1. Hệ thống chạy C1–C3, rồi C4 chỉ kiểm tra **Project ACTIVE** (không kiểm tra trạng thái Board).
2. Validate `status` thuộc ACTIVE/COMPLETED/CANCELLED.
3. Kiểm tra chuyển trạng thái theo bảng mục 2.3.
4. Cập nhật `status`, `updated_at`; trả Board.
5. Từ lần ghi Task kế tiếp, Task Service đọc trạng thái mới qua UC-B10 và từ chối nếu Board không ACTIVE. Không cần sự kiện.

**Luồng thay thế**

- **Đặt đúng trạng thái hiện tại:** no-op, 200.
- **Mở lại Board khi Project đã COMPLETED/CANCELLED:** bị chặn; Owner phải mở lại Project trước (UC-P05).

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| `status` không thuộc tập giá trị | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project COMPLETED/CANCELLED | 409 | `PROJECT_NOT_ACTIVE` |
| Chuyển thẳng COMPLETED ↔ CANCELLED | 409 | `INVALID_STATUS_TRANSITION` |

**Dữ liệu và sự kiện:** ghi `boards.status`, `boards.updated_at`. Không phát sự kiện (Task Service **kéo** trạng thái khi cần). `BOARD_STATUS_CHANGED` là tùy chọn, đề xuất, không bắt buộc.

---

### UC-B06 — Tạo Column

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Thêm cột mới vào Board, ở cuối hoặc ở vị trí chỉ định. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; Board ACTIVE. |
| **Điều kiện sau** | Một dòng `board_columns` mới; position của Board vẫn liên tục 0…n−1. |
| **Quy tắc** | BR-B04, B05, B06, B07, B13 |
| **API** | `POST /api/v1/boards/{boardId}/columns` — body `{ "name": "Review", "position": 2 }` (`position` tùy chọn) → 201 |

**Luồng chính**

1. Hệ thống chạy C1–C4.
2. Validate: `name` 1–100 ký tự sau khi cắt khoảng trắng; `position` (nếu có) là số nguyên ≥ 0.
3. Mở transaction và **khóa dòng `boards`** (BR-B07).
4. Đếm số cột hiện có (n). Không có `position` → đặt `position = n`. Có thì phải nằm trong 0…n.
5. Nếu chèn giữa: dồn các cột có `position ≥` vị trí chèn lên một bước theo cách an toàn với unique (mục 9.2).
6. Thêm `board_columns` (`board_id`, `name`, `position`), commit.
7. Trả Column vừa tạo và thứ tự cột mới của Board.

**Luồng thay thế**

- **Hai Owner cùng thêm cột:** khóa dòng `boards` làm hai request chạy lần lượt; cả hai thành công với position khác nhau.
- **Tên trùng cột khác:** MVP cho phép (DB không ràng buộc tên).

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| `name` rỗng/quá 100 ký tự; `position` âm hoặc lớn hơn số cột hiện có | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project hoặc Board không ACTIVE | 409 | `PROJECT_NOT_ACTIVE` / `BOARD_NOT_ACTIVE` |
| (Đề xuất) Board đã đạt số cột tối đa (cấu hình, mặc định 20) | 422 | `COLUMN_LIMIT_EXCEEDED` (mã **mới**) |

**Dữ liệu và sự kiện:** ghi `board_columns` (1 dòng) và có thể cập nhật position các cột phía sau; nên cập nhật `boards.updated_at`. Không phát sự kiện.

```json
201 Created
{ "data": { "id": 7, "boardId": 1, "name": "Review", "position": 2 },
  "columns": [ { "id": 1, "position": 0 }, { "id": 2, "position": 1 },
               { "id": 7, "position": 2 }, { "id": 3, "position": 3 } ] }
```

---

### UC-B07 — Đổi tên Column

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Sửa tên cột. Không đổi position và không ảnh hưởng Task. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; Board chứa Column đang ACTIVE. |
| **Điều kiện sau** | `board_columns.name` được cập nhật; Task giữ nguyên vì chỉ tham chiếu `column_id`. |
| **Quy tắc** | BR-B04, B05 |
| **API** | `PATCH /api/v1/columns/{columnId}` — body `{ "name": "..." }` |

**Luồng chính**

1. Nạp Column theo `columnId`, suy ra Board và Project, chạy C1–C4.
2. Validate `name` 1–100 ký tự sau khi cắt khoảng trắng.
3. Cập nhật `name`; cập nhật `boards.updated_at` của Board chứa cột (để client biết cấu trúc đã đổi).
4. Trả Column.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| `name` rỗng/quá dài | 400 | `VALIDATION_ERROR` |
| Actor là Member | 403 | `FORBIDDEN` |
| Column không tồn tại hoặc actor không phải thành viên ACTIVE của Project chứa Column | 404 | `RESOURCE_NOT_FOUND` |
| Project hoặc Board không ACTIVE | 409 | `PROJECT_NOT_ACTIVE` / `BOARD_NOT_ACTIVE` |

---

### UC-B08 — Sắp xếp lại Column

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Kéo thả để đổi thứ tự cột. Client gửi **toàn bộ danh sách** `columnId` theo thứ tự mới. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; Board ACTIVE. |
| **Điều kiện sau** | Các cột có position 0…n−1 đúng thứ tự gửi lên; không bao giờ có hai cột trùng vị trí ở trạng thái đã commit. |
| **Quy tắc** | BR-B04, B05, B06, B07 |
| **API** | `PUT /api/v1/boards/{boardId}/columns/order` — body `{ "columnIds": [3, 1, 2] }` |

**Luồng chính**

1. Hệ thống chạy C1–C4.
2. Validate: danh sách không rỗng, không trùng phần tử.
3. Mở transaction và **khóa dòng `boards`**.
4. Đọc tập `column_id` hiện có; so sánh với danh sách gửi lên — hai tập phải **bằng nhau** (không thiếu, không thừa, không thuộc Board khác).
5. Cập nhật position theo **hai pha** để không vi phạm `UQ_board_columns_board_position` giữa chừng (mục 9.2): pha 1 đẩy mọi cột lên vùng vị trí tạm; pha 2 đặt vị trí cuối 0…n−1.
6. Commit, trả danh sách cột theo thứ tự mới.

**Luồng thay thế**

- **Thứ tự không đổi:** vẫn thành công.
- **Client có danh sách cũ** (có người vừa thêm/xóa cột): danh sách không khớp nên bị từ chối; frontend tải lại Board (UC-B03) rồi cho thử lại.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Danh sách rỗng, trùng, thiếu/thừa Column, hoặc chứa Column của Board khác | 400 | `VALIDATION_ERROR` (chi tiết trong `details`) |
| Actor là Member | 403 | `FORBIDDEN` |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project hoặc Board không ACTIVE | 409 | `PROJECT_NOT_ACTIVE` / `BOARD_NOT_ACTIVE` |

**Dữ liệu và sự kiện:** ghi `board_columns.position` của toàn bộ cột và `boards.updated_at`. Task không bị ảnh hưởng.

---

### UC-B09 — Xóa Column

| | |
| --- | --- |
| **Actor** | Owner |
| **Mô tả** | Xóa một cột **đang rỗng**. Phải hỏi Task Service vì Task nằm ở database khác. |
| **Điều kiện trước** | Owner ACTIVE; Project ACTIVE; Board ACTIVE; Column không còn Task. |
| **Điều kiện sau** | Dòng `board_columns` bị xóa; các cột còn lại được đánh lại position 0…n−1. |
| **Quy tắc** | BR-B04, B05, B06, B07, B08 |
| **API** | `DELETE /api/v1/columns/{columnId}` → 204 No Content |

**Luồng chính**

1. Hệ thống chạy C1–C4.
2. Gọi Task Service `GET /internal/columns/{columnId}/task-count` (timeout ngắn, ví dụ 2 giây). Lời gọi này nằm **ngoài** transaction để không giữ khóa DB trong lúc chờ mạng.
3. Số Task > 0 thì từ chối.
4. Mở transaction, **khóa dòng `boards`**, xác nhận Column vẫn tồn tại, xóa Column.
5. Đánh lại position các cột còn lại thành 0…n−1 (mục 9.2).
6. (Đề xuất) Ghi `outbox_events` loại `COLUMN_DELETED` (`boardId`, `columnId`) để Task Service phát hiện Task mồ côi.
7. Commit, trả 204.

**Luồng thay thế**

- **Xóa lại Column đã xóa:** 404.
- **Xóa cột cuối cùng của Board:** được phép (Board chưa tạo được Task cho đến khi thêm cột mới).

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Column còn Task | 409 | `COLUMN_NOT_EMPTY` |
| Không hỏi được Task Service (lỗi/timeout) — **không xóa** | 503 | `DEPENDENCY_UNAVAILABLE` |
| Actor là Member | 403 | `FORBIDDEN` |
| Column không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | `RESOURCE_NOT_FOUND` |
| Project hoặc Board không ACTIVE | 409 | `PROJECT_NOT_ACTIVE` / `BOARD_NOT_ACTIVE` |

**Dữ liệu và sự kiện:** xóa 1 dòng `board_columns`, cập nhật position các cột còn lại, `boards.updated_at`, và (đề xuất) 1 dòng `outbox_events`.

> **Lưu ý (rủi ro R1):** giữa lúc kiểm tra số Task và lúc xóa, một Task mới vẫn có thể được tạo vào Column đó (hai database, không có giao dịch chung).

---

### UC-B10 — Xác minh Board và Column cho Task Service

| | |
| --- | --- |
| **Actor** | Task Service |
| **Mô tả** | Task Service không có dữ liệu Board nên mỗi lần tạo/di chuyển Task phải xác minh Board, Column và trạng thái ghi được. API **nội bộ**, không công khai qua Gateway. |
| **Điều kiện trước** | Lời gọi mang service credential hợp lệ. |
| **Điều kiện sau** | Không thay đổi dữ liệu. |
| **Quy tắc** | BR-B01, B03, B04 |
| **API** | `GET /internal/boards/{boardId}` |

**Luồng chính**

1. Task Service đã xác thực người dùng và kiểm tra membership (theo snapshot); Project Service **không** kiểm tra quyền người dùng ở API này.
2. Project Service xác thực service credential.
3. Nạp Board theo `boardId` và Project chứa nó.
4. Trả `projectId`, `boardStatus`, `projectStatus` và danh sách Column (`id`, `name`, `position`).
5. Task Service so khớp: Board thuộc đúng Project của Task; Column đích nằm trong danh sách; `boardStatus` và `projectStatus` đều ACTIVE.

**Luồng thay thế**

- **Bộ nhớ đệm:** Task Service chỉ được cache **cấu trúc Column** với TTL rất ngắn. **Không cache** `boardStatus`/`projectStatus` trên đường ghi, nếu không Board đã đóng vẫn nhận Task.

**Ngoại lệ**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Board không tồn tại | 404 | `RESOURCE_NOT_FOUND` (Task Service ánh xạ thành 422 `INVALID_COLUMN_TARGET`) |
| Thiếu hoặc sai service credential | 401 | `UNAUTHENTICATED` |
| Project Service quá tải/không sẵn sàng | 503 | `DEPENDENCY_UNAVAILABLE` (Task Service trả 503, không ghi Task) |

```json
200 OK
{ "boardId": 1, "projectId": 1, "boardStatus": "ACTIVE", "projectStatus": "ACTIVE",
  "columns": [ { "id": 1, "name": "Cần làm (Todo)", "position": 0 },
               { "id": 2, "name": "Đang xử lý (In Progress)", "position": 1 },
               { "id": 3, "name": "Hoàn thành (Done)", "position": 2 } ] }
```

---

## 8. Luồng liên service và sự kiện

### 8.1 Bảng tổng hợp lời gọi

| Hướng | Lời gọi | Use case | Mục đích |
| --- | --- | --- | --- |
| Project → User | `GET /internal/users/{userId}` | UC-P06, P12 | Xác minh người được mời; lấy tên hiển thị |
| Project → Task | `GET /internal/columns/{columnId}/task-count` | UC-B09 | Đếm Task trước khi xóa Column |
| Task → Project | `GET /internal/boards/{boardId}` | UC-B10 | Xác minh Board/Column/trạng thái khi ghi Task |
| Task → Project | `GET /internal/projects/{projectId}/members/{userId}` | UC-P17 | Xác minh membership, lấy `membership_version` |

### 8.2 Sự kiện Project Service ghi vào Outbox

| Sự kiện | Khi nào | Nơi nhận | Trạng thái |
| --- | --- | --- | --- |
| `MEMBER_REMOVED` (`projectId`, `userId`, `membershipVersion`, `boardIds`, `operationId`, `sourceAggregateVersion`) | UC-P13, UC-P14; `boardIds` là **mọi Board của Project**, đọc từ bảng `boards` trong cùng transaction | Task Service (dọn assignment) | Bắt buộc |
| `PROJECT_STATUS_CHANGED` (`projectId`, `status`, `previousStatus`) | UC-P05, kể cả mở lại về ACTIVE | Task, Notification | Bắt buộc |
| `MEMBER_ADDED` (`projectId`, `userId`, `membershipVersion`) | UC-P10 | Tùy chọn | Tùy chọn |
| `OWNER_TRANSFERRED` (`projectId`, `fromUserId`, `toUserId`) | UC-P15 | Tùy chọn | Tùy chọn |
| `INVITATION_CREATED` (`invitationId`, `projectId`, `inviteeUserId`, `invitedByUserId`) | UC-P06 | Notification | Tùy chọn |
| `COLUMN_DELETED` (`boardId`, `columnId`) | UC-B09 | Task Service (phát hiện Task mồ côi) | **Đề xuất** |
| `BOARD_STATUS_CHANGED` | UC-B05 | — | Không bắt buộc (Task Service kéo trạng thái) |

Mọi sự kiện có tối thiểu: `eventId`, `type`, `occurredAt`, `aggregateType`, `aggregateId`, `correlationId`, `payload`. Publisher quét theo (`status`, `next_attempt_at`, `created_at`); sự kiện có thể gửi hai lần nên consumer kiểm tra Inbox.

### 8.3 Truyền hiệu lực đổi trạng thái

- **Board:** không phát sự kiện. Task Service **kéo** trạng thái qua UC-B10 mỗi lần ghi Task, nên hiệu lực đến ngay ở lần ghi kế tiếp, không có độ trễ hàng đợi.
- **Project:** `PROJECT_STATUS_CHANGED` phục vụ nhu cầu bổ sung (thông báo…); đường ghi Task vẫn kiểm tra `projectStatus` qua UC-B10.

---

## 9. Dữ liệu liên quan và tiêu chí nghiệm thu

### 9.1 Bảng và ràng buộc trong SQL (`kanban_board_db`)

| Bảng | Cột / ràng buộc chính | Ghi chú |
| --- | --- | --- |
| `projects` | `id` BIGINT identity; `name` VARCHAR(255); `status` ∈ {ACTIVE, COMPLETED, CANCELLED}; `created_by_user_id`; `created_at`, `updated_at` | Điều kiện ghi cho mọi use case |
| `project_memberships` | PK (`project_id`, `user_id`); `role` ∈ {OWNER, MEMBER}; `status` ∈ {ACTIVE, REMOVED}; `joined_at`, `removed_at`, `updated_at`. Unique index một OWNER ACTIVE mỗi Project; index (`user_id`, `status`, `project_id`) | Nguồn quyền khi Redis không có snapshot |
| `invitations` | `id`; `project_id` FK; `user_id`; `invited_by_user_id`; `status` ∈ {PENDING, ACCEPTED, REJECTED, CANCELLED}; `responded_at`. Unique index một PENDING mỗi (`project_id`, `user_id`); index hộp thư user và thống kê Project | |
| `boards` | `id`; `project_id` FK; `name` VARCHAR(255); `board_key` VARCHAR(20); `status`; `created_by_user_id`; `created_at`, `updated_at`. `UQ_boards_project_key` (`project_id`, `board_key`) | Dòng dùng để **khóa** khi thao tác Column |
| `board_columns` | `id`; `board_id` FK; `name` VARCHAR(100); `position` INT ≥ 0. `UQ_board_columns_board_position` (`board_id`, `position`) | Không có cột thời gian, WIP, cờ "cột hoàn thành" |
| `project_membership_versions` | PK (`project_id`, `user_id`); `membership_version`, `authorization_version`, `row_version` | Version cho snapshot, fence và khóa lạc quan |
| `project_authorization_snapshot_sync_jobs` | `job_id`; `operation_id` UNIQUE; UNIQUE (`project_id`, `user_id`, `authorization_version`); `snapshot_json`, `status`, `attempt_count`, `last_error`, `next_attempt_at`; index (`status`, `next_attempt_at`) | Hàng đợi đồng bộ Redis |
| `outbox_events` / `inbox_events` | Outbox: `event_id`, `aggregate_type`, `aggregate_id`, `type`, `payload`, `status` ∈ {PENDING, PROCESSED, FAILED}, `created_at`, `next_attempt_at`. Inbox: PK (`consumer_name`, `event_id`) | Cùng schema với Task Service |

### 9.2 Mẫu xử lý vị trí Column (minh họa)

> **Lưu ý:** các câu lệnh dưới đây là mẫu minh họa logic cần cài đặt, **chưa được chạy thử** trên SQL Server. Cần kiểm thử trên database thật, đặc biệt trường hợp hai request đồng thời.

```sql
-- Khóa Board trước khi đổi cột (BR-B07)
BEGIN TRAN;
SELECT id FROM boards WITH (UPDLOCK, ROWLOCK) WHERE id = @boardId;

-- Pha 1: đẩy toàn bộ cột lên vùng vị trí tạm, không trùng vùng 0..n-1
UPDATE board_columns SET position = position + 100000 WHERE board_id = @boardId;

-- Pha 2: đặt vị trí cuối theo thứ tự mới (@ordered = bảng column_id, new_position)
UPDATE bc SET bc.position = o.new_position
FROM board_columns bc
JOIN @ordered o ON o.column_id = bc.id
WHERE bc.board_id = @boardId;
COMMIT;
```

Thứ tự mới (`@ordered`) do ứng dụng tính: lấy danh sách cột hiện có, áp dụng thao tác (chèn, xóa, hoán đổi), rồi gán `new_position = 0…n−1`. Số cột thường chỉ vài đến vài chục nên chi phí cập nhật toàn bộ là không đáng kể.

### 9.3 Tiêu chí nghiệm thu — nhóm Project và Thành viên

| Mã | Use Case | Điều kiện nghiệm thu |
| --- | --- | --- |
| AC-P01 | UC-P01 | Project ACTIVE và membership OWNER được tạo cùng lúc; nếu bước nào lỗi thì không còn Project mồ côi (AC-02 của v1.2). |
| AC-P02 | UC-P02, P03 | Danh sách/chi tiết chỉ hiển thị Project có membership ACTIVE; người đã bị loại và người ngoài nhận 404 / không thấy; SYSTEM_ADMIN không đọc được Project mà mình không là thành viên (AC-04). |
| AC-P03 | UC-P04, P05 | Owner đổi tên được khi Project ACTIVE; Member bị 403; đổi tên khi không ACTIVE bị 409 `PROJECT_NOT_ACTIVE`. |
| AC-P04 | UC-P05 | Owner chuyển COMPLETED/CANCELLED về ACTIVE thành công và phát `PROJECT_STATUS_CHANGED`; Member bị 403; chuyển thẳng COMPLETED ↔ CANCELLED bị 409; sau khi mở lại các thao tác ghi hoạt động bình thường (AC-21). |
| AC-P05 | UC-P06, P08, P10, P11 | Mời được; mời trùng khi đang PENDING bị 409; chấp nhận tạo membership MEMBER; từ chối không tạo membership; người khác không phản hồi thay được; hai request chấp nhận đồng thời chỉ một thành công (AC-05). |
| AC-P06 | UC-P13, P14 | Owner không thể rời hoặc bị loại khi chưa chuyển quyền (409 `OWNER_CANNOT_LEAVE`) (AC-06). |
| AC-P07 | UC-P13, P14 | Sau khi snapshot cập nhật, request của người bị loại bị từ chối; Task và bình luận của họ vẫn còn (AC-07). |
| AC-P08 | UC-P15 | Không thể có hai OWNER ACTIVE; chuyển quyền thành công mà không vi phạm ràng buộc; người nhận không phải thành viên ACTIVE bị 422 (AC-03). |
| AC-P09 | UC-P13, P14 | `MEMBER_REMOVED` được ghi vào `outbox_events` cùng transaction (rollback thì không có sự kiện); publisher gửi lại khi lỗi mà Task Service không xử lý trùng (AC-23). |
| AC-P10 | UC-P16 | Redis lỗi tạm thời: job được retry và quyền cuối cùng đúng; snapshot version cũ không ghi đè version mới (AC-19). |
| AC-P11 | Toàn bộ | Project COMPLETED/CANCELLED vẫn xem được nhưng mọi thao tác ghi (trừ mở lại Project) bị 409 `PROJECT_NOT_ACTIVE` (AC-14). |

### 9.4 Tiêu chí nghiệm thu — nhóm Board và Column

| Mã | Use Case | Điều kiện nghiệm thu |
| --- | --- | --- |
| AC-B01 | UC-B01 | Owner tạo Board thành công kèm 3 cột mặc định position 0, 1, 2; Member bị 403; người ngoài Project bị 404. |
| AC-B02 | UC-B01 | `boardKey` trùng trong cùng Project bị 409 `BOARD_KEY_ALREADY_EXISTS`, kể cả khi hai request đồng thời (chỉ một thành công); cùng `boardKey` ở Project khác tạo được. |
| AC-B03 | UC-B01, B05, B06 | Khi Project COMPLETED/CANCELLED, mọi thao tác ghi Board/Column (kể cả đổi trạng thái Board) bị 409 `PROJECT_NOT_ACTIVE`; các API xem vẫn hoạt động. |
| AC-B04 | UC-B02 | Danh sách chỉ hiển thị cho thành viên ACTIVE; lọc theo `status` và phân trang đúng; người đã bị loại nhận 404. |
| AC-B05 | UC-B03 | Phản hồi có Column theo position tăng dần, kèm `writable` và `myRole` đúng; không chứa Task. |
| AC-B06 | UC-B04 | Owner đổi được tên; gửi `boardKey` khác giá trị hiện tại bị 400; Board không ACTIVE bị 409 `BOARD_NOT_ACTIVE`. |
| AC-B07 | UC-B05 | ACTIVE → COMPLETED/CANCELLED và ngược lại về ACTIVE thành công; COMPLETED ↔ CANCELLED trực tiếp bị 409 `INVALID_STATUS_TRANSITION`; sau khi đóng Board, Task Service từ chối tạo/di chuyển Task trên Board đó. |
| AC-B08 | UC-B06 | Thêm cột ở cuối và ở giữa đều cho position liên tục 0…n−1; hai Owner thêm cột đồng thời không gây lỗi unique. |
| AC-B09 | UC-B07 | Đổi tên cột không làm đổi position hay mất Task trong cột. |
| AC-B10 | UC-B08 | Hoán đổi hai cột thành công không vi phạm unique; danh sách thiếu/thừa/trùng `columnId` bị 400 và dữ liệu không đổi. |
| AC-B11 | UC-B09 | Xóa cột rỗng thành công và position các cột còn lại liên tục; cột còn Task bị 409 `COLUMN_NOT_EMPTY`. |
| AC-B12 | UC-B09 | Khi Task Service không trả lời, Column **không bị xóa** và API trả 503 `DEPENDENCY_UNAVAILABLE`. |
| AC-B13 | UC-B10 | API nội bộ trả đúng trạng thái Board/Project và danh sách Column; không truy cập được từ Gateway công khai; thiếu service credential bị 401. |
| AC-B14 | Toàn bộ | Member gọi mọi API ghi bị 403; người không phải thành viên luôn nhận 404; lỗi trả đúng cấu trúc chuẩn có `correlationId`. |

---

## 10. Điểm đề xuất, rủi ro và điểm lệch giữa các tài liệu

### 10.1 Điểm đề xuất cần xác nhận

Nguồn không quy định nên tài liệu này tạm chọn như dưới đây. Cần chốt hoặc sửa trước khi code.

| # | Nội dung | Đề xuất |
| --- | --- | --- |
| 1 | Định dạng và tính bất biến của `board_key` | Chữ thường, số, dấu gạch ngang, 2–20 ký tự; không đổi sau khi tạo. |
| 2 | Mở lại Board đã COMPLETED/CANCELLED | Cho phép, chỉ Owner; không chuyển thẳng COMPLETED ↔ CANCELLED. |
| 3 | Giới hạn số cột mỗi Board | Tối đa 20 (cấu hình), vượt thì 422 `COLUMN_LIMIT_EXCEEDED` — mã lỗi **mới**, chưa có trong danh mục mã lỗi của v1.2. |
| 4 | Trùng tên Column trong cùng Board | Cho phép. Nếu muốn cấm cần thêm unique (`board_id`, `name`) để chống race. |
| 5 | Xóa cột cuối cùng của Board | Cho phép. |
| 6 | Xóa Board / xóa Project | Không có trong MVP; dùng CANCELLED. |
| 7 | Cột mặc định khi tạo Board | Todo, In Progress, Done, lấy từ cấu hình. |
| 8 | Phát `COLUMN_DELETED` qua Outbox | Nên có để phát hiện Task mồ côi (R1); không bắt buộc cho MVP. |
| 9 | Đặt đúng trạng thái hiện tại của Project | No-op, 200, không phát sự kiện (thống nhất với Board). |
| 10 | Chuyển Owner cho chính mình | 400 `VALIDATION_ERROR`. |
| 11 | Không hỏi được User Service khi mời | 503 `DEPENDENCY_UNAVAILABLE` (fail-closed). |
| 12 | Tên hiển thị trong danh sách thành viên | Lấy từ User Service; thiếu thì trả không có tên. |
| 13 | Khi Redis không có snapshot | Đọc lại database của chính service rồi nạp Redis; lỗi thì từ chối (quyết định mở số 5 của v1.2). |
| 14 | Giá trị `status` của bảng job snapshot | PENDING, RUNNING, SUCCESS, FAILED, thêm CHECK (quyết định mở số 8 của v1.2). |

### 10.2 Rủi ro đã biết

| Mã | Rủi ro | Giảm thiểu |
| --- | --- | --- |
| R1 | **Task mồ côi sau khi xóa Column.** Giữa lúc kiểm tra số Task và lúc xóa Column, một Task mới vẫn có thể được tạo vào Column đó. | Task Service luôn xác minh Column qua UC-B10; phát `COLUMN_DELETED`; frontend bỏ qua Task có `columnId` không tồn tại. |
| R2 | **Project Service là điểm phụ thuộc của việc ghi Task.** Mỗi lần ghi Task đều gọi UC-B10; chậm thì ghi Task chậm hoặc 503. | Timeout ngắn; cache **cấu trúc Column** với TTL rất ngắn; không cache trạng thái; theo dõi độ trễ API nội bộ. |
| R3 | **VARCHAR cho `name`.** `boards.name`, `board_columns.name` và cả `projects.name` dùng VARCHAR trong khi dữ liệu mẫu có tiếng Việt có dấu (ví dụ "Cần làm (Todo)"). Ký tự có dấu có thể bị mất nếu collation không hỗ trợ. | Đổi sang NVARCHAR hoặc xác nhận collation/mã hóa và kiểm thử ký tự có dấu trước khi triển khai. |
| R4 | **Hai Owner sửa cùng lúc** có thể vi phạm unique vị trí Column. | Khóa dòng `boards` trong mọi thao tác Column (BR-B07); bắt lỗi unique để trả 409 thay vì 500. |
| R5 | **Độ trễ snapshot Redis** giữa lúc đổi quyền và lúc snapshot cập nhật (ví dụ vừa bị loại vẫn thao tác được trong khoảnh khắc ngắn). | Thao tác nhạy cảm đọc thẳng database; job retry có backoff; kiểm tra `auth_version` trong token. |
| R6 | **`project_membership_versions` không có FK** tới `project_memberships`; có thể lệch nếu ứng dụng quên cập nhật cả hai trong một transaction. | Luôn ghi hai bảng trong cùng transaction; kiểm thử các use case P01, P10, P13, P14, P15. |

### 10.3 Điểm lệch giữa các tài liệu nguồn (đã đối chiếu)

| # | Điểm lệch | Cách xử lý trong tài liệu này |
| --- | --- | --- |
| 1 | Tài liệu v1.2 nói `outbox_events`/`inbox_events` của `kanban_board_db` nằm ở file riêng `kanban_board_outbox_inbox.sql`, nhưng file `kanban_mssql_databases.sql` hiện đã **có sẵn** hai bảng này trong phần `kanban_board_db`. | Coi hai bảng đã có; nên cập nhật lại mô tả ở tài liệu v1.2 để khớp. |
| 2 | Khối "IMPORTANT IMPLEMENTATION NOTES" cuối file SQL vẫn nhắc `PROJECT_MANAGER`, `ProjectMembers`, `Tasks` (thuật ngữ cũ của v1.0). | Dùng thuật ngữ v1.2 (OWNER/MEMBER, `project_memberships`, `cards`); nên dọn ghi chú trong file SQL. |
| 3 | Dữ liệu mẫu job snapshot dùng `status = 'SUCCESS'`, còn Outbox dùng `PROCESSED`; bảng job chưa có CHECK. | Giữ như đề xuất số 14. |
| 4 | Bảng `invitations` trong v1.2 (mục 12.2) và SQL khớp nhau; tuy nhiên v1.2 UC-10 không nói rõ có kiểm tra Project ACTIVE khi hủy lời mời, trong khi bảng 6.3 chặn "mời, chấp nhận lời mời…" khi Project không ACTIVE. | UC-P08 áp C4 (Project ACTIVE) cho nhất quán; cần xác nhận. |
| 5 | v1.2 (BR-19) và tài liệu Board đều đề xuất định dạng `board_key` nhưng chưa chốt. | Giữ ở đề xuất số 1. |
