# Backend Spring Boot

Backend cung cấp API quản trị người dùng và thống kê hệ thống cho Admin Web. Cấu hình hiện tại sử dụng schema MySQL có sẵn; Hibernate chỉ kiểm tra schema, không tạo/sửa/xóa bảng.

## Khởi động TaskMind Development (Windows)

Sau khi cài đặt MySQL, Java 21, Android SDK và đã build JAR cho Auth, Task và Productivity Service, mở Terminal tại `backend-spring` và chạy:

```powershell
.\tools\Start-TaskMindDev.ps1
```

Script sẽ kiểm tra và thử khởi động MySQL Windows Service, hỏi username/mật khẩu nếu Terminal chưa có credentials, rồi khởi động Auth (`8081`), Task (`8082`) và Productivity (`8083`). Auth chỉ bind vào localhost; Task và Productivity chạy profile `DevelopmentApi`. Username mặc định lần lượt là `taskmind_auth`, `taskmind_task` và `taskmind_productivity`; mật khẩu được nhập ẩn, chỉ truyền trong môi trường của tiến trình hiện tại và không được ghi vào source code hoặc log. Nếu username trong Terminal không đúng, script sẽ yêu cầu nhập mật khẩu cho username mặc định chính xác trước khi khởi động backend.

Script dùng lại `Set-DatabaseEnvironment.ps1`, `Start-AllServices.ps1` và `Check-Services.ps1`. Tiến trình đã chạy đúng JAR được quản lý và cấu hình DevelopmentApi sẽ được giữ nguyên; script không build lại JAR. Khi source thay đổi, build JAR trước bằng lệnh sau (chỉ cần chạy khi có thay đổi code):

```powershell
..\android-app\gradlew.bat :auth-service:bootJar :task-service:bootJar :productivity-service:bootJar
```

Không có thao tác xóa database/dữ liệu hoặc sửa migration đã áp dụng.

Nếu có điện thoại được cấp quyền ADB qua USB, script tìm `adb.exe` trong Android SDK và thiết lập/xác minh `adb reverse` cho cổng `8081`, `8082` và `8083`. Không kết nối điện thoại hoặc không tìm thấy ADB chỉ tạo cảnh báo, không ngăn backend chạy. `-PreflightOnly` chỉ kiểm tra MySQL, credentials và ADB, không khởi động backend:

```powershell
.\tools\Start-TaskMindDev.ps1 -PreflightOnly
```

Nếu service không khởi động, script báo trạng thái và đường dẫn `stdout`/`stderr` trong `backend-spring\build\services`. AI Service không được khởi động.

Hồ sơ tài khoản thuộc Auth Service. Flyway V3 bổ sung số điện thoại, ngày sinh, giới tính và tham chiếu ảnh vào `taskmind_auth_db.users`; migration chỉ thêm cột nullable, không xóa dữ liệu. Ảnh được giới hạn 5 MB, xác thực/chuẩn hóa thành JPEG, lưu dưới `AUTH_AVATAR_DIR` (mặc định `backend-spring\build\auth-avatars`), còn MySQL chỉ lưu tên tham chiếu tệp. Hãy giữ thư mục ảnh khi sao lưu database.

Android hiện chưa có màn hình đăng nhập/đăng ký nên chưa thể tạo phiên bearer token mới. Khi chưa có token hợp lệ, hồ sơ lưu trong app-private storage trên điện thoại và màn hình thông báo rõ rằng chưa đồng bộ MySQL. Khi tích hợp đăng nhập bằng token hợp lệ, Android tải/cập nhật hồ sơ qua Auth Service. Không tạo tài khoản hay user ID giả để vượt qua yêu cầu xác thực.

API hồ sơ yêu cầu `Authorization: Bearer <token>` hợp lệ:

- `GET /api/users/me`: đọc hồ sơ người dùng từ phiên đã xác thực.
- `PUT /api/users/me`: cập nhật tên, email, số điện thoại, ngày sinh và giới tính; email trùng trả `409`.
- `POST /api/users/me/avatar` (multipart field `file`): xác thực và lưu ảnh đại diện.
- `GET /api/users/me/avatar`: tải ảnh đại diện của chính người dùng.

## API Admin (phát triển cục bộ)

- `GET /api/admin/users`: tìm kiếm, lọc theo trạng thái/vai trò và phân trang.
- `PATCH /api/admin/users/{id}`: sửa họ tên hoặc vai trò.
- `PATCH /api/admin/users/{id}/lock`: khóa/mở khóa tài khoản.
- `DELETE /api/admin/users/{id}`: xóa mềm.
- `GET /api/admin/stats`: số liệu người dùng, công việc và hoạt động 30 ngày.

Các API trên hiện chỉ chấp nhận yêu cầu từ máy cục bộ. Chưa có xác thực/ủy quyền quản trị; không đưa server này lên mạng công khai trước khi bổ sung chính sách đăng nhập và phân quyền. API tạo tài khoản cũng chưa có vì chưa thống nhất cách khởi tạo thông tin đăng nhập.
