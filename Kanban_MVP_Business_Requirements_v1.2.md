**WORKFLOWHUB**

**TÀI LIỆU ĐẶC TẢ NGHIỆP VỤ MVP**

Use Case • Phân quyền • API Contract • Quy tắc nghiệp vụ

Hệ thống quản lý công việc Kanban — Java Spring Microservices

**Phiên bản 1.2  |  Đồng bộ với kanban_mssql_databases.sql  |  07/11/2026**

# Kiểm soát tài liệu

| Thuộc tính | Giá trị |
| --- | --- |
| Tên tài liệu | WorkflowHub — Business Requirements & API Baseline |
| Phiên bản | 1.2 (thay thế 1.1 và 1.0) |
| Nguyên tắc chính của bản này | Schema SQL là nguồn chuẩn. Mọi quy tắc nghiệp vụ trong tài liệu phải thực hiện được bằng các bảng, ràng buộc và chỉ mục đã có trong kanban_mssql_databases.sql. Phần nào DB chưa hỗ trợ được chuyển sang mục “Ngoài phạm vi MVP” (mục 15) hoặc ghi rõ là đề xuất. |
| Công nghệ | Java Spring Boot microservices; API Gateway; Eureka (service discovery); RabbitMQ (sự kiện bất đồng bộ, Outbox/Inbox); Redis (snapshot quyền); SQL Server (database-per-service) |
| Đối tượng đọc | BA, Developer, Tester, Technical Lead |
| Lưu ý | Path và payload API là đề xuất thống nhất để triển khai; có thể điều chỉnh theo repository thực tế. Giá trị trạng thái nào DB chưa có CHECK được đánh dấu “đề xuất”. |

## Thay đổi trong phiên bản 1.2

| Hạng mục | Quyết định / thay đổi | Vị trí trong tài liệu |
| --- | --- | --- |
| Mở lại Project | Owner được chuyển Project COMPLETED hoặc CANCELLED về ACTIVE. Không chuyển thẳng COMPLETED ↔ CANCELLED (phải qua ACTIVE). | BR-17, UC-07, mục 3.3, 6.2, 6.3, 9.2, 10 |
| Xóa Task | Owner được xóa vĩnh viễn Task (cascade xóa assignee và bình luận); phát TASK_DELETED để dọn thông báo liên quan. | BR-43, UC-25, mục 6.2, 9.4, 10, AC-22 |
| Outbox/Inbox cho Project Service | Bổ sung outbox_events và inbox_events vào kanban_board_db, cùng schema với Task Service. Script nằm ở file kanban_board_outbox_inbox.sql, chưa gộp vào kanban_mssql_databases.sql. | BR-36, mục 12.2, 13.3, 15.3, AC-23 |

## Thay đổi bản 1.1 so với phiên bản 1.0

Bảng dưới liệt kê các điểm bản 1.0 mâu thuẫn với SQL và cách bản 1.1 xử lý.

| Hạng mục | Bản 1.0 | Bản 1.1 (theo SQL) |
| --- | --- | --- |
| Số service | 7 service (thêm Board Service, Activity/Audit Service) | 4 service: User, Project (gồm Board), Task, Notification |
| Project role | PROJECT_MANAGER, MEMBER, VIEWER; nhiều PM | OWNER, MEMBER; tối đa một OWNER ACTIVE mỗi Project (unique index). Bỏ VIEWER |
| Luật “PM cuối cùng” | Không cho PM cuối cùng rời/hạ quyền | Owner duy nhất không được rời/bị loại; phải chuyển quyền Owner trước |
| Thêm thành viên | Thêm trực tiếp, không cần Accept | Lời mời (invitations): PENDING → ACCEPTED / REJECTED / CANCELLED |
| Trạng thái Project | ACTIVE / ARCHIVED, có Restore | ACTIVE / COMPLETED / CANCELLED; COMPLETED và CANCELLED là chỉ đọc |
| Trạng thái Board | Không có | ACTIVE / COMPLETED / CANCELLED; có board_key duy nhất trong Project |
| WIP limit | Cảnh báo mềm, PM cấu hình | Ngoài MVP — board_columns không có cột WIP |
| Thuộc tính Task | priority, labels, createdBy, version, assignee đơn | title, description, status, position, due_date; không có priority/labels/created_by/version |
| Assignee | Một assigneeId trong Task | Bảng task_assignees; MVP chỉ dùng một assignee primary; lưu membership_version làm hàng rào |
| Khi thành viên bị loại | Giữ Task, PM phân công lại thủ công | Task giữ lại; assignment của người đó được gỡ tự động bất đồng bộ (cleanup job + fence) |
| Xung đột phiên bản Task | VERSION_CONFLICT (optimistic lock) | MVP không khóa lạc quan trên Task (không có cột version) |
| History / Audit / Support Mode | Có activity_logs, audit_logs, support session | Ngoài MVP — DB chưa có bảng tương ứng |
| Thông báo | In-app và/hoặc email, retry email | Chỉ in-app; notifications không có trạng thái gửi email |
| Phân quyền | Kiểm tra membership trực tiếp | Kiểm tra qua snapshot trong Redis có version (auth_version, membership_version, authorization_version) |
| System role | Một role trên tài khoản | Bảng nhiều-nhiều user_system_roles (Admin mẫu giữ cả USER và SYSTEM_ADMIN); lưu granted_by |
| ID | UUID hoặc “thống nhất theo DB” | User = UUID; Project, Board, Column, Task (card), Comment, Notification = BIGINT |

# Mục lục

Tổng quan và phạm vi MVP

Kiến trúc, service và dữ liệu sở hữu

Thuật ngữ và mô hình phân quyền

Actor

Quy tắc nghiệp vụ thống nhất

Ma trận phân quyền

Đặc tả Use Case

Quy ước API

Danh mục API theo service

Sự kiện (RabbitMQ)

Mã lỗi

Mô hình dữ liệu (theo SQL)

Bảo mật, nhất quán và vận hành

Tiêu chí nghiệm thu MVP

Ngoài phạm vi MVP, quyết định mở và khuyến nghị DB

# 1. Tổng quan và phạm vi MVP

WorkflowHub là ứng dụng web quản lý công việc theo mô hình Kanban: người dùng tạo Project, mời thành viên, tổ chức Board/Column, tạo và phân công Task, bình luận và nhận thông báo. MVP ưu tiên luồng nghiệp vụ cốt lõi, phân quyền rõ ràng và kiểm tra quyền ở backend; không xây permission engine phức tạp.

## 1.1 Trong phạm vi MVP

**Tài khoản:**đăng ký, đăng nhập, đăng xuất, xem/sửa hồ sơ (họ tên).

**Project:**tạo Project (creator tự thành Owner), cập nhật tên, đổi trạng thái ACTIVE/COMPLETED/CANCELLED và mở lại về ACTIVE.

**Thành viên:**mời bằng lời mời, chấp nhận/từ chối/hủy lời mời, loại thành viên, tự rời Project, chuyển quyền Owner.

**Board và Column:**tạo/sửa Board (cóboard_key), tạo/đổi tên/sắp xếp/xóa Column.

**Task:**tạo, xem, sửa (tiêu đề, mô tả, hạn), gán/tự nhận/bỏ gán, di chuyển giữa Column trong cùng Board, bình luận, xóa Task (Owner).

**Thông báo in-app:**thông báo khi được gán Task; xem danh sách, đếm chưa đọc, đánh dấu đã đọc.

**System Admin:**tìm kiếm tài khoản, khóa/mở khóa, cấp/thu hồi system role.

**Hạ tầng nghiệp vụ:**đồng bộ snapshot quyền sang Redis, Outbox/Inbox cho RabbitMQ, dọn assignment khi thành viên rời Project.

## 1.2 Ngoài phạm vi MVP

Các mục sau chưa có bảng/cột trong SQL nên không thuộc MVP: WIP limit, Viewer, priority/labels/created_by/version cho Task, Activity/History, Audit log và Support Mode, email, upload attachment, xóa vĩnh viễn Project/User. Chi tiết và khuyến nghị ở mục 15.

# 2. Kiến trúc, service và dữ liệu sở hữu

| Thành phần | Database | Sở hữu dữ liệu / trách nhiệm | Use Case |
| --- | --- | --- | --- |
| API Gateway | — | Routing, xác thực token, correlation ID, rate limit. Không là lớp phân quyền duy nhất. | Mọi request |
| Eureka | — | Service discovery cho các service. | — |
| User Service | kanban_user_db | Tài khoản, mật khẩu băm, trạng thái, system role, auth_version, job đồng bộ snapshot tài khoản sang Redis. | UC-01–04, 21–23 |
| Project Service (gồm Board) | kanban_board_db | Project, membership, lời mời, Board, Column, version phân quyền, job đồng bộ snapshot membership sang Redis, Outbox/Inbox (bổ sung ở v1.2). | UC-05–14, 23 |
| Task Service | kanban_task_db | Task (bảng cards), assignee, bình luận, hàng rào và job dọn assignment. | UC-15–19, 24, 25 |
| Notification Service | kanban_notification_db | Thông báo in-app của từng người dùng; tiêu thụ sự kiện. | UC-20 |
| Redis | — | Snapshot quyền: trạng thái/role tài khoản và role membership theo Project, kèm version. | UC-23 |
| RabbitMQ | — | Truyền sự kiện giữa service; mỗi service dùng Outbox để phát và Inbox để chống xử lý trùng. | Mục 10 |

## 2.1 Nguyên tắc

**Database-per-service, không FK xuyên database.**Các ID bên ngoài (user, project, board, column) chỉ là tham chiếu scalar; tính hợp lệ được xác minh qua API nội bộ hoặc sự kiện.

**Đồng bộ (HTTP)**cho thao tác cần phản hồi ngay: tạo Project, tạo/di chuyển Task, xác minh Column thuộc Board.

**Bất đồng bộ (RabbitMQ)**cho thông báo, đồng bộ hệ quả của thay đổi thành viên (dọn assignment). Sự kiện phát qua Outbox trong cùng transaction với thay đổi dữ liệu.

**Lỗi bất đồng bộ không rollback giao dịch đã thành công**(ví dụ lỗi tạo thông báo không hủy việc gán Task).

**Mỗi service tự kiểm tra quyền**dựa trên snapshot Redis và dữ liệu của chính nó; Gateway chỉ xác thực và định tuyến.

# 3. Thuật ngữ và mô hình phân quyền

| Thuật ngữ | Định nghĩa thống nhất |
| --- | --- |
| System role | Vai trò cấp toàn hệ thống, lưu ở user_system_roles. Giá trị: USER, SYSTEM_ADMIN. Một tài khoản có thể có nhiều role. |
| Project role | Vai trò trong một Project cụ thể, lưu ở project_memberships.role. Giá trị: OWNER, MEMBER. |
| Membership | Quan hệ User–Project, khóa chính (project_id, user_id). Trạng thái ACTIVE hoặc REMOVED; dòng không bị xóa khi người dùng rời Project. |
| Invitation | Lời mời tham gia Project. Trạng thái PENDING, ACCEPTED, REJECTED, CANCELLED. |
| Project / Board | Project là ranh giới truy cập dữ liệu. Board thuộc một Project, có board_key duy nhất trong Project. Cả hai có trạng thái ACTIVE, COMPLETED, CANCELLED. |
| Column | Cột của Board, có position ≥ 0 và duy nhất trong Board. |
| Task (card) | Đơn vị công việc, lưu ở bảng cards; nằm trong một Board và một Column, có thứ tự position. |
| Assignee | Người phụ trách Task, lưu ở task_assignees. MVP chỉ có một assignee primary. |
| auth_version | Số phiên bản quyền của tài khoản; tăng khi trạng thái hoặc system role thay đổi. |
| membership_version | Số phiên bản (generation) của membership một người trong một Project; tăng mỗi khi vào/rời/đổi role. Dùng làm hàng rào (fence) cho dọn assignment. |
| authorization_version | Số phiên bản quyền của một người trong Project; dùng để so sánh snapshot Redis cũ/mới. |
| Snapshot quyền | Bản sao quyền trong Redis để kiểm tra nhanh. Nguồn sự thật vẫn là database của service sở hữu. |
| Outbox / Inbox | Outbox: sự kiện chờ phát, ghi cùng transaction. Inbox: ghi nhận sự kiện đã xử lý theo (consumer_name, event_id) để chống trùng. |

## 3.1 System role

| Role | Mô tả | Quyền cơ bản |
| --- | --- | --- |
| USER | Tài khoản thông thường. | Dùng hệ thống, tạo Project; không có quyền quản trị. |
| SYSTEM_ADMIN | Quản trị hệ thống. Dữ liệu mẫu cho tài khoản này giữ cả USER. | Tìm kiếm tài khoản, khóa/mở khóa, cấp/thu hồi system role. Không tự có quyền trên dữ liệu Project. |

## 3.2 Project role

| Role | Mô tả | Quyền cơ bản |
| --- | --- | --- |
| OWNER | Người sở hữu Project; mỗi Project có tối đa một OWNER ACTIVE. | Toàn quyền quản trị Project: tên/trạng thái, lời mời, loại thành viên, chuyển Owner, Board/Column, cùng mọi quyền của Member. |
| MEMBER | Thành viên cộng tác. | Xem Project; tạo/sửa Task, gán/tự nhận, di chuyển, bình luận. |

| Quy tắc cốt lõi System role không thay thế Project role. Người dùng phải có membership ACTIVE trong Project thì mới đọc hoặc sửa dữ liệu của Project đó. Owner ở Project A không tự động là Owner ở Project B. |
| --- |

## 3.3 Vòng đời trạng thái

| Đối tượng | Trạng thái | Chuyển trạng thái |
| --- | --- | --- |
| Project | ACTIVE, COMPLETED, CANCELLED | ACTIVE → COMPLETED hoặc CANCELLED (Owner). COMPLETED/CANCELLED chỉ đọc. Owner được mở lại về ACTIVE (từ COMPLETED hoặc CANCELLED). Không chuyển thẳng COMPLETED ↔ CANCELLED, phải qua ACTIVE. |
| Board | ACTIVE, COMPLETED, CANCELLED | Do Owner đổi. Board không ACTIVE chỉ đọc (không tạo/di chuyển Task). |
| Membership | ACTIVE, REMOVED | Chấp nhận lời mời → ACTIVE. Rời/bị loại → REMOVED (ghi removed_at). Được mời lại → ACTIVE, dùng lại dòng cũ và tăng version. |
| Invitation | PENDING, ACCEPTED, REJECTED, CANCELLED | PENDING → ACCEPTED/REJECTED (người được mời) hoặc CANCELLED (Owner). Trạng thái cuối không đổi nữa. |
| Tài khoản | ACTIVE và trạng thái khóa (đề xuất: PENDING_ACTIVATION, ACTIVE, LOCKED) | DB chưa có CHECK cho users.status; cần chốt tập giá trị trước khi code (quyết định mở số 2). |
| Outbox / Inbox event | PENDING, PROCESSED, FAILED | Có CHECK trong DB. |

# 4. Actor

| Mã | Actor | Mô tả / phạm vi |
| --- | --- | --- |
| ACT-01 | Guest | Chưa đăng nhập; đăng ký hoặc đăng nhập. |
| ACT-02 | User | Đã xác thực, trạng thái ACTIVE, có system role USER (và/hoặc SYSTEM_ADMIN). Quyền Project xét riêng theo membership. |
| ACT-03 | Project Owner | User có role=OWNER, status=ACTIVE trong một Project cụ thể. |
| ACT-04 | Project Member | User có role=MEMBER, status=ACTIVE trong một Project cụ thể. |
| ACT-05 | Invitee | User đang có lời mời PENDING vào một Project; chưa là thành viên. |
| ACT-06 | System Admin | User có system role SYSTEM_ADMIN; quản trị tài khoản. |
| ACT-07 | Hệ thống nền | Worker đồng bộ snapshot, worker dọn assignment, publisher Outbox, consumer Inbox, Notification Service. |

*Owner và Member là vai trò theo Project, không phải loại tài khoản riêng. Một User có thể là Owner ở Project A và Member ở Project B.*

# 5. Quy tắc nghiệp vụ thống nhất

## 5.1 Tài khoản và system role

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-01 | Đăng ký | Email duy nhất (chuẩn hóa chữ thường). Tài khoản mới được gán system role USER và auth_version = 1. Không bao giờ trả password_hash qua API. |
| BR-02 | Nhiều system role | Một tài khoản có thể có nhiều dòng trong user_system_roles; mỗi dòng ghi granted_by (NULL cho tài khoản khởi tạo hệ thống). |
| BR-03 | Không tự nâng quyền | Người dùng không được tự đặt system role, status, id hay Project role qua request body. |
| BR-04 | auth_version | Mỗi lần status hoặc system role thay đổi, auth_version tăng 1 và hệ thống tạo job đồng bộ snapshot cho (account_id, auth_version); cặp này là duy nhất. |
| BR-05 | Tài khoản bị khóa | Tài khoản không ở trạng thái hoạt động không thực hiện được nghiệp vụ mới sau khi snapshot mới có hiệu lực; token mang auth_version cũ bị từ chối. |
| BR-06 | Quản trị tài khoản | Chỉ SYSTEM_ADMIN khóa/mở khóa và cấp/thu hồi system role. Không được khóa hoặc thu hồi SYSTEM_ADMIN của quản trị viên cuối cùng. |
| BR-07 | Admin không mặc nhiên vào Project | SYSTEM_ADMIN không tự có quyền đọc/sửa dữ liệu Project; chỉ có quyền khi là thành viên của Project đó. |

## 5.2 Project và thành viên

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-08 | Tạo Project | Mọi User ACTIVE được tạo Project. Trong một transaction hệ thống tạo Project (ACTIVE), membership OWNER ACTIVE của creator và bản ghi version. Không được tồn tại Project không có Owner. |
| BR-09 | Một Owner | Mỗi Project có tối đa một OWNER ACTIVE (unique index có điều kiện). Project role chỉ gồm OWNER và MEMBER. |
| BR-10 | Membership duy nhất | Mỗi cặp (project_id, user_id) chỉ có một dòng membership. Khi rời/bị loại, dòng chuyển sang REMOVED và ghi removed_at (không xóa). Khi được thêm lại, dùng lại dòng đó: ACTIVE, removed_at = NULL, tăng version. |
| BR-11 | Thêm bằng lời mời | Thành viên chỉ vào Project qua lời mời. Chỉ Owner mời. Chỉ User ACTIVE đã tồn tại mới được mời. Mỗi (project, user) chỉ có tối đa một lời mời PENDING. |
| BR-12 | Phản hồi lời mời | Chỉ người được mời được chấp nhận/từ chối; chỉ Owner hủy. Chấp nhận tạo hoặc kích hoạt lại membership với role MEMBER. Lời mời đã ở trạng thái cuối không đổi được. |
| BR-13 | Không mời trùng | Không mời người đang là thành viên ACTIVE hoặc đang có lời mời PENDING. Người từng REJECTED, CANCELLED hoặc REMOVED được mời lại bằng lời mời mới. |
| BR-14 | Owner không rời | Owner không được rời hoặc bị loại. Muốn rời phải chuyển quyền Owner cho một MEMBER ACTIVE trước. |
| BR-15 | Chuyển quyền Owner | Thực hiện trong một transaction: hạ Owner cũ xuống MEMBER, nâng người nhận lên OWNER, tăng version của cả hai. Người nhận phải là MEMBER ACTIVE. |
| BR-16 | Tự rời Project | Member được tự rời Project; Owner loại được Member. Cả hai đều chuyển membership sang REMOVED và phát sự kiện MEMBER_REMOVED. |
| BR-17 | Trạng thái Project | Chỉ khi Project ACTIVE mới được thay đổi dữ liệu (thành viên, Board, Column, Task, bình luận). COMPLETED và CANCELLED chỉ đọc; chỉ Owner đổi được trạng thái Project. Owner được mở lại Project COMPLETED/CANCELLED về ACTIVE; đây là thao tác ghi duy nhất được phép khi Project không ACTIVE, và phải phát PROJECT_STATUS_CHANGED. |
| BR-18 | Project chỉ có tên | Project có name bắt buộc (tối đa 255 ký tự); chưa có mô tả. |

## 5.3 Board và Column

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-19 | Board | Chỉ Owner tạo/sửa/đổi trạng thái Board. board_key duy nhất trong Project (đề xuất định dạng chữ thường, số, dấu gạch, tối đa 20 ký tự). Board không ACTIVE chỉ đọc. |
| BR-20 | Column | Chỉ Owner quản lý Column. position ≥ 0 và duy nhất trong Board; sắp xếp lại phải thực hiện trong một transaction và không để vi phạm ràng buộc giữa chừng. |
| BR-21 | Xóa Column | Chỉ xóa được Column không còn Task. Project Service hỏi Task Service số Task trong Column; còn Task thì từ chối. |
| BR-22 | Cột mặc định | Board mới có thể được tạo kèm các cột mặc định (ví dụ Todo, In Progress, Done) theo template cấu hình. |

## 5.4 Task

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-23 | Tạo Task | Owner và Member tạo Task trong Board ACTIVE của Project ACTIVE. title bắt buộc (tối đa 500 ký tự); description, due_date tùy chọn; status mặc định TODO; Task xếp cuối Column. |
| BR-24 | Quan hệ tài nguyên | Backend xác minh Board thuộc Project và Column thuộc Board qua Project Service; không tin projectId, boardId do client tự khai. |
| BR-25 | Sửa Task | Owner và Member sửa nội dung Task (title, description, due_date) của bất kỳ ai. Không đổi được id, project_id, board_id. |
| BR-26 | Di chuyển Task | Owner và Member di chuyển Task giữa các Column của cùng Board, kèm vị trí đích. Cập nhật column_id và position của các Task liên quan trong một transaction. Không hỗ trợ chuyển sang Board/Project khác. |
| BR-27 | Assignee | MVP có tối đa một assignee (primary) mỗi Task. Assignee phải là thành viên ACTIVE của Project. Gán lại thay thế assignee cũ. Lưu assigned_by_user_id, assigned_at và membership_version hiện tại của assignee. |
| BR-28 | Tự nhận | Owner và Member được tự nhận Task (assignee = chính mình). |
| BR-29 | Bình luận | Owner và Member bình luận. Nội dung bắt buộc. Bình luận lưu author_id và created_at; MVP không sửa/xóa bình luận. Bình luận được giữ nguyên khi tác giả rời Project. |
| BR-43 | Xóa Task | Chỉ Owner xóa Task, khi Project và Board ACTIVE. Xóa vĩnh viễn: task_assignees và card_comments bị xóa theo (ON DELETE CASCADE), không khôi phục được; giao diện phải cảnh báo trước khi xóa. Thứ tự position các Task còn lại trong Column phải hợp lệ sau khi xóa. Phát TASK_DELETED để Notification Service dọn thông báo có card_id tương ứng; thông báo còn sót chỉ hiển thị “Task đã bị xóa”, không gây lỗi. |

## 5.5 Khi thành viên rời Project

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-30 | Dữ liệu được giữ | Task và bình luận do/liên quan người đó không bị xóa. Chỉ assignment của họ bị gỡ. |
| BR-31 | Gỡ assignment bất đồng bộ | Task Service nhận MEMBER_REMOVED, tạo hàng rào (fence) theo (project, user, membership_version) và job dọn dẹp, rồi gỡ assignment của người đó trên các Board của Project theo lô và có thể chạy lại an toàn. |
| BR-32 | Fence chống gỡ nhầm | Job dọn dẹp chỉ gỡ assignment có membership_version nhỏ hơn hoặc bằng version trong sự kiện. Nếu người đó được thêm lại (version cao hơn) rồi được gán Task mới thì assignment mới không bị gỡ. |
| BR-33 | Chặn trong cửa sổ trễ | Không cho gán Task cho người đã REMOVED, kể cả khi job dọn dẹp chưa chạy xong. |

## 5.6 Thông báo và sự kiện

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-34 | Thông báo in-app | Khi Task được gán cho người khác, tạo thông báo loại ASSIGNED cho assignee. Không thông báo cho người tự nhận. Các loại thông báo khác chưa chốt (quyết định mở số 7). |
| BR-35 | Không rollback | Lỗi tạo thông báo hoặc lỗi tiêu thụ sự kiện không rollback giao dịch nghiệp vụ đã commit. |
| BR-36 | Outbox / Inbox | Mỗi service phát sự kiện (kể cả Project Service từ v1.2) ghi sự kiện vào outbox_events của database mình trong cùng transaction với thay đổi dữ liệu. Consumer ghi inbox_events theo (consumer_name, event_id) để không xử lý hai lần. Có retry bằng next_attempt_at. |
| BR-37 | Quyền đọc thông báo | Người dùng chỉ xem và đánh dấu đã đọc thông báo của chính mình. Không gửi nội dung nhạy cảm cho người đã mất quyền Project. |

## 5.7 Phân quyền và nhất quán

| Mã | Chủ đề | Quy tắc |
| --- | --- | --- |
| BR-38 | Backend là nguồn quyết định | Mọi service tự kiểm tra quyền; Gateway không phải lớp bảo vệ duy nhất. Gọi API trực tiếp không vượt được quyền chỉ vì frontend ẩn nút. |
| BR-39 | Snapshot Redis có version | Quyền được kiểm tra qua snapshot trong Redis. Thay đổi quyền có hiệu lực sau khi job đồng bộ hoàn tất; cần quy định TTL và hành vi khi Redis không có dữ liệu (quyết định mở số 5). Không bao giờ ghi snapshot có version thấp hơn đè snapshot có version cao hơn. |
| BR-40 | Không tin ID từ client | Actor luôn lấy từ token đã xác thực; không nhận actorId, role do client gửi. |
| BR-41 | Chống dò tài nguyên | Người không có quyền xem Project nhận 404 thay vì 403 để không lộ sự tồn tại; người có quyền xem nhưng không đủ quyền thao tác nhận 403. |
| BR-42 | Idempotency | API tạo mới có khả năng retry (tạo Task, bình luận, lời mời) nên hỗ trợ Idempotency-Key hoặc phát hiện trùng. |

# 6. Ma trận phân quyền

## 6.1 Cấp hệ thống

| Hành động | USER | SYSTEM_ADMIN |
| --- | --- | --- |
| Đăng ký, đăng nhập, xem/sửa hồ sơ của mình | Có | Có |
| Tạo Project | Có | Có |
| Tìm kiếm / xem tài khoản toàn hệ thống | Không | Có |
| Khóa / mở khóa tài khoản | Không | Có (không khóa Admin cuối cùng) |
| Cấp / thu hồi system role | Không | Có (không thu hồi SYSTEM_ADMIN cuối cùng) |
| Đọc hoặc sửa Project chỉ vì là Admin | Không | Không |
| Xem thông báo của người khác | Không | Không |

## 6.2 Cấp Project (khi Project ACTIVE)

| Hành động | Không phải thành viên | MEMBER | OWNER |
| --- | --- | --- | --- |
| Xem Project, Board, Column, Task, bình luận | Không (404) | Có | Có |
| Xem danh sách thành viên | Không | Có | Có |
| Cập nhật tên Project | Không | Không | Có |
| Đổi trạng thái / mở lại Project | Không | Không | Có |
| Mời thành viên, xem và hủy lời mời của Project | Không | Không | Có |
| Loại thành viên | Không | Không | Có (trừ chính Owner) |
| Tự rời Project | — | Có | Không (phải chuyển quyền Owner) |
| Chuyển quyền Owner | Không | Không | Có |
| Tạo/sửa/đổi trạng thái Board | Không | Không | Có |
| Tạo/đổi tên/sắp xếp/xóa Column | Không | Không | Có |
| Tạo Task, sửa nội dung Task | Không | Có | Có |
| Xóa Task | Không | Không | Có |
| Gán / tự nhận / bỏ gán Task | Không | Có | Có |
| Di chuyển Task | Không | Có | Có |
| Bình luận | Không | Có | Có |

*Ghi chú: lời mời của chính mình (xem hộp thư lời mời, chấp nhận, từ chối) do Invitee thực hiện, không phụ thuộc membership.*

## 6.3 Theo trạng thái Project

| Hành động | ACTIVE | COMPLETED / CANCELLED |
| --- | --- | --- |
| Xem Project, Board, Task, bình luận, thành viên (thành viên hiện tại) | Có | Có |
| Tạo/sửa/xóa/di chuyển/gán Task, bình luận | Có | Không (409 PROJECT_NOT_ACTIVE) |
| Mời, chấp nhận lời mời, loại thành viên, tự rời, chuyển Owner | Có | Không (409 PROJECT_NOT_ACTIVE) |
| Tạo/sửa Board, Column | Có | Không (409 PROJECT_NOT_ACTIVE) |
| Đổi trạng thái Project (Owner) | Có (→ COMPLETED hoặc CANCELLED) | Có (mở lại → ACTIVE) |

# 7. Đặc tả Use Case

Mỗi Use Case gồm actor, mục tiêu, điều kiện trước/sau, luồng chính, ngoại lệ và quy tắc liên quan (mã BR ở mục 5). Mã UC được dùng thống nhất trong tài liệu kiểm thử.

## UC-01 — Đăng ký tài khoản

| Actor chính | Guest |
| --- | --- |
| Mục tiêu | Tạo tài khoản để sử dụng hệ thống. |
| Điều kiện trước | Chưa đăng nhập. |
| Điều kiện sau | Tài khoản được tạo với system role USER, auth_version = 1 và một job đồng bộ snapshot; hoặc request bị từ chối mà không để lại dữ liệu dở dang. |
| Quy tắc liên quan | BR-01, BR-03, BR-04 |

**Luồng chính**

Guest nhập email, mật khẩu, họ tên (tùy chọn).

User Service chuẩn hóa email (chữ thường), kiểm tra định dạng và chính sách mật khẩu.

Kiểm tra email chưa tồn tại.

Trong một transaction: băm mật khẩu, lưuusers, gán role USER vàouser_system_roles, tạo job đồng bộ snapshot.

Trả thông tin tài khoản (không cópassword_hash).

**Ngoại lệ**

Email đã tồn tại: 409 EMAIL_ALREADY_EXISTS.

Dữ liệu không hợp lệ: 400 VALIDATION_ERROR.

Nếu quy trình yêu cầu kích hoạt: tài khoản chưa dùng được cho đến khi kích hoạt; sự kiện USER_ACTIVATED phát qua Outbox sau khi kích hoạt.

## UC-02 — Đăng nhập

| Actor chính | Guest |
| --- | --- |
| Mục tiêu | Xác thực danh tính và cấp phiên/token. |
| Điều kiện trước | Tài khoản tồn tại. |
| Điều kiện sau | Người dùng có access token hợp lệ hoặc bị từ chối. |
| Quy tắc liên quan | BR-05, BR-38 |

**Luồng chính**

Guest gửi email và mật khẩu.

User Service xác thực và kiểm tra tài khoản đang hoạt động.

Cấp access token chứa accountId vàauth_versionhiện tại; cấp refresh token/phiên theo thiết kế.

Frontend lưu token theo chính sách bảo mật và mở trang chính.

**Ngoại lệ**

Sai thông tin: 401 AUTHENTICATION_FAILED.

Tài khoản bị khóa/chưa kích hoạt: 403 ACCOUNT_DISABLED.

## UC-03 — Đăng xuất

| Actor chính | User |
| --- | --- |
| Mục tiêu | Kết thúc phiên hiện tại. |
| Điều kiện trước | Đã đăng nhập. |
| Điều kiện sau | Phiên/refresh token bị thu hồi theo cơ chế đã triển khai. |
| Quy tắc liên quan | BR-38 |

**Luồng chính**

Frontend gọi endpoint logout.

Backend thu hồi refresh token/phiên.

Frontend xóa thông tin xác thực cục bộ và về màn hình đăng nhập.

**Ngoại lệ**

Token đã hết hạn: frontend vẫn xóa trạng thái cục bộ và kết thúc luồng.

## UC-04 — Xem/cập nhật hồ sơ

| Actor chính | User |
| --- | --- |
| Mục tiêu | Xem và sửa hồ sơ cá nhân. |
| Điều kiện trước | Đã đăng nhập. |
| Điều kiện sau | Họ tên được cập nhật hoặc giữ nguyên khi lỗi. |
| Quy tắc liên quan | BR-03, BR-40 |

**Luồng chính**

Backend xác định userId từ token.

Trả hồ sơ hiện tại (id, email, fullName, system roles).

User sửafullName.

Backend validate và lưu.

**Ngoại lệ**

Request cố đổi email, status, system role hoặc id: 400 VALIDATION_ERROR hoặc 403 FORBIDDEN. Đổi email không thuộc MVP.

## UC-05 — Tạo Project

| Actor chính | User |
| --- | --- |
| Mục tiêu | Tạo không gian làm việc mới. |
| Điều kiện trước | Tài khoản hoạt động. |
| Điều kiện sau | Project ACTIVE có Owner đầu tiên, version khởi tạo và job đồng bộ snapshot. |
| Quy tắc liên quan | BR-08, BR-09, BR-18, BR-22 |

**Luồng chính**

User nhập tên Project.

Project Service lấy creatorId từ token và validate tên.

Trong một transaction: tạoprojects(ACTIVE),project_memberships(OWNER, ACTIVE),project_membership_versions(membership_version = 1, authorization_version = 1, row_version = 0), job đồng bộ snapshot.

Tùy cấu hình: tạo Board và các cột mặc định.

Trả Project kèmmyRole = OWNER.

**Ngoại lệ**

Tên rỗng hoặc quá dài: 400 VALIDATION_ERROR.

Bất kỳ bước nào trong transaction lỗi: rollback toàn bộ, không để Project không có Owner.

## UC-06 — Xem danh sách và chi tiết Project

| Actor chính | User |
| --- | --- |
| Mục tiêu | Chỉ thấy Project mà mình có membership ACTIVE. |
| Điều kiện trước | Đã đăng nhập. |
| Điều kiện sau | Chỉ dữ liệu được phép được trả về. |
| Quy tắc liên quan | BR-07, BR-38, BR-41 |

**Luồng chính**

Backend xác định người dùng từ token.

Danh sách: trả các Project có membership ACTIVE của người dùng, có phân trang.

Chi tiết: kiểm tra membership ACTIVE rồi trả Project, trạng thái vàmyRole.

**Ngoại lệ**

Không có membership hoặc Project không tồn tại: 404 RESOURCE_NOT_FOUND.

## UC-07 — Cập nhật Project, đổi trạng thái và mở lại

| Actor chính | Owner |
| --- | --- |
| Mục tiêu | Đổi tên Project; chuyển sang COMPLETED hoặc CANCELLED; mở lại về ACTIVE. |
| Điều kiện trước | Actor là Owner ACTIVE. Đổi tên yêu cầu Project ACTIVE; mở lại yêu cầu Project đang COMPLETED hoặc CANCELLED. |
| Điều kiện sau | Project được cập nhật; khi trạng thái đổi (kể cả mở lại về ACTIVE) sự kiện PROJECT_STATUS_CHANGED được phát. |
| Quy tắc liên quan | BR-17, BR-18, BR-38 |

**Luồng chính**

Owner gửi tên mới hoặc trạng thái mới (COMPLETED, CANCELLED, hoặc ACTIVE để mở lại).

Backend xác minh Owner qua snapshot/membership và kiểm tra quy tắc chuyển trạng thái.

Lưu thay đổi, cập nhậtupdated_at.

Khi đổi trạng thái (kể cả mở lại): ghi Outbox PROJECT_STATUS_CHANGED để các service biết Project đã chỉ đọc hoặc đã hoạt động trở lại.

Trả Project mới.

**Ngoại lệ**

Không phải Owner: 403 FORBIDDEN.

Đổi tên khi Project không ACTIVE: 409 PROJECT_NOT_ACTIVE.

Chuyển trạng thái không hợp lệ (ví dụ chuyển thẳng COMPLETED ↔ CANCELLED, phải qua ACTIVE): 409 INVALID_STATUS_TRANSITION.

## UC-08 — Mời thành viên

| Actor chính | Owner |
| --- | --- |
| Mục tiêu | Gửi lời mời cho một User đã tồn tại vào Project. |
| Điều kiện trước | Project ACTIVE; actor là Owner; người được mời tồn tại và đang hoạt động. |
| Điều kiện sau | Có một lời mời PENDING; người được mời thấy lời mời trong hộp thư lời mời. |
| Quy tắc liên quan | BR-11, BR-13, BR-42 |

**Luồng chính**

Owner chọn người dùng (theo userId hoặc email; Project Service xác minh qua User Service).

Backend kiểm tra người đó chưa là thành viên ACTIVE và chưa có lời mời PENDING.

Tạoinvitationsvớistatus = PENDING,invited_by_user_id,created_at.

Có thể phát sự kiện INVITATION_CREATED để Notification Service tạo thông báo (tùy chọn).

**Ngoại lệ**

Đã là thành viên: 409 MEMBERSHIP_ALREADY_EXISTS.

Đã có lời mời PENDING: 409 INVITATION_ALREADY_PENDING.

Người được mời không tồn tại hoặc bị khóa: 422 INVITEE_NOT_AVAILABLE.

Không phải Owner: 403 FORBIDDEN. Project không ACTIVE: 409 PROJECT_NOT_ACTIVE.

## UC-09 — Chấp nhận / từ chối lời mời

| Actor chính | Invitee |
| --- | --- |
| Mục tiêu | Tham gia hoặc từ chối tham gia Project. |
| Điều kiện trước | Có lời mời PENDING gửi cho mình; Project ACTIVE; tài khoản hoạt động. |
| Điều kiện sau | Chấp nhận: có membership MEMBER ACTIVE. Từ chối: lời mời REJECTED, không có membership. |
| Quy tắc liên quan | BR-10, BR-12, BR-39 |

**Luồng chính**

Invitee xem hộp thư lời mời (lọc theo trạng thái) và chọn lời mời.

**Chấp nhận:**trong một transaction đặt lời mời ACCEPTED (responded_at); tạo membership (MEMBER, ACTIVE) hoặc, nếu đã có dòng REMOVED, kích hoạt lại và xóaremoved_at; tăngmembership_version,authorization_versiontrongproject_membership_versions(khóa lạc quan bằngrow_version); tạo job đồng bộ snapshot.

Phát MEMBER_ADDED qua Outbox.

**Từ chối:**đặt lời mời REJECTED và ghiresponded_at.

**Ngoại lệ**

Lời mời không phải của mình hoặc không tồn tại: 404 RESOURCE_NOT_FOUND.

Lời mời không còn PENDING: 409 INVITATION_NOT_PENDING.

Project không ACTIVE: 409 PROJECT_NOT_ACTIVE.

Hai request cùng lúc: chỉ một thành công (khóa chính membership vàrow_version); request còn lại nhận 409.

## UC-10 — Hủy lời mời

| Actor chính | Owner |
| --- | --- |
| Mục tiêu | Thu hồi lời mời chưa được phản hồi. |
| Điều kiện trước | Lời mời PENDING thuộc Project của Owner. |
| Điều kiện sau | Lời mời CANCELLED. |
| Quy tắc liên quan | BR-12 |

**Luồng chính**

Owner mở danh sách lời mời của Project và chọn hủy.

Backend xác minh Owner và trạng thái PENDING.

Đặt CANCELLED, ghiupdated_at.

**Ngoại lệ**

Lời mời không còn PENDING: 409 INVITATION_NOT_PENDING.

Không phải Owner: 403.

## UC-11 — Loại thành viên / Tự rời Project

| Actor chính | Owner (loại), Member (tự rời) |
| --- | --- |
| Mục tiêu | Thu hồi quyền truy cập Project của một thành viên. |
| Điều kiện trước | Project ACTIVE; target có membership ACTIVE và không phải Owner. |
| Điều kiện sau | Membership REMOVED; sự kiện MEMBER_REMOVED được phát; assignment được gỡ bất đồng bộ (UC-24). |
| Quy tắc liên quan | BR-10, BR-14, BR-16, BR-30, BR-31 |

**Luồng chính**

Owner chọn loại thành viên, hoặc Member chọn Rời Project.

Backend kiểm tra quyền và đảm bảo target không phải Owner.

Trong một transaction: membership → REMOVED, ghiremoved_at,updated_at; tăngmembership_version,authorization_version; tạo job đồng bộ snapshot; ghi Outbox MEMBER_REMOVED (kèm membership_version và danh sách Board của Project).

Sau khi snapshot cập nhật, các request mới của target vào Project bị từ chối.

Task Service dọn assignment (UC-24); Task và bình luận được giữ lại.

**Ngoại lệ**

Target là Owner: 409 OWNER_CANNOT_LEAVE (phải chuyển quyền trước).

Target không phải thành viên ACTIVE: 404.

Không đủ quyền: 403.

## UC-12 — Chuyển quyền Owner

| Actor chính | Owner |
| --- | --- |
| Mục tiêu | Trao quyền sở hữu Project cho một Member. |
| Điều kiện trước | Project ACTIVE; người nhận là MEMBER ACTIVE. |
| Điều kiện sau | Người nhận là OWNER duy nhất; Owner cũ thành MEMBER. |
| Quy tắc liên quan | BR-09, BR-15 |

**Luồng chính**

Owner chọn người nhận quyền và xác nhận.

Backend xác minh Owner hiện tại và người nhận.

Trong một transaction: hạ Owner cũ xuống MEMBER**trước**, rồi nâng người nhận lên OWNER (để không vi phạm unique index một Owner); tăng version và tạo job snapshot cho cả hai người.

Phát OWNER_TRANSFERRED qua Outbox.

**Ngoại lệ**

Người nhận không phải thành viên ACTIVE: 422 TARGET_NOT_PROJECT_MEMBER.

Không phải Owner: 403.

Xung đột đồng thời (row_version): 409 VERSION_CONFLICT, client tải lại và thử lại.

## UC-13 — Quản lý Board

| Actor chính | Owner |
| --- | --- |
| Mục tiêu | Tạo, đổi tên và đổi trạng thái Board. |
| Điều kiện trước | Project ACTIVE; actor là Owner. |
| Điều kiện sau | Board thuộc đúng Project. |
| Quy tắc liên quan | BR-17, BR-19, BR-22 |

**Luồng chính**

Owner nhập tên Board vàboardKey.

Backend xác minh Owner và Project ACTIVE; kiểm traboard_keychưa dùng trong Project.

Tạoboards(ACTIVE,created_by_user_id); tùy cấu hình tạo các cột mặc định.

Trả Board. Đổi tên/đổi trạng thái tương tự, cập nhậtupdated_at.

**Ngoại lệ**

boardKeytrùng trong Project: 409 BOARD_KEY_ALREADY_EXISTS.

Dữ liệu sai: 400.

Không phải Owner: 403. Project không ACTIVE: 409 PROJECT_NOT_ACTIVE.

## UC-14 — Quản lý Column

| Actor chính | Owner |
| --- | --- |
| Mục tiêu | Tạo, đổi tên, sắp xếp và xóa Column. |
| Điều kiện trước | Board ACTIVE thuộc Project ACTIVE; actor là Owner. |
| Điều kiện sau | Các Column của Board có position liên tục, không trùng. |
| Quy tắc liên quan | BR-20, BR-21, BR-24 |

**Luồng chính**

Owner tạo Column (xếp cuối hoặc vị trí chỉ định), đổi tên, hoặc gửi danh sáchcolumnIdtheo thứ tự mới.

Backend xác minh quan hệ Board–Project và quyền Owner.

Sắp xếp lại: cập nhậtpositioncho cả danh sách trong một transaction, tránh trùng(board_id, position)giữa chừng (ví dụ qua vị trí tạm).

Xóa: Project Service hỏi Task Service số Task trong Column; nếu bằng 0 thì xóa và đánh số lạiposition.

**Ngoại lệ**

Column còn Task: 409 COLUMN_NOT_EMPTY.

Danh sách sắp xếp thiếu/dư Column: 400 VALIDATION_ERROR.

Board hoặc Project không ACTIVE: 409.

## UC-15 — Tạo Task

| Actor chính | Owner, Member |
| --- | --- |
| Mục tiêu | Tạo Task trong một Column. |
| Điều kiện trước | Actor có membership ACTIVE; Project và Board ACTIVE. |
| Điều kiện sau | Task được tạo đúng Project/Board/Column. |
| Quy tắc liên quan | BR-23, BR-24, BR-27, BR-34, BR-42 |

**Luồng chính**

Actor nhậpcolumnId,title, mô tả, hạn và assignee (tùy chọn).

Task Service lấy actorId từ token và kiểm tra quyền qua snapshot.

Xác minh qua Project Service: Board thuộc Project, Column thuộc Board, Project và Board ACTIVE.

Nếu có assignee: xác minh là thành viên ACTIVE.

Tạocards(status = TODO,positioncuối Column), tạotask_assigneesnếu có.

Nếu gán cho người khác: ghi Outbox TASK_ASSIGNED.

**Ngoại lệ**

Thiếutitle: 400 VALIDATION_ERROR.

Assignee không hợp lệ: 422 ASSIGNEE_NOT_PROJECT_MEMBER.

Column không thuộc Board: 422 INVALID_COLUMN_TARGET.

Không có quyền: 403/404. Project hoặc Board không ACTIVE: 409.

## UC-16 — Xem và cập nhật Task

| Actor chính | Owner, Member |
| --- | --- |
| Mục tiêu | Đọc Task và sửa tiêu đề, mô tả, hạn. |
| Điều kiện trước | Actor có membership ACTIVE trong Project chứa Task. |
| Điều kiện sau | Task được cập nhật hoặc không đổi nếu lỗi. |
| Quy tắc liên quan | BR-25, BR-38 |

**Luồng chính**

Backend kiểm tra membership và Project/Board ACTIVE khi sửa.

Validate trường được phép (title, description, due_date).

Lưu thay đổi và trả Task mới.

**Ngoại lệ**

Task hoặc Project không truy cập được: 404.

Project hoặc Board không ACTIVE khi sửa: 409.

Hai người cùng sửa: MVP áp dụng ghi sau thắng (Task chưa có cột version).

## UC-17 — Gán / tự nhận / bỏ gán Task

| Actor chính | Owner, Member |
| --- | --- |
| Mục tiêu | Đổi hoặc gỡ người phụ trách Task. |
| Điều kiện trước | Actor có membership ACTIVE; Project và Board ACTIVE. |
| Điều kiện sau | Task có tối đa một assignee primary; thông báo được tạo nếu gán cho người khác. |
| Quy tắc liên quan | BR-27, BR-28, BR-33, BR-34 |

**Luồng chính**

Actor chọn assignee hoặc “Assign to me”.

Backend xác minh assignee là thành viên ACTIVE (qua snapshot/Project Service) và lấymembership_versionhiện tại của assignee.

Trong một transaction: gỡ assignee cũ (nếu có), thêmtask_assigneesvớiis_primary = 1,assigned_by_user_id,assigned_at,membership_version.

Nếu assignee khác actor: ghi Outbox TASK_ASSIGNED.

Bỏ gán: xóa dòng assignee của Task.

**Ngoại lệ**

Assignee không phải thành viên ACTIVE (kể cả vừa bị loại): 422 ASSIGNEE_NOT_PROJECT_MEMBER.

Không có quyền: 403/404. Project không ACTIVE: 409.

## UC-18 — Di chuyển Task

| Actor chính | Owner, Member |
| --- | --- |
| Mục tiêu | Đổi Column và vị trí của Task trong cùng Board. |
| Điều kiện trước | Task và Column đích thuộc cùng Board; Project và Board ACTIVE. |
| Điều kiện sau | Task ở Column đích đúng vị trí; thứ tự các Task khác nhất quán. |
| Quy tắc liên quan | BR-24, BR-26 |

**Luồng chính**

Frontend gửitargetColumnIdvàtargetPositionkhi kéo thả.

Backend kiểm tra membership và quyền.

Xác minh Column đích thuộc Board của Task qua Project Service.

Trong một transaction: cập nhậtcolumn_id,positioncủa Task và dịchpositioncác Task bị ảnh hưởng ở cột nguồn/đích.

Trả vị trí mới.

**Ngoại lệ**

Column đích không thuộc Board: 422 INVALID_COLUMN_TARGET.

targetPositionngoài phạm vi: 400 VALIDATION_ERROR.

Project hoặc Board không ACTIVE: 409.

Không có quyền: 403/404.

## UC-19 — Bình luận Task

| Actor chính | Owner, Member |
| --- | --- |
| Mục tiêu | Trao đổi nội dung liên quan Task. |
| Điều kiện trước | Actor có membership ACTIVE; Project ACTIVE. |
| Điều kiện sau | Bình luận được lưu với tác giả và thời điểm. |
| Quy tắc liên quan | BR-29, BR-42 |

**Luồng chính**

Actor nhập nội dung.

Backend kiểm tra quyền, Project ACTIVE và nội dung không rỗng.

Lưucard_comments(card_id, author_id, content, created_at).

Xem bình luận: trả theo thứ tự thời gian, có phân trang.

**Ngoại lệ**

Nội dung rỗng/quá dài: 400. Người đã bị loại khỏi Project: 404. Project không ACTIVE: 409.

## UC-20 — Nhận và quản lý thông báo

| Actor chính | User; Notification Service |
| --- | --- |
| Mục tiêu | Được báo khi có Task gán cho mình và xem lại thông báo. |
| Điều kiện trước | Sự kiện TASK_ASSIGNED đã commit và được phát. |
| Điều kiện sau | Có đúng một thông báo cho mỗi sự kiện; nghiệp vụ gốc không bị ảnh hưởng nếu lỗi. |
| Quy tắc liên quan | BR-34, BR-35, BR-36, BR-37 |

**Luồng chính**

Notification Service nhận TASK_ASSIGNED và kiểm trainbox_eventstheo (consumer_name, event_id); đã xử lý thì bỏ qua.

Tạonotifications(type ASSIGNED, title, project_id, board_id, card_id,read = 0, created_at) cho assignee.

Đánh dấu inbox PROCESSED.

Khi nhận TASK_DELETED: xóa các thông báo cócard_idcủa Task đã xóa (cũng kiểm tra Inbox).

User xem danh sách mới nhất trước, xem số chưa đọc, đánh dấu một hoặc tất cả đã đọc.

**Ngoại lệ**

Lỗi xử lý: retry có giới hạn, trạng thái FAILED khi hết lần thử; không rollback việc gán Task.

Sự kiện trùng: không tạo thông báo trùng.

Người nhận không còn quyền Project: không gửi nội dung chi tiết.

## UC-21 — Quản lý tài khoản hệ thống, khóa/mở khóa

| Actor chính | System Admin |
| --- | --- |
| Mục tiêu | Tìm tài khoản và khóa hoặc mở khóa. |
| Điều kiện trước | Actor có system role SYSTEM_ADMIN. |
| Điều kiện sau | Trạng thái đổi, auth_version tăng, job đồng bộ snapshot được tạo. |
| Quy tắc liên quan | BR-04, BR-05, BR-06, BR-07 |

**Luồng chính**

Admin tìm kiếm theo email/tên/trạng thái, xem chi tiết (dữ liệu tối thiểu).

Admin chọn Khóa hoặc Mở khóa.

Backend xác minh quyền và tài khoản đích; kiểm tra không khóa Admin cuối cùng.

Trong một transaction: đổistatus, tăngauth_version, tạo job đồng bộ snapshot.

Thu hồi refresh token/phiên; token cũ bị từ chối theoauth_version.

**Ngoại lệ**

Không phải Admin: 403.

Không tìm thấy tài khoản: 404.

Khóa Admin cuối cùng: 409 LAST_SYSTEM_ADMIN.

DB chưa có bảng audit: MVP ghi log ứng dụng có correlationId (lý do khóa nếu có chỉ nằm trong log).

## UC-22 — Cấp / thu hồi system role

| Actor chính | System Admin |
| --- | --- |
| Mục tiêu | Thay đổi system role của một tài khoản. |
| Điều kiện trước | Actor là SYSTEM_ADMIN; tài khoản đích tồn tại. |
| Điều kiện sau | user_system_roles đổi, auth_version tăng, job snapshot được tạo. |
| Quy tắc liên quan | BR-02, BR-04, BR-06 |

**Luồng chính**

Admin chọn tài khoản và role (USER hoặc SYSTEM_ADMIN).

Backend kiểm tra role hợp lệ và không để mất SYSTEM_ADMIN cuối cùng.

Trong một transaction: thêm/xóa dònguser_system_roles(ghigranted_by= Admin thực hiện), tăngauth_version, tạo job snapshot.

**Ngoại lệ**

Role không tồn tại: 400. Thu hồi SYSTEM_ADMIN cuối cùng: 409 LAST_SYSTEM_ADMIN. Không phải Admin: 403.

## UC-23 — Đồng bộ snapshot quyền sang Redis

| Actor chính | Hệ thống nền (User Service, Project Service) |
| --- | --- |
| Mục tiêu | Đưa thay đổi quyền từ database sang Redis một cách tin cậy. |
| Điều kiện trước | Có job account_authorization_snapshot_sync_jobs hoặc project_authorization_snapshot_sync_jobs đến hạn. |
| Điều kiện sau | Redis chứa snapshot đúng version mới nhất, hoặc job được hẹn thử lại. |
| Quy tắc liên quan | BR-39 |

**Luồng chính**

Worker quét job theo (status, next_attempt_at).

Đọc dữ liệu hiện tại (hoặcsnapshot_jsonđã lưu cho Project) và ghi vào Redis chỉ khi version mới hơn version đang có.

Thành công: đánh dấu job hoàn tất.

Lỗi: tăngattempt_count, ghilast_error, đặtnext_attempt_attheo backoff.

**Ngoại lệ**

Redis tạm thời lỗi: job được thử lại, thay đổi quyền không bị mất.

Job chạy lại hoặc đến muộn: không làm giảm version đã có trong Redis.

## UC-24 — Dọn assignment khi thành viên rời Project

| Actor chính | Hệ thống nền (Task Service) |
| --- | --- |
| Mục tiêu | Gỡ assignment của người đã rời Project mà không xóa Task. |
| Điều kiện trước | Task Service nhận MEMBER_REMOVED. |
| Điều kiện sau | Người đó không còn là assignee của Task nào trong Project (ở các version đã bị fence). |
| Quy tắc liên quan | BR-30, BR-31, BR-32, BR-36 |

**Luồng chính**

Consumer kiểm tra Inbox (consumer_name, event_id); đã xử lý thì bỏ qua.

Ghimembership_assignment_fencestheo (project_id, user_id, membership_version).

Tạomember_assignment_cleanup_jobsvớideduplication_key,event_id,board_ids_json; trùng khóa thì không tạo thêm.

Worker xử lý theo lô, dùngcursor_last_processed_card_id, gỡtask_assigneescủa người đó cómembership_versionnhỏ hơn hoặc bằng version của sự kiện trên các card thuộc các Board đã liệt kê.

Hoàn tất khi hết card; lỗi thì ghilast_error, tăngattempt_count, đặtnext_attempt_at.

**Ngoại lệ**

Sự kiện đến trùng hoặc sai thứ tự: không gỡ nhầm assignment của version cao hơn (người được thêm lại).

Worker chết giữa chừng: tiếp tục từ cursor đã lưu.

## UC-25 — Xóa Task

| Actor chính | Owner |
| --- | --- |
| Mục tiêu | Xóa vĩnh viễn một Task không còn cần thiết. |
| Điều kiện trước | Actor là Owner ACTIVE; Project và Board ACTIVE; Task tồn tại. |
| Điều kiện sau | Task cùng assignee và bình luận của nó bị xóa; sự kiện TASK_DELETED được phát; thứ tự các Task còn lại trong Column hợp lệ. |
| Quy tắc liên quan | BR-17, BR-38, BR-43 |

**Luồng chính**

Owner chọn Xóa Task; giao diện cảnh báo việc xóa là vĩnh viễn và bình luận sẽ mất, Owner xác nhận.

Task Service lấy actorId từ token, kiểm tra Owner qua snapshot và xác minh Project, Board ACTIVE (qua Project Service).

Trong một transaction: xóacards(cascade xóatask_assigneesvàcard_comments), cập nhậtpositioncác Task còn lại nếu cần, ghi Outbox TASK_DELETED (projectId, boardId, cardId, assigneeUserId nếu có).

Notification Service nhận TASK_DELETED (kiểm tra Inbox) và xóa các thông báo cócard_idtương ứng.

Trả 204 No Content.

**Ngoại lệ**

Task không tồn tại hoặc người gọi không xem được Project: 404 RESOURCE_NOT_FOUND (gọi xóa lần hai cũng 404).

Member gọi xóa: 403 FORBIDDEN.

Project hoặc Board không ACTIVE: 409 PROJECT_NOT_ACTIVE hoặc BOARD_NOT_ACTIVE.

Lỗi xử lý TASK_DELETED ở Notification Service: retry; không rollback việc xóa Task.

# 8. Quy ước API

## 8.1 Quy ước chung

Base path đề xuất:/api/v1, đi qua API Gateway; Gateway định tuyến tới service theo Eureka.

Resource dùng danh từ số nhiều; GET đọc, POST tạo/thực hiện lệnh, PATCH cập nhật một phần, PUT thay thế, DELETE xóa/gỡ.

ID của User là UUID; ID của Project, Board, Column, Task, Comment, Invitation, Notification là số nguyên (BIGINT). Tài liệu dùng{projectId},{taskId}để minh họa.

Không nhậnactorId,roletừ client; actor lấy từ access token. JSON dùng camelCase; thời gian theo ISO 8601 UTC.

Danh sách dùngpage,size,sort; có giới hạnsizetối đa.

API cần xác thực dùngAuthorization: Bearer <access_token>. HeaderX-Correlation-Idkhuyến nghị (Gateway tự tạo nếu thiếu).Idempotency-Keycho POST có nguy cơ retry.

Mọi lỗi dùng chung một cấu trúc (mục 8.3). Không trả stack trace, SQL, token hay thông tin nội bộ.

## 8.2 Ví dụ thành công

POST /api/v1/projects

Authorization: Bearer <access_token>

{ "name": "Kanban MVP" }

HTTP/1.1 201 Created

Location: /api/v1/projects/1

{

"data": {

"id": 1,

"name": "Kanban MVP",

"status": "ACTIVE",

"createdByUserId": "e7b3a2c1-3094-4f3b-910a-7e82845e0b2d",

"myRole": "OWNER",

"createdAt": "2026-11-06T06:30:00Z"

},

"meta": { "correlationId": "1d0c..." }

}

POST /api/v1/boards/1/tasks

{

"columnId": 1,

"title": "Thiết kế giao diện Đăng ký/Đăng nhập",

"description": "Hoàn thiện màn hình Figma",

"dueDate": "2026-11-12T10:00:00Z",

"assigneeId": "a4f9c8d2-5061-4b9f-834c-9d12756f3a1e"

}

POST /api/v1/tasks/2/move

{ "targetColumnId": 2, "targetPosition": 0 }

PUT /api/v1/tasks/2/assignee

{ "userId": "a4f9c8d2-5061-4b9f-834c-9d12756f3a1e" }

## 8.3 Cấu trúc lỗi

{

"error": {

"code": "PROJECT_NOT_ACTIVE",

"message": "Project is not active and is read-only.",

"details": [ { "field": "projectId", "reason": "Mutations are not allowed." } ]

},

"meta": { "correlationId": "1d0c...", "timestamp": "2026-11-06T06:31:00Z" }

}

## 8.4 HTTP status

| HTTP | Khi dùng | Ví dụ error.code |
| --- | --- | --- |
| 200 / 201 / 204 | Thành công; tạo mới; thành công không có body | — |
| 400 | JSON sai, thiếu trường, validation lỗi | VALIDATION_ERROR |
| 401 | Token thiếu/sai/hết hạn/auth_version cũ; đăng nhập thất bại | UNAUTHENTICATED, AUTHENTICATION_FAILED |
| 403 | Đã xác thực nhưng không đủ quyền; tài khoản bị khóa | FORBIDDEN, ACCOUNT_DISABLED |
| 404 | Không tồn tại hoặc ẩn để chống dò quyền | RESOURCE_NOT_FOUND |
| 409 | Xung đột trạng thái: trùng, Project/Board không ACTIVE, version | PROJECT_NOT_ACTIVE, INVITATION_ALREADY_PENDING |
| 422 | Đúng cấu trúc nhưng vi phạm quy tắc nghiệp vụ | ASSIGNEE_NOT_PROJECT_MEMBER |
| 429 / 500 / 503 | Rate limit; lỗi không dự kiến; service phụ thuộc không sẵn sàng | RATE_LIMIT_EXCEEDED, INTERNAL_ERROR, DEPENDENCY_UNAVAILABLE |

# 9. Danh mục API theo service

## 9.1 User Service

| Method | Endpoint | Mục đích | Quyền / ghi chú |
| --- | --- | --- | --- |
| POST | /api/v1/auth/register | Đăng ký | Public; role mặc định USER. |
| POST | /api/v1/auth/login | Đăng nhập | Public; trả token/phiên. |
| POST | /api/v1/auth/logout | Đăng xuất | Authenticated. |
| GET | /api/v1/users/me | Xem hồ sơ | Authenticated. |
| PATCH | /api/v1/users/me | Sửa hồ sơ | Chỉ fullName. |
| GET | /api/v1/admin/users | Tìm kiếm tài khoản | SYSTEM_ADMIN; có phân trang, lọc. |
| GET | /api/v1/admin/users/{userId} | Chi tiết tài khoản | SYSTEM_ADMIN. |
| PATCH | /api/v1/admin/users/{userId}/status | Khóa / mở khóa | SYSTEM_ADMIN; tăng auth_version. |
| POST | /api/v1/admin/users/{userId}/system-roles | Cấp system role | SYSTEM_ADMIN; body roleCode; ghi granted_by. |
| DELETE | /api/v1/admin/users/{userId}/system-roles/{roleCode} | Thu hồi system role | SYSTEM_ADMIN; không thu hồi Admin cuối cùng. |

## 9.2 Project Service — Project, thành viên, lời mời

| Method | Endpoint | Mục đích | Quyền / ghi chú |
| --- | --- | --- | --- |
| POST | /api/v1/projects | Tạo Project | User hoạt động; creator thành OWNER. |
| GET | /api/v1/projects | Project của tôi | Chỉ membership ACTIVE; page/size. |
| GET | /api/v1/projects/{projectId} | Chi tiết Project | Member/Owner. |
| PATCH | /api/v1/projects/{projectId} | Đổi tên Project | Owner; Project ACTIVE. |
| PATCH | /api/v1/projects/{projectId}/status | Đổi trạng thái / mở lại | Owner; ACTIVE/COMPLETED/CANCELLED; mở lại bằng status=ACTIVE. |
| GET | /api/v1/projects/{projectId}/members | Danh sách thành viên | Member/Owner. |
| DELETE | /api/v1/projects/{projectId}/members/{userId} | Loại thành viên | Owner; không loại Owner. |
| POST | /api/v1/projects/{projectId}/leave | Tự rời Project | Member; Owner phải chuyển quyền trước. |
| POST | /api/v1/projects/{projectId}/transfer-ownership | Chuyển quyền Owner | Owner; body newOwnerUserId. |
| POST | /api/v1/projects/{projectId}/invitations | Mời thành viên | Owner; body userId (hoặc email). |
| GET | /api/v1/projects/{projectId}/invitations | Lời mời của Project | Owner; lọc theo trạng thái. |
| POST | /api/v1/projects/{projectId}/invitations/{invitationId}/cancel | Hủy lời mời | Owner; chỉ khi PENDING. |
| GET | /api/v1/invitations/me | Hộp thư lời mời của tôi | Authenticated; lọc theo trạng thái. |
| POST | /api/v1/invitations/{invitationId}/accept | Chấp nhận lời mời | Chỉ người được mời. |
| POST | /api/v1/invitations/{invitationId}/reject | Từ chối lời mời | Chỉ người được mời. |

## 9.3 Project Service — Board và Column

| Method | Endpoint | Mục đích | Quyền / ghi chú |
| --- | --- | --- | --- |
| POST | /api/v1/projects/{projectId}/boards | Tạo Board | Owner; body name, boardKey. |
| GET | /api/v1/projects/{projectId}/boards | Danh sách Board | Member/Owner. |
| GET | /api/v1/boards/{boardId} | Chi tiết Board kèm Column | Member/Owner. |
| PATCH | /api/v1/boards/{boardId} | Đổi tên Board | Owner. |
| PATCH | /api/v1/boards/{boardId}/status | Đổi trạng thái Board | Owner. |
| POST | /api/v1/boards/{boardId}/columns | Tạo Column | Owner. |
| PATCH | /api/v1/columns/{columnId} | Đổi tên Column | Owner. |
| PUT | /api/v1/boards/{boardId}/columns/order | Sắp xếp Column | Owner; body columnIds theo thứ tự mới. |
| DELETE | /api/v1/columns/{columnId} | Xóa Column | Owner; chỉ khi không còn Task. |

## 9.4 Task Service

| Method | Endpoint | Mục đích | Quyền / ghi chú |
| --- | --- | --- | --- |
| POST | /api/v1/boards/{boardId}/tasks | Tạo Task | Owner/Member; xác minh Column và Project. |
| GET | /api/v1/boards/{boardId}/tasks | Task trên Board | Member/Owner; theo Column và position. |
| GET | /api/v1/tasks/{taskId} | Chi tiết Task | Member/Owner. |
| PATCH | /api/v1/tasks/{taskId} | Sửa Task | title, description, dueDate. |
| DELETE | /api/v1/tasks/{taskId} | Xóa Task | Owner; Project và Board ACTIVE; xóa vĩnh viễn kèm assignee và bình luận. |
| PUT | /api/v1/tasks/{taskId}/assignee | Gán / tự nhận | Assignee là thành viên ACTIVE. |
| DELETE | /api/v1/tasks/{taskId}/assignee | Bỏ gán | Owner/Member. |
| POST | /api/v1/tasks/{taskId}/move | Di chuyển Task | Body targetColumnId, targetPosition; cùng Board. |
| GET | /api/v1/tasks/{taskId}/comments | Xem bình luận | Member/Owner. |
| POST | /api/v1/tasks/{taskId}/comments | Tạo bình luận | Owner/Member. |

## 9.5 Notification Service

| Method | Endpoint | Mục đích | Quyền / ghi chú |
| --- | --- | --- | --- |
| GET | /api/v1/notifications | Thông báo của tôi | Mới nhất trước; lọc read; page/size. |
| GET | /api/v1/notifications/unread-count | Số chưa đọc | Chỉ của người gọi. |
| PATCH | /api/v1/notifications/{notificationId}/read | Đánh dấu đã đọc | Chỉ người nhận. |
| POST | /api/v1/notifications/read-all | Đánh dấu tất cả đã đọc | Chỉ của người gọi. |

## 9.6 API nội bộ (service-to-service, không qua Gateway công khai)

Do không có FK xuyên database, các service xác minh dữ liệu của nhau qua API nội bộ. Đây là đề xuất; có thể thay bằng cache/snapshot nếu đủ.

| Method | Endpoint | Mục đích | Quyền / ghi chú |
| --- | --- | --- | --- |
| GET | /internal/projects/{projectId}/members/{userId} | Xác minh membership | Trả role, status, membership_version, trạng thái Project. |
| GET | /internal/boards/{boardId} | Thông tin Board | Trả projectId, trạng thái Board/Project, danh sách Column. |
| GET | /internal/users/{userId} | Xác minh tài khoản | Trả trạng thái hoạt động và tên hiển thị. |
| GET | /internal/columns/{columnId}/task-count | Đếm Task trong Column | Task Service; dùng khi xóa Column. |

# 10. Sự kiện (RabbitMQ)

Mọi sự kiện phát quaoutbox_eventscủa service sở hữu và được tiêu thụ idempotent quainbox_events. Mỗi sự kiện có tối thiểu:eventId,type,occurredAt,aggregateType,aggregateId,correlationIdvà payload.

| Sự kiện | Nguồn → Đích | Mục đích / payload chính |
| --- | --- | --- |
| USER_ACTIVATED | User → Notification (tùy chọn) | Tài khoản được kích hoạt. userId, email. (Có trong dữ liệu mẫu.) |
| INVITATION_CREATED | Project → Notification (tùy chọn) | Có lời mời mới. invitationId, projectId, inviteeUserId, invitedByUserId. |
| MEMBER_ADDED | Project → (tùy chọn) | Thành viên tham gia. projectId, userId, membershipVersion. |
| MEMBER_REMOVED | Project → Task | Kích hoạt dọn assignment. projectId, userId, membershipVersion, boardIds, operationId, sourceAggregateVersion. |
| OWNER_TRANSFERRED | Project → (tùy chọn) | Đổi Owner. projectId, fromUserId, toUserId. |
| PROJECT_STATUS_CHANGED | Project → Task, Notification | Project chuyển trạng thái, kể cả mở lại về ACTIVE. projectId, status, previousStatus. |
| TASK_ASSIGNED | Task → Notification | Task được gán cho người khác. projectId, boardId, cardId, taskTitle, assigneeUserId, assignedByUserId. Handler mẫu: TaskAssignedNotificationHandler. |
| TASK_DELETED | Task → Notification | Task bị xóa. projectId, boardId, cardId, deletedByUserId. Notification Service xóa thông báo có card_id tương ứng. |

*Việc đồng bộ snapshot sang Redis dùng bảng job riêng (không qua RabbitMQ), xem UC-23.*

# 11. Mã lỗi

| Mã lỗi | HTTP | Ý nghĩa |
| --- | --- | --- |
| VALIDATION_ERROR | 400 | Một hoặc nhiều trường không hợp lệ. |
| UNAUTHENTICATED | 401 | Token thiếu, sai, hết hạn hoặc auth_version đã cũ. |
| AUTHENTICATION_FAILED | 401 | Đăng nhập thất bại. |
| ACCOUNT_DISABLED | 403 | Tài khoản bị khóa hoặc chưa kích hoạt. |
| FORBIDDEN | 403 | Không đủ quyền cho hành động. |
| RESOURCE_NOT_FOUND | 404 | Không tồn tại hoặc bị ẩn do chính sách bảo mật. |
| EMAIL_ALREADY_EXISTS | 409 | Email đã được sử dụng. |
| MEMBERSHIP_ALREADY_EXISTS | 409 | Người dùng đã là thành viên ACTIVE. |
| INVITATION_ALREADY_PENDING | 409 | Đã có lời mời PENDING cho (project, user). |
| INVITATION_NOT_PENDING | 409 | Lời mời đã ở trạng thái cuối. |
| OWNER_CANNOT_LEAVE | 409 | Owner không được rời/bị loại; phải chuyển quyền trước. |
| LAST_SYSTEM_ADMIN | 409 | Không thể khóa hoặc thu hồi SYSTEM_ADMIN cuối cùng. |
| PROJECT_NOT_ACTIVE | 409 | Project COMPLETED/CANCELLED, chỉ đọc. |
| BOARD_NOT_ACTIVE | 409 | Board COMPLETED/CANCELLED, chỉ đọc. |
| INVALID_STATUS_TRANSITION | 409 | Chuyển trạng thái Project/Board không hợp lệ (ví dụ chuyển thẳng COMPLETED ↔ CANCELLED). |
| BOARD_KEY_ALREADY_EXISTS | 409 | board_key đã dùng trong Project. |
| COLUMN_NOT_EMPTY | 409 | Không thể xóa Column còn Task. |
| VERSION_CONFLICT | 409 | Membership bị thay đổi bởi request khác cùng lúc (row_version); tải lại và thử lại. |
| ASSIGNEE_NOT_PROJECT_MEMBER | 422 | Assignee không phải thành viên ACTIVE của Project. |
| TARGET_NOT_PROJECT_MEMBER | 422 | Người nhận quyền Owner không phải thành viên ACTIVE. |
| INVITEE_NOT_AVAILABLE | 422 | Người được mời không tồn tại hoặc không hoạt động. |
| INVALID_COLUMN_TARGET | 422 | Column không thuộc Board của Task. |
| RATE_LIMIT_EXCEEDED | 429 | Vượt giới hạn request. |
| INTERNAL_ERROR | 500 | Lỗi không dự kiến; dùng correlationId để tra log. |
| DEPENDENCY_UNAVAILABLE | 503 | Service phụ thuộc tạm thời không sẵn sàng. |

# 12. Mô hình dữ liệu (theo SQL)

Bảng dưới tóm tắt schema trongkanban_mssql_databases.sqlvà ý nghĩa nghiệp vụ. Thời gian dùng DATETIMEOFFSET; lưu UTC và đổi múi giờ ở client.

## 12.1 kanban_user_db (User Service)

| Bảng | Cột / ràng buộc chính | Ý nghĩa nghiệp vụ |
| --- | --- | --- |
| system_roles | role_code PK. Dữ liệu mẫu: USER, SYSTEM_ADMIN. | Danh mục system role. |
| users | id UUID PK; email UNIQUE; password_hash; full_name NVARCHAR NULL; status; auth_version mặc định 1. | Tài khoản. auth_version tăng khi đổi status/system role. Chưa có CHECK cho status. |
| user_system_roles | PK (user_id, role_code); granted_by NULL; FK tới users và system_roles. | Gán nhiều role cho một tài khoản; lưu người cấp. |
| account_authorization_snapshot_sync_jobs | job_id PK; account_id, auth_version, status, next_attempt_at; UNIQUE (account_id, auth_version); index (status, next_attempt_at). | Hàng đợi đồng bộ snapshot tài khoản sang Redis. |
| outbox_events / inbox_events | Outbox: event_id PK, aggregate_type, aggregate_id, type, payload, status, next_attempt_at. Inbox: PK (consumer_name, event_id). | Phát sự kiện tin cậy và chống xử lý trùng. |

## 12.2 kanban_board_db (Project Service, gồm Board)

| Bảng | Cột / ràng buộc chính | Ý nghĩa nghiệp vụ |
| --- | --- | --- |
| projects | id BIGINT identity PK; name ≤ 255; status ∈ {ACTIVE, COMPLETED, CANCELLED}; created_by_user_id; created_at, updated_at. | Project và trạng thái vòng đời. |
| project_memberships | PK (project_id, user_id); role ∈ {OWNER, MEMBER}; status ∈ {ACTIVE, REMOVED}; joined_at, removed_at, updated_at. Unique index một OWNER ACTIVE mỗi Project; index (user_id, status, project_id). | Membership và Project role; một dòng cho mỗi cặp, dùng lại khi vào lại. |
| invitations | id PK; project_id FK; user_id; invited_by_user_id; status ∈ {PENDING, ACCEPTED, REJECTED, CANCELLED}; responded_at. Unique index một PENDING mỗi (project, user); index cho hộp thư của user và thống kê của Project. | Lời mời tham gia Project. |
| boards | id PK; project_id FK; name; board_key; status ∈ {ACTIVE, COMPLETED, CANCELLED}; UNIQUE (project_id, board_key). | Board thuộc Project. |
| board_columns | id PK; board_id FK; name ≤ 100; position ≥ 0; UNIQUE (board_id, position). | Cột của Board và thứ tự hiển thị. |
| project_membership_versions | PK (project_id, user_id); membership_version, authorization_version, row_version. | Version dùng cho snapshot, fence và khóa lạc quan membership. |
| project_authorization_snapshot_sync_jobs | job_id PK; operation_id UNIQUE; UNIQUE (project_id, user_id, authorization_version); snapshot_json, status, attempt_count, last_error, next_attempt_at. | Hàng đợi đồng bộ snapshot membership sang Redis. |
| outbox_events / inbox_events (bổ sung v1.2) | Cùng schema với Task Service. Outbox: event_id PK, aggregate_type, aggregate_id, type, payload, status ∈ {PENDING, PROCESSED, FAILED}, created_at, next_attempt_at; index idx_outbox_due (status, next_attempt_at, created_at). Inbox: PK (consumer_name, event_id). Script: kanban_board_outbox_inbox.sql. | Project Service phát MEMBER_REMOVED, PROJECT_STATUS_CHANGED, INVITATION_CREATED... tin cậy qua RabbitMQ. Inbox dự phòng nếu Project Service tiêu thụ sự kiện sau này. |

## 12.3 kanban_task_db (Task Service)

| Bảng | Cột / ràng buộc chính | Ý nghĩa nghiệp vụ |
| --- | --- | --- |
| cards | id PK; project_id, board_id, column_id (scalar); title ≤ 500; description NVARCHAR(MAX); status mặc định TODO; position ≥ 0 mặc định 0; due_date. Index (column_id, position). | Task. Vị trí theo Column + position. |
| task_assignees | PK (task_id, user_id); assigned_by_user_id, assigned_at, membership_version, is_primary; FK task_id ON DELETE CASCADE; unique index một primary mỗi Task. | Người phụ trách Task. MVP chỉ một primary. |
| membership_assignment_fences | PK (project_id, user_id, membership_version); event_id, fenced_at. | Hàng rào chống job dọn dẹp cũ gỡ nhầm assignment mới. |
| member_assignment_cleanup_jobs | job_id PK; deduplication_key UNIQUE; event_id UNIQUE; operation_id, project_id, user_id, membership_version, source_aggregate_version, board_ids_json, status, cursor_last_processed_card_id, attempt_count, last_error, next_attempt_at. | Job gỡ assignment theo lô, chạy lại được. |
| card_comments | id PK; card_id FK ON DELETE CASCADE; author_id; content NVARCHAR(MAX); created_at. Index (card_id, created_at DESC). | Bình luận; không có updated_at nên không sửa trong MVP. |
| outbox_events / inbox_events | Như User Service. | Hạ tầng sự kiện của Task Service. |

## 12.4 kanban_notification_db (Notification Service)

| Bảng | Cột / ràng buộc chính | Ý nghĩa nghiệp vụ |
| --- | --- | --- |
| notifications | id PK; user_id (người nhận); project_id, board_id, card_id NULL; type mặc định ASSIGNED; title NVARCHAR(500); read BIT mặc định 0; created_at. Index (user_id, read) và (user_id, created_at DESC). | Thông báo in-app theo người nhận. |
| outbox_events / inbox_events | Outbox dùng id, không có next_attempt_at; Inbox dùng PK (id, message_handler). | Hạ tầng sự kiện; khác tên cột so với ba service còn lại (xem mục 15.3). |

# 13. Bảo mật, nhất quán và vận hành

## 13.1 Checklist phân quyền cho mỗi API nghiệp vụ

Gateway xác thực token; service kiểm traauth_versiontrong token so với snapshot tài khoản.

Lấy actorId từ principal đã xác thực; không tin ID người dùng do client gửi.

Đọc snapshot membership (role, version) từ Redis; nếu không có thì xử lý theo quyết định mở số 5.

Xác minh tài nguyên tồn tại và thuộc đúng Project/Board (qua API nội bộ hoặc dữ liệu của chính service).

Kiểm tra quyền theo ma trận mục 6 và trạng thái Project/Board.

Thực hiện thay đổi trong transaction; ghi Outbox cùng transaction nếu có sự kiện.

Trả lỗi theo cấu trúc chuẩn; không trả 200 khi mutation thất bại.

## 13.2 Snapshot quyền, version và fence

Nguồn sự thật là database của User/Project Service. Redis chỉ là bản sao để đọc nhanh.

Mỗi thay đổi quyền tăng version và tạo job đồng bộ trong cùng transaction; job đảm bảo Redis cuối cùng khớp dữ liệu gốc dù Redis lỗi tạm thời.

Khi ghi Redis, chỉ ghi nếu version mới hơn version hiện có.

Có độ trễ ngắn giữa lúc đổi quyền và lúc snapshot cập nhật; thao tác nhạy cảm có thể gọi xác minh trực tiếp service sở hữu.

membership_versionđi kèm assignment và sự kiện MEMBER_REMOVED để job dọn dẹp không gỡ nhầm assignment của lần tham gia sau.

## 13.3 Đồng thời và độ tin cậy

Chuyển quyền Owner, chấp nhận lời mời, loại thành viên dùngrow_versionđể chống ghi đè đồng thời.

Task chưa có version: hai người cùng sửa thì ghi sau thắng (quy tắc MVP).

Consumer phải idempotent; retry có giới hạn; message lỗi quá số lần thử chuyển FAILED để xử lý thủ công.

Publisher Outbox quét theo (status, next_attempt_at, created_at). Nếu chạy nhiều instance, dùng khóa UPDLOCK/READPAST khi quét để không lấy trùng một dòng; sự kiện vẫn có thể gửi hai lần nên consumer kiểm tra Inbox.

Không ghi mật khẩu, token, secret vào log; log kèm correlationId.

# 14. Tiêu chí nghiệm thu MVP

| Mã | Chức năng | Điều kiện nghiệm thu |
| --- | --- | --- |
| AC-01 | Đăng ký | Tài khoản mới có role USER, auth_version = 1, email duy nhất; response không chứa password_hash. |
| AC-02 | Tạo Project | Project ACTIVE và membership OWNER được tạo cùng lúc; nếu bước nào lỗi thì không còn Project mồ côi. |
| AC-03 | Một Owner | Không thể có hai OWNER ACTIVE trong cùng Project; chuyển quyền Owner thành công mà không vi phạm ràng buộc. |
| AC-04 | Hai cấp quyền | Owner ở Project A không có quyền ở Project B; SYSTEM_ADMIN không đọc được Project mà mình không là thành viên. |
| AC-05 | Lời mời | Owner mời được; mời trùng khi đang PENDING bị 409; chấp nhận tạo membership MEMBER; từ chối không tạo membership; người khác không phản hồi thay được. |
| AC-06 | Owner không rời | Owner không thể rời hoặc bị loại khi chưa chuyển quyền (409 OWNER_CANNOT_LEAVE). |
| AC-07 | Loại thành viên | Sau khi snapshot cập nhật, request của người bị loại bị từ chối; Task và bình luận của họ vẫn còn. |
| AC-08 | Dọn assignment | Assignment của người bị loại được gỡ; xử lý lại cùng sự kiện không đổi kết quả; người được thêm lại và được gán Task mới không bị gỡ nhầm. |
| AC-09 | Board | board_key trùng trong cùng Project bị 409; có thể trùng ở Project khác. |
| AC-10 | Column | Sắp xếp lại Column thành công không vi phạm unique vị trí; xóa Column còn Task bị 409. |
| AC-11 | Tạo/sửa Task | Member tạo và sửa Task của người khác; Column không thuộc Board bị 422. |
| AC-12 | Assignee | Gán cho thành viên ACTIVE thành công và chỉ có một assignee; gán cho người ngoài Project hoặc đã bị loại bị 422. |
| AC-13 | Di chuyển Task | Di chuyển trong cùng Board thành công và thứ tự đúng; Column của Board khác bị 422. |
| AC-14 | Project chỉ đọc | Project COMPLETED/CANCELLED vẫn xem được nhưng mọi mutation nghiệp vụ bị 409 PROJECT_NOT_ACTIVE. |
| AC-15 | Bình luận | Owner/Member bình luận được; người đã bị loại không bình luận được; bình luận cũ của họ vẫn hiển thị. |
| AC-16 | Thông báo | Gán Task cho người khác tạo đúng một thông báo ASSIGNED; sự kiện trùng không nhân đôi; người dùng chỉ thấy thông báo của mình. |
| AC-17 | Lỗi thông báo | Lỗi Notification Service không làm mất việc gán Task đã commit. |
| AC-18 | Khóa tài khoản | auth_version tăng; token cũ bị từ chối sau khi snapshot cập nhật; không khóa được Admin cuối cùng. |
| AC-19 | Đồng bộ snapshot | Redis lỗi tạm thời: job được retry và quyền cuối cùng đúng; snapshot version cũ không ghi đè version mới. |
| AC-20 | Hợp đồng lỗi và quyền | Mọi lỗi theo cấu trúc chuẩn; gọi API trực tiếp bằng công cụ như Postman không vượt được quyền. |
| AC-21 | Mở lại Project | Owner chuyển Project COMPLETED/CANCELLED về ACTIVE thành công và phát PROJECT_STATUS_CHANGED; Member gọi bị 403; sau khi mở lại các thao tác ghi hoạt động bình thường; chuyển thẳng COMPLETED ↔ CANCELLED bị 409. |
| AC-22 | Xóa Task | Owner xóa được Task, assignee và bình luận của Task bị xóa theo; Member bị 403; Project hoặc Board không ACTIVE bị 409; thông báo liên quan được dọn sau TASK_DELETED, thông báo còn sót không gây lỗi giao diện. |
| AC-23 | Outbox của Project Service | Loại thành viên ghi MEMBER_REMOVED vào outbox_events cùng transaction (rollback thì không có sự kiện); publisher gửi lại khi lỗi mà Task Service không xử lý trùng. |

# 15. Ngoài phạm vi MVP, quyết định mở và khuyến nghị DB

## 15.1 Ngoài phạm vi MVP (DB chưa hỗ trợ)

| Hạng mục | Vì sao chưa có | Cần thêm vào DB khi muốn làm |
| --- | --- | --- |
| WIP limit | board_columns không có cột WIP | wip_limit NULL ở board_columns |
| Role Viewer | CHECK role chỉ có OWNER, MEMBER | Thêm VIEWER vào CHECK và ma trận quyền |
| Priority, labels, created_by, version của Task | cards không có các cột này | Thêm cột; version để có VERSION_CONFLICT cho Task |
| Activity / History | Chưa có bảng | Bảng activity theo Project/Task |
| Audit log, Support Mode | Chưa có bảng | Bảng audit và support session; lý do khóa tài khoản cần nơi lưu |
| Email thông báo | notifications không có trạng thái gửi | Bảng/cột trạng thái gửi và retry email |
| Mô tả Project/Board, sửa/xóa bình luận | Thiếu cột | description, updated_at |
| Nhiều assignee | API MVP chỉ một primary (schema đã cho phép nhiều) | Mở API và bỏ ràng buộc một primary |
| Upload attachment, xóa vĩnh viễn Project/User, realtime Board, SSO | Ngoài thiết kế hiện tại | Thiết kế riêng |

## 15.2 Quyết định mở

| # | Nội dung | Đề xuất |
| --- | --- | --- |
| 1 | Có cho Owner mở lại Project COMPLETED/CANCELLED về ACTIVE không? | Đã chốt (v1.2): CÓ. Chỉ Owner; phát PROJECT_STATUS_CHANGED. |
| 2 | Tập giá trị users.status (DB chưa có CHECK). | PENDING_ACTIVATION, ACTIVE, LOCKED; thêm CHECK. |
| 3 | Vai trò của cards.status (mặc định TODO; dữ liệu mẫu có giá trị ACTIVE, trùng thông tin với column_id). | Chốt tập giá trị hoặc bỏ cột và suy từ Column. |
| 4 | Có xóa Task không (DB có ON DELETE CASCADE cho assignee và bình luận). | Đã chốt (v1.2): CÓ. Chỉ Owner; xem UC-25 và BR-43. |
| 5 | Khi Redis không có snapshot (miss/hết TTL): từ chối hay gọi lại service sở hữu? | Gọi lại service sở hữu và nạp lại Redis; lỗi thì từ chối (fail-closed). |
| 6 | Cách đánh position (INT): đánh lại cả cột hay dùng khoảng cách lớn. | Khoảng cách lớn (1000, 2000...) và đánh lại khi hết chỗ. |
| 7 | Loại thông báo ngoài ASSIGNED (lời mời, bình luận, bị loại khỏi Project). | Thêm INVITED trước; còn lại ở phiên bản sau. |
| 8 | Giá trị status của các bảng job (account/project snapshot, cleanup). | PENDING, RUNNING, SUCCESS, FAILED; thêm CHECK; thống nhất với outbox. |

## 15.3 Khuyến nghị chỉnh DB (không đổi nghiệp vụ)

**Đã quyết định bổ sung `outbox_events`/`inbox_events` cho `kanban_board_db`**(cùng schema Task Service), xem mục 12.2 và filekanban_board_outbox_inbox.sql. Cần chạy trước khi triển khai Project Service.

**`UQ_board_columns_board_position`**làm việc hoán đổi vị trí bị vướng ở bước giữa. Cập nhật hai pha (vị trí tạm) hoặc bỏ unique và kiểm tra ở ứng dụng.

**Schema outbox/inbox không đồng nhất:**Notification dùngid/message_handlervà thiếunext_attempt_at, các service khác dùngevent_id/consumer_name. Nên dùng chung một schema.

**Notification chưa chống trùng theo sự kiện.**Nên thêmevent_idvà unique (event_id,user_id,type) bên cạnh Inbox.

**Dữ liệu mẫu `cards.status = ACTIVE`**không khớp mặc định TODO (xem quyết định mở số 3). Job đồng bộ dùng statusSUCCESStrong khi Outbox dùngPROCESSED; cần thống nhất.

**Script:**thêmGOsauCREATE DATABASEvà kiểm tra tồn tại để chạy lại được; tách dữ liệu mẫu khỏi schema;TRUNCATEsẽ lỗi nếu sau này có FK tham chiếu.

**`users.password_hash NOT NULL`**sẽ vướng nếu thêm đăng nhập SSO sau này.
