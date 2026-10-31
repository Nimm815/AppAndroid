# Backend Spring Boot

Backend gồm các Spring Boot microservice Auth, Task, Productivity và AI Service viết bằng Python + FastAPI. Mỗi service dùng database riêng hiện có: Auth (`taskmind_auth_db.users`), Task (`taskmind_task_db.tasks`, `tasklists`, `tasklistitems`), Productivity (`taskmind_productivity_db.habits`, `habitlogs`, `journalentries`, `calendarevents`, `reminders`, `notifications`) và AI (`taskmind_ai_db.aichats`, `aichatmessages`). Backend không tự tạo bảng hoặc chạy migration. Xem `ai-service/README.md` để cài và chạy FastAPI/Gemini.

## Khởi động TaskMind Development (Windows)

Sau khi cài đặt MySQL, Java 21, Android SDK và đã build JAR cho Auth, Task và Productivity Service, mở Terminal tại `backend-spring` và chạy:

```powershell
.\tools\Start-TaskMindDev.ps1
```

Script sẽ kiểm tra và thử khởi động MySQL Windows Service, hỏi username/mật khẩu nếu Terminal chưa có credentials, rồi khởi động Auth (`8081`), Task (`8082`) và Productivity (`8083`). Auth chỉ bind vào localhost; Task và Productivity chạy profile `dev` để bật các API mà ứng dụng Android/Admin Web hiện gọi. Username mặc định lần lượt là `taskmind_auth`, `taskmind_task` và `taskmind_productivity`; mật khẩu được nhập ẩn, chỉ truyền trong môi trường của tiến trình hiện tại và không được ghi vào source code hoặc log. Nếu chỉ có tài khoản MySQL `root` trong môi trường phát triển cục bộ, có thể truyền `-DatabaseUsername root`; script hỏi mật khẩu đó một lần và dùng cho ba service. Không dùng tài khoản root trong môi trường production.

Script dùng lại `Set-DatabaseEnvironment.ps1`, `Start-AllServices.ps1` và `Check-Services.ps1`. Tiến trình đã chạy đúng JAR được quản lý và cấu hình DevelopmentApi sẽ được giữ nguyên; script không build lại JAR. Khi source thay đổi, build JAR trước bằng lệnh sau (chỉ cần chạy khi có thay đổi code):

```powershell
..\android-app\gradlew.bat :auth-service:bootJar :task-service:bootJar :productivity-service:bootJar
```

Không có thao tác xóa database/dữ liệu. `ddl-auto=none` và SQL init bị tắt; backend chỉ kết nối các bảng đã có.

Nếu có điện thoại được cấp quyền ADB qua USB, script tìm `adb.exe` trong Android SDK và thiết lập/xác minh `adb reverse` cho cổng `8081`, `8082` và `8083`. Không kết nối điện thoại hoặc không tìm thấy ADB chỉ tạo cảnh báo, không ngăn backend chạy. `-PreflightOnly` chỉ kiểm tra MySQL, credentials và ADB, không khởi động backend:

```powershell
.\tools\Start-TaskMindDev.ps1 -PreflightOnly
```

Nếu service không khởi động, script báo trạng thái và đường dẫn `stdout`/`stderr` trong `backend-spring\build\services`. AI Service không được khởi động.

Hồ sơ tài khoản thuộc Auth Service. Theo các cột schema đã nhận, `users` chỉ lưu tên, email và thông tin xác thực; không có số điện thoại, ngày sinh, giới tính hoặc avatar.

Android có luồng đăng ký/đăng nhập trong **Cài đặt → Tài khoản**. Auth Service cấp bearer token; Task và Productivity xác thực token qua Auth Service để lấy đúng UserID. Các màn hình offline hiện giữ dữ liệu cục bộ; backend không còn endpoint snapshot sync.

API hồ sơ yêu cầu `Authorization: Bearer <token>` hợp lệ:

- `GET /api/users/me`: đọc hồ sơ người dùng từ phiên đã xác thực.
- `PUT /api/users/me`: cập nhật tên và email; schema hiện tại không có cột điện thoại/ngày sinh/giới tính nên gửi các giá trị đó sẽ nhận `422`.
- Avatar chưa có API vì schema hiện tại không có cột/lưu trữ ảnh.

API đăng nhập và đăng ký (Auth Service, 8081):

- `POST /api/auth/register` với `{"email":"...","password":"...","fullName":"..."}`: tạo tài khoản và trả bearer token; email đã dùng trả `409`.
- `POST /api/auth/login` với `{"email":"...","password":"..."}`: xác thực tài khoản và trả bearer token.
- `GET /api/auth/me`, `POST /api/auth/logout`: kiểm tra token; do không có bảng session, logout được xử lý phía client và token hết hạn sau 30 ngày (hoặc bị vô hiệu khi đổi mật khẩu/khóa/xóa tài khoản).

Task Service có CRUD `/api/lists`, `/api/tasks`; Productivity có CRUD `/api/habits`, `/api/journal-entries`, `/api/calendar-events`, `/api/reminders` và đọc/đánh dấu `/api/notifications`. Với thói quen, các ngày lặp được giới hạn theo enum `DAILY` hoặc một ngày `WEEKLY`; icon/màu/nhắc giờ không có cột lưu. Task không nhận ghi chú riêng. Tính năng gửi yêu cầu lưu những trường không tồn tại sẽ bị từ chối thay vì ghi sai.

Task Service (`8082`) cung cấp `/api/lists` và `/api/tasks`. Productivity Service (`8083`) cung cấp `/api/habits`, `/api/journal-entries`, `/api/calendar-events`, `/api/reminders` và `/api/notifications`. APIs chỉ sử dụng bảng nghiệp vụ đã có. Đồng bộ snapshot từ `WorkspaceSyncWorker` chưa được backend hỗ trợ vì cần bảng ánh xạ thiết bị/lịch sử không có trong schema mới; giữ dữ liệu trên thiết bị cho tới khi app chuyển sang CRUD API trực tiếp.

## API Admin trong microservices

Các API Admin phát triển được đặt theo ranh giới dữ liệu của service:

- Auth (`8081`): danh sách, sửa vai trò/tên, khóa/mở khóa, xóa mềm và thống kê người dùng từ `taskmind_auth_db`.
- Task (`8082`): tổng số công việc và số công việc theo người dùng từ `taskmind_task_db`.
- Productivity (`8083`): thống kê tổng hợp trực tiếp từ `habitlogs`, `journalentries` và `calendarevents` trong `taskmind_productivity_db`.

Các API Admin chỉ dùng cục bộ trong phát triển. Task và Productivity cần profile `dev` (được bật bởi `Start-TaskMindDev.ps1`); Auth cũng bind localhost khi chạy script. Chưa có xác thực/ủy quyền quản trị, nên không đưa các API này lên mạng công khai. API tạo tài khoản Admin Web và nhật ký đăng nhập/thao tác quản trị chưa được triển khai.
