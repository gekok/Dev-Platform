**WORKFLOWHUB**

**BOARD SERVICE**

**Đặc tả chi tiết Use Case**

Board • Column • API nội bộ cho Task Service

**Phiên bản 1.0  |  Khớp tài liệu nghiệp vụ v1.2 và kanban_mssql_databases.sql  |  07/11/2026**

| Thuộc tính | Giá trị |
| --- | --- |
| Mục đích | Mô tả chi tiết từng Use Case của Board Service để BA, Developer và Tester dùng chung: luồng xử lý, ngoại lệ, mã lỗi, dữ liệu bị tác động, tiêu chí nghiệm thu. |
| Quan hệ với tài liệu v1.2 | Tài liệu này tách nhỏ và làm chi tiết UC-13 (Quản lý Board) và UC-14 (Quản lý Column), đồng thời bổ sung các use case xem Board và API nội bộ mà tài liệu v1.2 mới nhắc sơ lược. Mã quy tắc dùng tiền tố BR-B để không trùng với BR-01…BR-43 của tài liệu v1.2. |
| Nguồn chuẩn | Schema trong kanban_mssql_databases.sql (bảng boards, board_columns, projects, project_memberships). Điểm nào SQL không quy định mà tài liệu phải chọn thì được đánh dấu (đề xuất) và tổng hợp ở mục 9. |
| Công nghệ | Java Spring Boot; SQL Server; Redis (snapshot quyền); RabbitMQ (Outbox); gọi nội bộ qua Eureka |

# Mục lục

Phạm vi và vị trí của Board Service

Actor và phân quyền

Danh mục Use Case

Quy tắc nghiệp vụ của Board Service

Đặc tả chi tiết Use Case

Luồng liên service

Dữ liệu liên quan và mẫu truy vấn

Tiêu chí nghiệm thu

Điểm đề xuất cần xác nhận và rủi ro

# 1. Phạm vi và vị trí của Board Service

Board Service**không phải một microservice tách riêng**. Theo thiết kế 4 service của hệ thống, Board là một module nằm trong**Project Service**và dùng chung databasekanban_board_db. Hệ quả quan trọng:

Kiểm tra Project (trạng thái) và membership (role) là**truy vấn cục bộ**, thực hiện được trong cùng transaction với thao tác Board/Column; không cần gọi HTTP nội bộ.

Hệ thống không có FK xuyên database, nên Board Service chỉ**tham chiếu scalar**tới User (created_by_user_id) và không biết gì về Task. Mọi thứ liên quan Task phải đi qua API nội bộ hoặc sự kiện.

Sự kiện gửi đi (nếu có) ghi vàooutbox_eventscủakanban_board_db(bảng này được bổ sung ở v1.2).

|  | Thuộc Board Service | Không thuộc Board Service |
| --- | --- | --- |
| Dữ liệu | Bảng boards, board_columns | Task/cards (Task Service); thông báo (Notification Service); tài khoản (User Service) |
| Chức năng | Tạo/xem/sửa Board, đổi trạng thái Board, tạo/đổi tên/sắp xếp/xóa Column, cung cấp API nội bộ xác minh Board và Column | Project, membership, lời mời (module khác trong cùng Project Service); tạo/di chuyển Task; bình luận |
| Kiểm tra phụ thuộc | Đọc projects.status và project_memberships (cục bộ) | Đếm Task trong Column: hỏi Task Service qua API nội bộ |

# 2. Actor và phân quyền

| Actor | Mô tả | Cách nhận diện |
| --- | --- | --- |
| Project Owner | Quản trị cấu trúc Board/Column của Project. | project_memberships.role = OWNER, status = ACTIVE |
| Project Member | Chỉ xem cấu trúc Board/Column; làm việc trên Task (ngoài phạm vi tài liệu này). | role = MEMBER, status = ACTIVE |
| Người không phải thành viên | Không được biết Board có tồn tại; luôn nhận 404. | Không có membership ACTIVE (kể cả đã REMOVED) |
| System Admin | Không có quyền đặc biệt trên Board (BR-07 của tài liệu v1.2). Chỉ có quyền khi chính là thành viên. | System role không được xét ở Board Service |
| Task Service | Hệ thống nền gọi API nội bộ để xác minh Board/Column. | Service credential trên kênh nội bộ |

## 2.1 Ma trận quyền (khi Project và Board đều ACTIVE)

| Hành động | Không phải thành viên | MEMBER | OWNER |
| --- | --- | --- | --- |
| Xem danh sách Board | Không (404) | Có | Có |
| Xem chi tiết Board kèm Column | Không (404) | Có | Có |
| Tạo Board | Không (404) | Không (403) | Có |
| Đổi tên Board | Không (404) | Không (403) | Có |
| Đổi trạng thái Board | Không (404) | Không (403) | Có |
| Tạo / đổi tên / sắp xếp / xóa Column | Không (404) | Không (403) | Có |

## 2.2 Điều kiện theo trạng thái Project và Board

| Project | Board | Xem | Đổi cấu trúc Board/Column | Đổi trạng thái Board | Task ghi (tạo/di chuyển) |
| --- | --- | --- | --- | --- | --- |
| ACTIVE | ACTIVE | Có | Có | Có | Có |
| ACTIVE | COMPLETED / CANCELLED | Có | Không (409 BOARD_NOT_ACTIVE) | Có (để mở lại) | Không |
| COMPLETED / CANCELLED | Bất kỳ | Có | Không (409 PROJECT_NOT_ACTIVE) | Không (409 PROJECT_NOT_ACTIVE) | Không |

*Mở lại Project về ACTIVE****không****tự thay đổi trạng thái các Board bên trong; Board đang COMPLETED/CANCELLED vẫn chỉ đọc cho đến khi Owner mở lại từng Board.*

# 3. Danh mục Use Case

| Mã | Tên Use Case | Actor | API | Liên hệ v1.2 |
| --- | --- | --- | --- | --- |
| UC-B01 | Tạo Board | Owner | POST /api/v1/projects/{projectId}/boards | UC-13 |
| UC-B02 | Xem danh sách Board của Project | Owner, Member | GET /api/v1/projects/{projectId}/boards | Mới |
| UC-B03 | Xem chi tiết Board kèm Column | Owner, Member | GET /api/v1/boards/{boardId} | Mới |
| UC-B04 | Đổi tên Board | Owner | PATCH /api/v1/boards/{boardId} | UC-13 |
| UC-B05 | Đổi trạng thái Board | Owner | PATCH /api/v1/boards/{boardId}/status | UC-13 |
| UC-B06 | Tạo Column | Owner | POST /api/v1/boards/{boardId}/columns | UC-14 |
| UC-B07 | Đổi tên Column | Owner | PATCH /api/v1/columns/{columnId} | UC-14 |
| UC-B08 | Sắp xếp lại Column | Owner | PUT /api/v1/boards/{boardId}/columns/order | UC-14 |
| UC-B09 | Xóa Column | Owner | DELETE /api/v1/columns/{columnId} | UC-14 |
| UC-B10 | Xác minh Board và Column cho Task Service | Task Service | GET /internal/boards/{boardId} | Mục 9.6 |

*Không có use case****xóa Board****trong MVP: Board không còn dùng thì chuyển sang CANCELLED (đề xuất, mục 9).*

# 4. Quy tắc nghiệp vụ của Board Service

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-B01 | Quan hệ Board–Project | Mỗi Board thuộc đúng một Project; project_id không đổi sau khi tạo. Backend luôn kiểm tra Board thuộc Project trong đường dẫn, không tin ID do client tự khai. |
| BR-B02 | board_key | Duy nhất trong Project (UQ_boards_project_key), khác Project thì được trùng. Tối đa 20 ký tự (DB). (Đề xuất) định dạng chữ thường, số và dấu gạch ngang, 2–20 ký tự, bất biến sau khi tạo. |
| BR-B03 | Trạng thái Board | ACTIVE, COMPLETED, CANCELLED (CHECK trong DB). Chuyển hợp lệ: ACTIVE → COMPLETED, ACTIVE → CANCELLED, COMPLETED → ACTIVE, CANCELLED → ACTIVE. Không chuyển thẳng COMPLETED ↔ CANCELLED. Đặt lại đúng trạng thái hiện tại là thao tác không đổi gì (trả 200). |
| BR-B04 | Điều kiện được ghi | Mọi thao tác ghi cấu trúc cần Project ACTIVE và Board ACTIVE. Riêng đổi trạng thái Board chỉ cần Project ACTIVE (để Owner mở lại Board đã đóng). |
| BR-B05 | Chỉ Owner đổi cấu trúc | Tạo/sửa Board, đổi trạng thái Board và mọi thao tác Column chỉ dành cho Owner. Member chỉ đọc. |
| BR-B06 | Thứ tự Column | position ≥ 0 và duy nhất trong Board (UQ_board_columns_board_position, CHK_board_columns_position). Sau mỗi thao tác, các position của một Board phải liên tục từ 0 đến n−1. |
| BR-B07 | Đồng thời | Mọi thao tác làm thay đổi Column chạy trong một transaction và khóa dòng boards tương ứng, để hai Owner thao tác cùng lúc không vi phạm unique vị trí. |
| BR-B08 | Xóa Column | Chỉ xóa được Column không còn Task. Số Task do Task Service trả lời; nếu không hỏi được thì từ chối xóa (fail-closed). |
| BR-B09 | Không xóa Board | MVP không có thao tác xóa Board (không có DELETE trên boards); dùng trạng thái CANCELLED. |
| BR-B10 | Che giấu tài nguyên | Người không có membership ACTIVE nhận 404, không phải 403. Member thiếu quyền nhận 403. Thứ tự kiểm tra ở mục 5.1. |
| BR-B11 | Cột mặc định | Khi tạo Board có thể sinh sẵn 3 cột theo cấu hình (mặc định Todo, In Progress, Done) với position 0, 1, 2. |
| BR-B12 | Thời gian | created_at, updated_at lưu UTC (DATETIMEOFFSET). Mọi thay đổi Board cập nhật updated_at; board_columns không có cột thời gian. |
| BR-B13 | Idempotency | Tạo Board và tạo Column hỗ trợ Idempotency-Key để retry không tạo trùng (BR-42 của tài liệu v1.2). |

# 5. Đặc tả chi tiết Use Case

## 5.1 Chuỗi kiểm tra chuẩn

Mọi API của Board Service (trừ API nội bộ) chạy các bước sau theo đúng thứ tự. Các use case bên dưới chỉ nhắc tên bước (C1…C6) để tránh lặp.

| Bước | Nội dung | Kết quả khi không đạt |
| --- | --- | --- |
| C1 | Xác thực token tại Gateway; lấy actorId từ principal. Không nhận actorId hay role từ client. | 401 UNAUTHENTICATED |
| C2 | Nạp tài nguyên từ đường dẫn (Board hoặc Column), suy ra Project, rồi kiểm tra membership ACTIVE của actor trong Project đó. | 404 RESOURCE_NOT_FOUND (cả khi tài nguyên không tồn tại và khi actor không có quyền xem) |
| C3 | Kiểm tra role theo ma trận mục 2.1. | 403 FORBIDDEN |
| C4 | Kiểm tra trạng thái: Project ACTIVE; Board ACTIVE (nếu thao tác cần). | 409 PROJECT_NOT_ACTIVE hoặc 409 BOARD_NOT_ACTIVE |
| C5 | Validate dữ liệu đầu vào (độ dài, định dạng, kiểu). | 400 VALIDATION_ERROR |
| C6 | Kiểm tra ràng buộc nghiệp vụ và duy nhất; thực hiện thay đổi trong transaction. | 409 / 422 theo từng use case |

*Role và membership đọc từ snapshot Redis; nếu không có snapshot thì đọc trực tiếp**project_memberships**(cùng database nên không tốn thêm một lần gọi liên service).*

## UC-B01 — Tạo Board

| Actor | Project Owner |
| --- | --- |
| Mô tả | Owner tạo một Board mới trong Project để tổ chức công việc theo cột (ví dụ một Sprint). |
| Kích hoạt | Owner chọn “Tạo Board” trong màn hình Project. |
| Điều kiện trước | Owner có membership ACTIVE; Project ACTIVE. |
| Điều kiện sau | Một dòng boards mới (status = ACTIVE, created_by_user_id = Owner) và, nếu bật cột mặc định, 3 dòng board_columns. Tất cả được tạo trong một transaction. |
| Quy tắc áp dụng | BR-B01, BR-B02, BR-B04, BR-B05, BR-B11, BR-B12, BR-B13 |
| API | POST /api/v1/projects/{projectId}/boards → 201 Created, header Location |

**Luồng chính**

Owner nhập tên Board,boardKeyvà chọn có sinh cột mặc định hay không (mặc định: có).

Hệ thống chạy C1–C4 (Owner của Project, Project ACTIVE).

Validate:namesau khi cắt khoảng trắng đầu/cuối có 1–255 ký tự;boardKeyđúng định dạng (BR-B02).

Mở transaction: thêmboardsvớistatus = ACTIVE,created_at = updated_at = now (UTC).

Nếu bật cột mặc định: thêm các cột theo cấu hình vớiposition0, 1, 2.

Commit và trả Board kèm danh sách Column đã tạo.

**Luồng thay thế**

**Tắt cột mặc định:**Board được tạo không có cột; Owner tự tạo cột ở UC-B06. Board chưa có cột thì chưa tạo được Task.

**Gửi lại cùng Idempotency-Key:**trả lại kết quả của lần tạo đầu, không tạo Board thứ hai.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Thiếu/sai name hoặc boardKey (rỗng, quá dài, sai định dạng) | 400 | VALIDATION_ERROR |
| Actor là Member | 403 | FORBIDDEN |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | RESOURCE_NOT_FOUND |
| Project COMPLETED hoặc CANCELLED | 409 | PROJECT_NOT_ACTIVE |
| boardKey đã có trong Project (kể cả khi hai request chạy đồng thời: bắt lỗi vi phạm UQ_boards_project_key từ DB, không chỉ kiểm tra trước) | 409 | BOARD_KEY_ALREADY_EXISTS |

**Dữ liệu và sự kiện**

Ghi:boards(1 dòng),board_columns(0 hoặc 3 dòng).

Không phát sự kiện RabbitMQ. Task Service chưa có gì để biết về Board mới cho đến khi có Task.

**Ví dụ request / response**

POST /api/v1/projects/1/boards

{ "name": "Sprint 2 - Hoàn thiện MVP", "boardKey": "sprint-2", "createDefaultColumns": true }

201 Created   Location: /api/v1/boards/2

{ "data": {

"id": 2, "projectId": 1, "name": "Sprint 2 - Hoàn thiện MVP", "boardKey": "sprint-2",

"status": "ACTIVE", "createdAt": "2026-11-07T03:50:00Z",

"columns": [ { "id": 4, "name": "Todo", "position": 0 },

{ "id": 5, "name": "In Progress", "position": 1 },

{ "id": 6, "name": "Done", "position": 2 } ] } }

## UC-B02 — Xem danh sách Board của Project

| Actor | Project Owner, Project Member |
| --- | --- |
| Mô tả | Thành viên xem các Board của Project để chọn Board làm việc. |
| Kích hoạt | Mở trang chi tiết Project hoặc menu Board. |
| Điều kiện trước | Actor có membership ACTIVE trong Project. Project ở trạng thái nào cũng được xem. |
| Điều kiện sau | Không thay đổi dữ liệu. |
| Quy tắc áp dụng | BR-B10 |
| API | GET /api/v1/projects/{projectId}/boards?status=&page=&size=&sort= |

**Luồng chính**

Hệ thống chạy C1–C2 (membership ACTIVE; chưa cần quyền Owner).

Validate tham số:statusthuộc ACTIVE/COMPLETED/CANCELLED nếu có;sizekhông vượt giới hạn tối đa;sortthuộc tập cho phép (mặc địnhcreatedAt,desc).

Truy vấnboardstheoproject_id, lọcstatusnếu có, phân trang.

Trả danh sách mỗi Board gồmid,name,boardKey,status,createdAt,columnCount, kèm thông tin phân trang.

**Luồng thay thế**

**Project chưa có Board:**trả danh sách rỗng (200), không phải lỗi.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| status, size hoặc sort không hợp lệ | 400 | VALIDATION_ERROR |
| Project không tồn tại hoặc actor không phải thành viên ACTIVE (kể cả người đã bị loại) | 404 | RESOURCE_NOT_FOUND |

**Dữ liệu và sự kiện**

Chỉ đọcboards(và đếmboard_columnschocolumnCount). Truy vấn theoproject_iddùng được chỉ mục củaUQ_boards_project_key.

## UC-B03 — Xem chi tiết Board kèm Column

| Actor | Project Owner, Project Member |
| --- | --- |
| Mô tả | Lấy cấu trúc của Board (thông tin Board và các Column theo thứ tự) để dựng màn hình Kanban. Task thuộc Task Service nên không nằm trong phản hồi này. |
| Kích hoạt | Mở một Board. |
| Điều kiện trước | Actor có membership ACTIVE trong Project chứa Board. |
| Điều kiện sau | Không thay đổi dữ liệu. |
| Quy tắc áp dụng | BR-B01, BR-B06, BR-B10 |
| API | GET /api/v1/boards/{boardId} |

**Luồng chính**

Hệ thống nạp Board theoboardId, suy ra Project, rồi chạy C1–C2.

Đọc các Column của Board, sắp xếp theopositiontăng dần.

Tính cờwritable= (Project ACTIVE**và**Board ACTIVE) vàmyRolecủa actor để giao diện biết nên ẩn/khóa nút nào.

Trả Board,writable,myRolevà danh sách Column.

Frontend gọi tiếp Task Service (GET /api/v1/boards/{boardId}/tasks) và ghép Task vào từng Column theocolumnId.

**Luồng thay thế**

**Task Service lỗi hoặc chậm:**cấu trúc Board vẫn hiển thị; vùng Task báo lỗi và cho tải lại. Board Service không phụ thuộc Task Service ở use case này.

**Có Task mang columnId không còn trong danh sách Column (dữ liệu mồ côi, xem rủi ro R1):**frontend không hiển thị Task đó và ghi log; không làm hỏng cả màn hình.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | RESOURCE_NOT_FOUND |

**Dữ liệu và sự kiện**

Chỉ đọcboards,board_columns,projects(lấy status),project_memberships(lấy role).

**Ví dụ response**

200 OK

{ "data": {

"id": 1, "projectId": 1, "name": "Sprint 1 - Core Features", "boardKey": "sprint-1",

"status": "ACTIVE", "writable": true, "myRole": "MEMBER",

"columns": [ { "id": 1, "name": "Cần làm (Todo)", "position": 0 },

{ "id": 2, "name": "Đang xử lý (In Progress)", "position": 1 },

{ "id": 3, "name": "Hoàn thành (Done)", "position": 2 } ] } }

## UC-B04 — Đổi tên Board

| Actor | Project Owner |
| --- | --- |
| Mô tả | Owner sửa tên hiển thị của Board. boardKey không đổi. |
| Kích hoạt | Owner chọn “Đổi tên” trên Board. |
| Điều kiện trước | Owner có membership ACTIVE; Project ACTIVE; Board ACTIVE. |
| Điều kiện sau | boards.name và updated_at được cập nhật. |
| Quy tắc áp dụng | BR-B02, BR-B04, BR-B05, BR-B12 |
| API | PATCH /api/v1/boards/{boardId} — body { "name": "..." } |

**Luồng chính**

Hệ thống chạy C1–C4 (Owner, Project ACTIVE, Board ACTIVE).

Validatenamesau khi cắt khoảng trắng có 1–255 ký tự.

Cập nhậtnamevàupdated_at = now (UTC).

Trả Board sau khi cập nhật.

**Luồng thay thế**

**Tên mới giống tên cũ:**coi như không đổi, vẫn trả 200.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Tên rỗng/quá dài; body chứa boardKey khác giá trị hiện tại (không hỗ trợ đổi key) | 400 | VALIDATION_ERROR |
| Actor là Member | 403 | FORBIDDEN |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | RESOURCE_NOT_FOUND |
| Project COMPLETED/CANCELLED | 409 | PROJECT_NOT_ACTIVE |
| Board COMPLETED/CANCELLED | 409 | BOARD_NOT_ACTIVE |

**Dữ liệu và sự kiện**

Ghiboards.name,boards.updated_at. Không phát sự kiện.

## UC-B05 — Đổi trạng thái Board

| Actor | Project Owner |
| --- | --- |
| Mô tả | Owner đóng Board (COMPLETED hoặc CANCELLED) khi xong việc/hủy, hoặc mở lại Board về ACTIVE. |
| Kích hoạt | Owner chọn “Hoàn thành Board”, “Hủy Board” hoặc “Mở lại Board”. |
| Điều kiện trước | Owner có membership ACTIVE; Project ACTIVE. Board ở trạng thái nguồn hợp lệ. |
| Điều kiện sau | boards.status và updated_at được cập nhật. Board không ACTIVE trở thành chỉ đọc đối với cả Board Service và Task Service. Dữ liệu Task, Column giữ nguyên. |
| Quy tắc áp dụng | BR-B03, BR-B04, BR-B05 |
| API | PATCH /api/v1/boards/{boardId}/status — body { "status": "COMPLETED" } |

**Luồng chính**

Hệ thống chạy C1–C3, rồi C4 chỉ kiểm tra**Project ACTIVE**(không kiểm tra trạng thái Board, vì đây là thao tác đổi chính trạng thái đó).

Validatestatusthuộc ACTIVE/COMPLETED/CANCELLED.

Kiểm tra chuyển trạng thái theo bảng bên dưới.

Cập nhậtstatus,updated_atvà trả Board.

Từ lần ghi Task kế tiếp, Task Service đọc trạng thái mới qua UC-B10 và từ chối nếu Board không ACTIVE. Không cần sự kiện.

**Luồng thay thế**

**Đặt đúng trạng thái hiện tại:**không đổi gì, trả 200 (thao tác lặp an toàn).

**Mở lại Board khi Project đã COMPLETED/CANCELLED:**bị chặn; Owner phải mở lại Project trước (UC-07 của tài liệu v1.2).

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| status không thuộc tập giá trị | 400 | VALIDATION_ERROR |
| Actor là Member | 403 | FORBIDDEN |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | RESOURCE_NOT_FOUND |
| Project COMPLETED/CANCELLED | 409 | PROJECT_NOT_ACTIVE |
| Chuyển thẳng COMPLETED ↔ CANCELLED | 409 | INVALID_STATUS_TRANSITION |

**Dữ liệu và sự kiện**

Ghiboards.status,boards.updated_at.

Không phát sự kiện; việc truyền hiệu lực dựa vào Task Service**kéo**trạng thái khi cần (mục 6.4). Có thể thêm sự kiệnBOARD_STATUS_CHANGEDnếu sau này cần thông báo (đề xuất, không bắt buộc).

**Bảng chuyển trạng thái**

Từ \ Sang      ACTIVE     COMPLETED   CANCELLED

ACTIVE         (no-op)    cho phép    cho phép

COMPLETED      cho phép   (no-op)     KHÔNG

CANCELLED      cho phép   KHÔNG       (no-op)

## UC-B06 — Tạo Column

| Actor | Project Owner |
| --- | --- |
| Mô tả | Owner thêm một cột mới vào Board, ở cuối hoặc ở một vị trí chỉ định. |
| Kích hoạt | Owner chọn “Thêm cột”. |
| Điều kiện trước | Owner có membership ACTIVE; Project ACTIVE; Board ACTIVE. |
| Điều kiện sau | Một dòng board_columns mới; các position của Board vẫn liên tục 0…n−1. |
| Quy tắc áp dụng | BR-B04, BR-B05, BR-B06, BR-B07, BR-B13 |
| API | POST /api/v1/boards/{boardId}/columns — body { "name": "Review", "position": 2 } (position tùy chọn) → 201 |

**Luồng chính**

Hệ thống chạy C1–C4.

Validate:name1–100 ký tự sau khi cắt khoảng trắng;position(nếu có) là số nguyên ≥ 0.

Mở transaction và**khóa dòng boards**của Board (BR-B07).

Đếm số cột hiện có (n). Nếupositionkhông có thì đặtposition = n(thêm cuối). Nếu có thì phải nằm trong khoảng 0…n.

Nếu chèn giữa: dồn các cột cóposition ≥ vị trí chènlên một bước theo cách an toàn với unique (mục 7.2).

Thêmboard_columns(board_id,name,position), commit.

Trả Column vừa tạo và thứ tự cột mới của Board.

**Luồng thay thế**

**Hai Owner cùng thêm cột:**khóa dòngboardslàm hai request chạy lần lượt; cả hai thành công vớipositionkhác nhau, không phát sinh lỗi unique.

**Tên trùng với cột khác:**MVP cho phép (DB không ràng buộc tên). Xem đề xuất mục 9.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| name rỗng/quá 100 ký tự; position âm hoặc lớn hơn số cột hiện có | 400 | VALIDATION_ERROR |
| Actor là Member | 403 | FORBIDDEN |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | RESOURCE_NOT_FOUND |
| Project hoặc Board không ACTIVE | 409 | PROJECT_NOT_ACTIVE / BOARD_NOT_ACTIVE |
| (Đề xuất) Board đã đạt số cột tối đa (cấu hình, mặc định 20) | 422 | COLUMN_LIMIT_EXCEEDED |

**Dữ liệu và sự kiện**

Ghiboard_columns(1 dòng) và có thể cập nhậtpositioncủa các cột phía sau. Không phát sự kiện.

**Ví dụ response**

201 Created

{ "data": { "id": 7, "boardId": 1, "name": "Review", "position": 2 },

"columns": [ { "id": 1, "position": 0 }, { "id": 2, "position": 1 },

{ "id": 7, "position": 2 }, { "id": 3, "position": 3 } ] }

## UC-B07 — Đổi tên Column

| Actor | Project Owner |
| --- | --- |
| Mô tả | Owner sửa tên một cột. Không đổi position và không ảnh hưởng Task trong cột. |
| Kích hoạt | Owner sửa tên trên tiêu đề cột. |
| Điều kiện trước | Owner có membership ACTIVE; Project ACTIVE; Board chứa Column đang ACTIVE. |
| Điều kiện sau | board_columns.name được cập nhật. Task giữ nguyên vì chỉ tham chiếu column_id. |
| Quy tắc áp dụng | BR-B04, BR-B05 |
| API | PATCH /api/v1/columns/{columnId} — body { "name": "..." } |

**Luồng chính**

Hệ thống nạp Column theocolumnId, suy ra Board và Project, rồi chạy C1–C4.

Validatename1–100 ký tự sau khi cắt khoảng trắng.

Cập nhậtname; cập nhậtboards.updated_atcủa Board chứa cột (để các client biết cấu trúc đã đổi).

Trả Column sau khi cập nhật.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| name rỗng/quá dài | 400 | VALIDATION_ERROR |
| Actor là Member | 403 | FORBIDDEN |
| Column không tồn tại hoặc actor không phải thành viên ACTIVE của Project chứa Column | 404 | RESOURCE_NOT_FOUND |
| Project hoặc Board không ACTIVE | 409 | PROJECT_NOT_ACTIVE / BOARD_NOT_ACTIVE |

**Dữ liệu và sự kiện**

Ghiboard_columns.namevàboards.updated_at. Không phát sự kiện.

## UC-B08 — Sắp xếp lại Column

| Actor | Project Owner |
| --- | --- |
| Mô tả | Owner kéo thả để đổi thứ tự các cột của Board. Client gửi toàn bộ danh sách columnId theo thứ tự mới. |
| Kích hoạt | Owner kéo thả một cột sang vị trí khác. |
| Điều kiện trước | Owner có membership ACTIVE; Project ACTIVE; Board ACTIVE. |
| Điều kiện sau | Các cột có position 0…n−1 đúng thứ tự trong danh sách gửi lên; không có hai cột trùng vị trí ở bất kỳ thời điểm nào được commit. |
| Quy tắc áp dụng | BR-B04, BR-B05, BR-B06, BR-B07 |
| API | PUT /api/v1/boards/{boardId}/columns/order — body { "columnIds": [3, 1, 2] } |

**Luồng chính**

Hệ thống chạy C1–C4.

Validate: danh sách không rỗng, không có phần tử trùng.

Mở transaction và**khóa dòng boards**.

Đọc tậpcolumn_idhiện có của Board; so sánh với danh sách gửi lên. Hai tập phải**bằng nhau**(không thiếu, không thừa, không thuộc Board khác).

Cập nhậtpositiontheo**hai pha**để không vi phạmUQ_board_columns_board_positiongiữa chừng (mục 7.2): pha 1 đẩy mọi cột lên vùng vị trí tạm; pha 2 đặt vị trí cuối cùng 0…n−1.

Commit và trả danh sách cột theo thứ tự mới.

**Luồng thay thế**

**Thứ tự không đổi:**vẫn thành công, kết quả giữ nguyên.

**Client có danh sách cũ**(có người vừa thêm/xóa cột): danh sách không khớp tập hiện tại nên bị từ chối; frontend tải lại Board (UC-B03) rồi cho người dùng thử lại.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Danh sách rỗng, trùng phần tử, thiếu/thừa Column so với hiện tại, hoặc chứa Column của Board khác | 400 | VALIDATION_ERROR (chi tiết trong details) |
| Actor là Member | 403 | FORBIDDEN |
| Board không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | RESOURCE_NOT_FOUND |
| Project hoặc Board không ACTIVE | 409 | PROJECT_NOT_ACTIVE / BOARD_NOT_ACTIVE |

**Dữ liệu và sự kiện**

Ghiboard_columns.positioncủa toàn bộ cột vàboards.updated_at. Không phát sự kiện. Task không bị ảnh hưởng vì chỉ lưucolumn_id.

## UC-B09 — Xóa Column

| Actor | Project Owner |
| --- | --- |
| Mô tả | Owner xóa một cột đang rỗng (không còn Task). Hệ thống phải hỏi Task Service vì Task nằm ở database khác. |
| Kích hoạt | Owner chọn “Xóa cột”. |
| Điều kiện trước | Owner có membership ACTIVE; Project ACTIVE; Board ACTIVE; Column không còn Task. |
| Điều kiện sau | Dòng board_columns bị xóa; các cột còn lại được đánh lại position 0…n−1. |
| Quy tắc áp dụng | BR-B04, BR-B05, BR-B06, BR-B07, BR-B08 |
| API | DELETE /api/v1/columns/{columnId} → 204 No Content |

**Luồng chính**

Hệ thống chạy C1–C4.

Gọi Task Service:GET /internal/columns/{columnId}/task-count(timeout ngắn, ví dụ 2 giây). Lời gọi này nằm**ngoài**transaction để không giữ khóa DB trong lúc chờ mạng.

Nếu số Task lớn hơn 0: từ chối.

Mở transaction,**khóa dòng boards**, xác nhận Column vẫn còn tồn tại, xóa Column.

Đánh lạipositioncác cột còn lại thành 0…n−1 (mục 7.2).

(Đề xuất) Ghioutbox_eventsloạiCOLUMN_DELETED(boardId,columnId) để Task Service phát hiện Task mồ côi nếu có (rủi ro R1).

Commit và trả 204.

**Luồng thay thế**

**Xóa lại Column đã xóa:**trả 404, không gây lỗi khác.

**Xóa cột cuối cùng của Board:**được phép (Board khi đó chưa tạo được Task cho đến khi thêm cột mới). Xem mục 9.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Column còn Task | 409 | COLUMN_NOT_EMPTY |
| Không hỏi được Task Service (lỗi/timeout) — không xóa | 503 | DEPENDENCY_UNAVAILABLE |
| Actor là Member | 403 | FORBIDDEN |
| Column không tồn tại hoặc actor không phải thành viên ACTIVE | 404 | RESOURCE_NOT_FOUND |
| Project hoặc Board không ACTIVE | 409 | PROJECT_NOT_ACTIVE / BOARD_NOT_ACTIVE |

**Dữ liệu và sự kiện**

Ghi: xóa 1 dòngboard_columns, cập nhậtpositioncác cột còn lại,boards.updated_at, và (đề xuất) 1 dòngoutbox_events.

**Điểm cần lưu ý:**giữa lúc kiểm tra số Task và lúc xóa, một Task mới vẫn có thể được tạo vào Column đó (hai database khác nhau, không có giao dịch chung). Xem rủi ro R1 ở mục 9.

## UC-B10 — Xác minh Board và Column cho Task Service

| Actor | Task Service (hệ thống nền) |
| --- | --- |
| Mô tả | Task Service không có dữ liệu Board nên mỗi lần tạo hoặc di chuyển Task phải xác minh Board, Column và trạng thái ghi được. Đây là API nội bộ, không công khai qua Gateway. |
| Kích hoạt | Task Service xử lý tạo Task (UC-15) hoặc di chuyển Task (UC-18). |
| Điều kiện trước | Lời gọi mang service credential hợp lệ trên kênh nội bộ. |
| Điều kiện sau | Không thay đổi dữ liệu. |
| Quy tắc áp dụng | BR-B01, BR-B03, BR-B04 |
| API | GET /internal/boards/{boardId} |

**Luồng chính**

Task Service đã xác thực người dùng và kiểm tra quyền thành viên của họ (theo snapshot); Board Service**không**kiểm tra quyền người dùng ở API này.

Board Service xác thực service credential của lời gọi.

Nạp Board theoboardIdvà Project chứa nó.

TrảprojectId,boardStatus,projectStatusvà danh sách Column (id,name,position).

Task Service so khớp: Board thuộc đúng Project của Task; Column đích nằm trong danh sách;boardStatusvàprojectStatusđều ACTIVE. Nếu một điều kiện sai thì Task Service từ chối theo mã lỗi của nó.

**Luồng thay thế**

**Bộ nhớ đệm:**Task Service chỉ được cache**cấu trúc Column**với TTL rất ngắn.**Không cache**boardStatus/projectStatustrên đường ghi, nếu không Board đã đóng vẫn nhận Task.

**Ngoại lệ và mã lỗi**

| Tình huống | HTTP | Mã lỗi |
| --- | --- | --- |
| Board không tồn tại | 404 | RESOURCE_NOT_FOUND (Task Service ánh xạ thành 422 INVALID_COLUMN_TARGET khi tạo/di chuyển Task) |
| Thiếu hoặc sai service credential | 401 | UNAUTHENTICATED |
| Board Service quá tải/không sẵn sàng | 503 | DEPENDENCY_UNAVAILABLE (Task Service trả 503, không ghi Task) |

**Dữ liệu và sự kiện**

Chỉ đọcboards,board_columns,projects.

**Ví dụ response**

200 OK

{ "boardId": 1, "projectId": 1, "boardStatus": "ACTIVE", "projectStatus": "ACTIVE",

"columns": [ { "id": 1, "name": "Cần làm (Todo)", "position": 0 },

{ "id": 2, "name": "Đang xử lý (In Progress)", "position": 1 },

{ "id": 3, "name": "Hoàn thành (Done)", "position": 2 } ] }

# 6. Luồng liên service

## 6.1 Tạo hoặc di chuyển Task (Task Service → Board Service)

| Bước | Bên thực hiện | Nội dung |
| --- | --- | --- |
| 1 | Task Service | Nhận yêu cầu tạo/di chuyển Task; xác thực người dùng và kiểm tra membership. |
| 2 | Task Service → Board Service | Gọi UC-B10 với boardId. |
| 3 | Task Service | Kiểm tra Column thuộc Board; cả Board và Project đều ACTIVE. |
| 4 | Task Service | Ghi cards trong transaction của mình; sai điều kiện thì trả 422 INVALID_COLUMN_TARGET, 409 PROJECT_NOT_ACTIVE hoặc BOARD_NOT_ACTIVE. |

## 6.2 Xóa Column (Board Service → Task Service)

| Bước | Bên thực hiện | Nội dung |
| --- | --- | --- |
| 1 | Board Service | Kiểm tra quyền và trạng thái (C1–C4). |
| 2 | Board Service → Task Service | GET /internal/columns/{columnId}/task-count. |
| 3 | Task Service | Đếm cards theo column_id (dùng được chỉ mục IX_cards_column_position) và trả số lượng. |
| 4 | Board Service | Số lượng bằng 0 thì xóa Column và đánh lại vị trí; lớn hơn 0 thì 409 COLUMN_NOT_EMPTY; không hỏi được thì 503 và không xóa. |

## 6.3 Loại thành viên (Project Service → Task Service)

Khi một thành viên bị loại hoặc tự rời Project, sự kiệnMEMBER_REMOVEDmang theoboardIdslà**danh sách mọi Board của Project**. Project Service đọc danh sách này từ bảngboards(cùng database) ngay trong transaction loại thành viên rồi ghi vàooutbox_events. Task Service dùng danh sách để gỡ assignment trên các Board đó. Board nào được tạo sau thời điểm này không có assignment của người bị loại nên không cần xử lý.

## 6.4 Đổi trạng thái Board hoặc Project

Không phát sự kiện riêng cho Board. Task Service**kéo**trạng thái hiện hành qua UC-B10 mỗi lần ghi Task, nên hiệu lực của việc đóng/mở Board đến ngay ở lần ghi kế tiếp, không có độ trễ do hàng đợi. Với Project, sự kiệnPROJECT_STATUS_CHANGED(tài liệu v1.2) chỉ phục vụ các nhu cầu bổ sung như thông báo.

# 7. Dữ liệu liên quan và mẫu truy vấn

## 7.1 Bảng và ràng buộc trong SQL

| Bảng | Cột / ràng buộc | Ghi chú cho Board Service |
| --- | --- | --- |
| boards | id BIGINT identity PK; project_id FK → projects; name VARCHAR(255); board_key VARCHAR(20); status mặc định ACTIVE, CHECK ∈ {ACTIVE, COMPLETED, CANCELLED}; created_by_user_id; created_at, updated_at. UNIQUE (project_id, board_key). | Là dòng dùng để khóa khi thao tác Column. name dùng VARCHAR (không phải NVARCHAR) nên tên có dấu tiếng Việt cần lưu ý collation/kiểu (mục 9, R3). |
| board_columns | id BIGINT identity PK; board_id FK → boards; name VARCHAR(100); position INT, CHECK ≥ 0. UNIQUE (board_id, position). | Không có cột thời gian, không có cột WIP, không có cột đánh dấu “cột hoàn thành”. |
| projects | status CHECK ∈ {ACTIVE, COMPLETED, CANCELLED}. | Đọc để kiểm tra điều kiện ghi. |
| project_memberships | PK (project_id, user_id); role, status. | Đọc để kiểm tra quyền khi Redis không có snapshot. |

## 7.2 Mẫu xử lý vị trí Column (minh họa)

| Lưu ý Các câu lệnh dưới đây là mẫu minh họa cho logic cần cài đặt, chưa được chạy thử trên SQL Server trong lúc soạn tài liệu. Cần kiểm thử trên database thật, đặc biệt các trường hợp hai request đồng thời. |
| --- |

**Khóa Board trước khi đổi cột (BR-B07)**

BEGIN TRAN;

SELECT id FROM boards WITH (UPDLOCK, ROWLOCK) WHERE id = @boardId;

**Sắp xếp / đánh lại vị trí theo hai pha (UC-B06, B08, B09)**

-- Pha 1: đẩy toàn bộ cột lên vùng vị trí tạm, không trùng vùng 0..n-1

UPDATE board_columns SET position = position + 100000 WHERE board_id = @boardId;

-- Pha 2: đặt vị trí cuối cùng theo thứ tự mới (ordered_ids = bảng id, new_position)

UPDATE bc SET bc.position = o.new_position

FROM board_columns bc

JOIN @ordered o ON o.column_id = bc.id

WHERE bc.board_id = @boardId;

COMMIT;

Thứ tự mới (@ordered) do ứng dụng tính: lấy danh sách cột hiện có, áp dụng thao tác (chèn, xóa, hoán đổi), rồi gánnew_position= 0…n−1. Số cột n thường chỉ vài đến vài chục nên chi phí cập nhật toàn bộ cột là không đáng kể.

## 7.3 Sự kiện Board Service có thể ghi vào Outbox

| Sự kiện | Khi nào | Trạng thái |
| --- | --- | --- |
| MEMBER_REMOVED (mang boardIds) | Loại thành viên / tự rời Project (module membership dùng danh sách Board) | Bắt buộc (tài liệu v1.2) |
| COLUMN_DELETED | Sau khi xóa Column (UC-B09) | Đề xuất: giúp phát hiện Task mồ côi |
| BOARD_STATUS_CHANGED | Sau khi đổi trạng thái Board (UC-B05) | Không bắt buộc; hiện Task Service kéo trạng thái |

# 8. Tiêu chí nghiệm thu

| Mã | Use Case | Điều kiện nghiệm thu |
| --- | --- | --- |
| AC-B01 | UC-B01 | Owner tạo Board thành công kèm 3 cột mặc định có position 0, 1, 2; Member gọi bị 403; người ngoài Project bị 404. |
| AC-B02 | UC-B01 | boardKey trùng trong cùng Project bị 409 BOARD_KEY_ALREADY_EXISTS, kể cả khi hai request gửi đồng thời (chỉ một thành công); cùng boardKey ở Project khác tạo được. |
| AC-B03 | UC-B01, B05, B06 | Khi Project COMPLETED/CANCELLED, mọi thao tác ghi Board/Column (kể cả đổi trạng thái Board) bị 409 PROJECT_NOT_ACTIVE; các API xem vẫn hoạt động. |
| AC-B04 | UC-B02 | Danh sách chỉ hiển thị cho thành viên ACTIVE; lọc theo status và phân trang đúng; người đã bị loại khỏi Project nhận 404. |
| AC-B05 | UC-B03 | Phản hồi có Column theo position tăng dần, kèm writable và myRole đúng; không chứa Task. |
| AC-B06 | UC-B04 | Owner đổi được tên; gửi boardKey khác giá trị hiện tại bị 400; Board không ACTIVE bị 409 BOARD_NOT_ACTIVE. |
| AC-B07 | UC-B05 | ACTIVE → COMPLETED/CANCELLED và ngược lại về ACTIVE thành công; COMPLETED ↔ CANCELLED trực tiếp bị 409 INVALID_STATUS_TRANSITION; sau khi đóng Board, Task Service từ chối tạo/di chuyển Task trên Board đó. |
| AC-B08 | UC-B06 | Thêm cột ở cuối và ở giữa đều cho position liên tục 0…n−1; hai Owner thêm cột đồng thời không gây lỗi unique. |
| AC-B09 | UC-B07 | Đổi tên cột không làm đổi position hay mất Task trong cột. |
| AC-B10 | UC-B08 | Hoán đổi hai cột thành công không vi phạm unique; danh sách thiếu/thừa/trùng columnId bị 400 và dữ liệu không đổi. |
| AC-B11 | UC-B09 | Xóa cột rỗng thành công và position các cột còn lại liên tục; cột còn Task bị 409 COLUMN_NOT_EMPTY. |
| AC-B12 | UC-B09 | Khi Task Service không trả lời, Column không bị xóa và API trả 503 DEPENDENCY_UNAVAILABLE. |
| AC-B13 | UC-B10 | API nội bộ trả đúng trạng thái Board/Project và danh sách Column; không truy cập được từ Gateway công khai; thiếu service credential bị 401. |
| AC-B14 | Toàn bộ | Member gọi mọi API ghi bị 403; người không phải thành viên luôn nhận 404 (không lộ sự tồn tại); lỗi trả đúng cấu trúc chuẩn có correlationId. |

# 9. Điểm đề xuất cần xác nhận và rủi ro

## 9.1 Điểm đề xuất

Những điểm sau SQL không quy định nên tài liệu tạm chọn như dưới đây. Bạn chốt hoặc sửa trước khi code.

| # | Nội dung | Đề xuất trong tài liệu |
| --- | --- | --- |
| 1 | Định dạng và tính bất biến của board_key | Chữ thường, số, dấu gạch ngang, 2–20 ký tự; không đổi sau khi tạo. |
| 2 | Mở lại Board đã COMPLETED/CANCELLED | Cho phép, chỉ Owner, tương tự quyết định đã chốt cho Project; không chuyển thẳng COMPLETED ↔ CANCELLED. |
| 3 | Giới hạn số cột mỗi Board | Tối đa 20 (cấu hình), vượt thì 422 COLUMN_LIMIT_EXCEEDED. Đây là mã lỗi mới, chưa có trong danh mục mã lỗi của tài liệu v1.2. |
| 4 | Trùng tên Column trong cùng Board | Cho phép (DB không ràng buộc). Nếu muốn cấm cần thêm unique (board_id, name) để chống race. |
| 5 | Xóa cột cuối cùng của Board | Cho phép. |
| 6 | Xóa Board | Không có trong MVP; dùng CANCELLED. |
| 7 | Cột mặc định khi tạo Board | Todo, In Progress, Done, lấy từ cấu hình để đổi được không cần sửa code. |
| 8 | Phát COLUMN_DELETED qua Outbox | Nên có để phát hiện Task mồ côi (R1). Không bắt buộc cho MVP. |

## 9.2 Rủi ro đã biết

| Mã | Rủi ro | Giảm thiểu |
| --- | --- | --- |
| R1 | Task mồ côi sau khi xóa Column. Hai database khác nhau nên giữa lúc kiểm tra số Task và lúc xóa Column, một Task mới vẫn có thể được tạo vào Column đó. Task đó sẽ trỏ tới column_id không còn. | Task Service luôn xác minh Column qua UC-B10 khi tạo/di chuyển (thu hẹp cửa sổ). Phát COLUMN_DELETED để Task Service phát hiện và xử lý Task mồ côi (chuyển về cột khác hoặc báo Owner). Frontend bỏ qua Task có columnId không tồn tại (xem UC-B03). |
| R2 | Board Service là điểm phụ thuộc của việc ghi Task. Mỗi lần ghi Task đều gọi UC-B10; Board Service chậm thì ghi Task chậm hoặc 503. | Đặt timeout ngắn; cache cấu trúc Column với TTL rất ngắn; không cache trạng thái. Theo dõi độ trễ API nội bộ. |
| R3 | Kiểu VARCHAR cho name của Board/Column. SQL dùng VARCHAR thay vì NVARCHAR trong khi dữ liệu mẫu có tên tiếng Việt có dấu (ví dụ “Cần làm (Todo)”). Ký tự có dấu có thể bị mất hoặc biến dạng nếu collation database không hỗ trợ. | Đổi boards.name và board_columns.name sang NVARCHAR, hoặc xác nhận collation/mã hóa của database và kiểm thử ký tự có dấu trước khi triển khai. (Các bảng khác như notifications.title đã dùng NVARCHAR.) |
| R4 | Hai Owner sửa cùng lúc. Nếu chỉ kiểm tra trước rồi ghi, các thao tác Column có thể vi phạm unique vị trí. | Khóa dòng boards trong mọi thao tác Column (BR-B07); bắt lỗi unique từ DB để trả 409 thay vì 500. |
