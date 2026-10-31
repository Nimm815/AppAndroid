# Chạy backend trên Windows

Backend dùng bốn service Spring Boot riêng, mỗi service kết nối đúng database của nó. Schema phải được tạo sẵn; cấu hình hiện tại tắt Hibernate DDL và SQL initialization. Script không tạo bảng hay chạy migration.

## Điều kiện

- Java 21 trên `PATH`.
- MySQL đang chạy và đã có bốn database cùng các bảng mới.
- Tài khoản MySQL được cấp quyền trên database tương ứng.
- Đã build JAR sau khi sửa code.

Tại `backend-spring`, build ba service Android đang dùng:

```powershell
..\android-app\gradlew.bat :auth-service:bootJar :task-service:bootJar :productivity-service:bootJar
```

Sau đó khởi động:

```powershell
.\tools\Start-TaskMindDev.ps1
```

Script hỏi username/mật khẩu MySQL (mật khẩu được nhập ẩn), kiểm tra service và khởi động Auth `8081`, Task `8082`, Productivity `8083`. Thông tin database chỉ tồn tại trong terminal hiện tại. Không gửi mật khẩu qua chat.

Kiểm tra và dừng service:

```powershell
.\tools\Check-Services.ps1 | Format-Table -AutoSize
.\tools\Stop-AllServices.ps1
```

Log stdout/stderr nằm trong `build/services`. Nếu service dừng lúc khởi động, xem log mới nhất của service đó. `RUNNING` chỉ xác nhận tiến trình đã khởi động; thao tác API cần database hợp lệ và token đăng nhập.

## Bốn database

- Auth: `taskmind_auth_db.users`
- Task: `taskmind_task_db.tasks`, `tasklists`, `tasklistitems`
- Productivity: `taskmind_productivity_db.habits`, `habitlogs`, `journalentries`, `calendarevents`, `reminders`, `notifications`
- AI: `taskmind_ai_db.aichats`, `aichatmessages`

Auth/Task/Productivity là các API nghiệp vụ đang dùng. AI Service có mapping dữ liệu nhưng tính năng AI chưa triển khai. Snapshot sync cũ không dùng vì cần bảng không nằm trong schema được yêu cầu.
