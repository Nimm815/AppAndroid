# Backend Spring Boot

Backend gồm các Spring Boot microservice chạy riêng: Auth quản lý tài khoản, Task quản lý công việc/danh sách, Productivity quản lý thói quen/nhật ký/lịch/nhắc việc. Mỗi service dùng schema MySQL riêng và trao đổi qua API. Android giữ bản cục bộ để dùng offline rồi đồng bộ lên các service tương ứng. Admin Web cũng gọi API từng service; React không kết nối trực tiếp MySQL.

Các file trong `backend-spring/src/main` là backend đơn khối cũ, không được `settings.gradle` đưa vào build và không được script khởi động. Chúng được giữ để tham khảo; không dùng chúng cho app hiện tại.

## Khởi động TaskMind Development (Windows)

Sau khi cài đặt MySQL, Java 21, Android SDK và đã build JAR cho Auth, Task và Productivity Service, mở Terminal tại `backend-spring` và chạy:

```powershell
.\tools\Start-TaskMindDev.ps1
```

Script sẽ kiểm tra và thử khởi động MySQL Windows Service, hỏi username/mật khẩu nếu Terminal chưa có credentials, rồi khởi động Auth (`8081`), Task (`8082`) và Productivity (`8083`). Auth chỉ bind vào localhost; Task và Productivity chạy profile `DevelopmentApi`. Username mặc định lần lượt là `taskmind_auth`, `taskmind_task` và `taskmind_productivity`; mật khẩu được nhập ẩn, chỉ truyền trong môi trường của tiến trình hiện tại và không được ghi vào source code hoặc log. Nếu chỉ có tài khoản MySQL `root` trong môi trường phát triển cục bộ, có thể truyền `-DatabaseUsername root`; script hỏi mật khẩu đó một lần và dùng cho ba service. Không dùng tài khoản root trong môi trường production.

Script dùng lại `Set-DatabaseEnvironment.ps1`, `Start-AllServices.ps1` và `Check-Services.ps1`. Tiến trình đã chạy đúng JAR được quản lý và cấu hình DevelopmentApi sẽ được giữ nguyên; script không build lại JAR. Khi source thay đổi, build JAR trước bằng lệnh sau (chỉ cần chạy khi có thay đổi code):

```powershell
..\android-app\gradlew.bat :auth-service:bootJar :task-service:bootJar :productivity-service:bootJar
```

Không có thao tác xóa database/dữ liệu hoặc sửa migration đã áp dụng. Flyway sẽ tự áp dụng migration mới khi service khởi động: Task V4 bổ sung thời điểm tạo công việc; Productivity V6 bổ sung dữ liệu nhật ký, sự kiện, lời nhắc và lịch sử hoạt động cần cho bản đồng bộ Android. Các migration chỉ bổ sung cấu trúc.

Nếu có điện thoại được cấp quyền ADB qua USB, script tìm `adb.exe` trong Android SDK và thiết lập/xác minh `adb reverse` cho cổng `8081`, `8082` và `8083`. Không kết nối điện thoại hoặc không tìm thấy ADB chỉ tạo cảnh báo, không ngăn backend chạy. `-PreflightOnly` chỉ kiểm tra MySQL, credentials và ADB, không khởi động backend:

```powershell
.\tools\Start-TaskMindDev.ps1 -PreflightOnly
```

Nếu service không khởi động, script báo trạng thái và đường dẫn `stdout`/`stderr` trong `backend-spring\build\services`. AI Service không được khởi động.

Hồ sơ tài khoản thuộc Auth Service. Flyway V3 bổ sung số điện thoại, ngày sinh, giới tính và tham chiếu ảnh vào `taskmind_auth_db.users`; migration chỉ thêm cột nullable, không xóa dữ liệu. Ảnh được giới hạn 5 MB, xác thực/chuẩn hóa thành JPEG, lưu dưới `AUTH_AVATAR_DIR` (mặc định `backend-spring\build\auth-avatars`), còn MySQL chỉ lưu tên tham chiếu tệp. Hãy giữ thư mục ảnh khi sao lưu database.

Android có luồng đăng ký/đăng nhập trong **Cài đặt → Tài khoản**. Auth Service cấp bearer token; Task và Productivity xác thực token qua Auth Service để lấy đúng UserID. Dữ liệu trước đăng nhập vẫn lưu trên thiết bị. Sau lần đăng nhập đầu tiên, dữ liệu cục bộ được gắn với tài khoản đó; đăng xuất giữ bản cục bộ nhưng tạm dừng đồng bộ.

API hồ sơ yêu cầu `Authorization: Bearer <token>` hợp lệ:

- `GET /api/users/me`: đọc hồ sơ người dùng từ phiên đã xác thực.
- `PUT /api/users/me`: cập nhật tên, email, số điện thoại, ngày sinh và giới tính; email trùng trả `409`.
- `POST /api/users/me/avatar` (multipart field `file`): xác thực và lưu ảnh đại diện.
- `GET /api/users/me/avatar`: tải ảnh đại diện của chính người dùng.

## API Admin trong microservices

Các API Admin phát triển được đặt theo ranh giới dữ liệu của service:

- Auth (`8081`): danh sách, sửa vai trò/tên, khóa/mở khóa, xóa mềm và thống kê người dùng từ `taskmind_auth_db`.
- Task (`8082`): tổng số công việc và số công việc theo người dùng từ `taskmind_task_db`.
- Productivity (`8083`): thống kê và lịch sử công việc/thói quen từ `daily_activity_history` trong `taskmind_productivity_db`.

Các API Admin chỉ dùng cục bộ trong phát triển. Task và Productivity cần profile `dev` (được bật bởi `Start-TaskMindDev.ps1`); Auth cũng bind localhost khi chạy script. Chưa có xác thực/ủy quyền quản trị, nên không đưa các API này lên mạng công khai. API tạo tài khoản Admin Web và nhật ký đăng nhập/thao tác quản trị chưa được triển khai. Mã backend đơn khối cũ vẫn được giữ để tham khảo nhưng Admin Web không còn gọi nó.
