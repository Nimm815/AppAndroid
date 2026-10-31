# Chạy backend trên Windows

Auth, Task và Productivity là ba Spring Boot service. AI là Python + FastAPI và chạy riêng ở cổng 8084. Các service kết nối database riêng đã có; cấu hình tắt tạo bảng và migration tự động.

## Chạy Auth, Task, Productivity

Yêu cầu Java 21 và MySQL. Build các service Java từ thư mục `backend-spring`:

```powershell
..\android-app\gradlew.bat :auth-service:bootJar :task-service:bootJar :productivity-service:bootJar
```

Khởi động development backend:

```powershell
.\tools\Start-TaskMindDev.ps1
```

Script hỏi credentials MySQL cho ba service rồi khởi động Auth `8081`, Task `8082`, Productivity `8083`. Mật khẩu nhập ẩn và chỉ tồn tại trong terminal hiện tại.

## Chạy AI Service

Cài Python, cấu hình `backend-spring/ai-service/.env` theo `.env.example`, rồi chạy Uvicorn theo hướng dẫn trong `backend-spring/ai-service/README.md`. AI dùng `taskmind_ai_db`; không đưa Gemini key vào app Android.

`Check-Services.ps1` và `Stop-AllServices.ps1` quản lý ba tiến trình Java. Dừng Uvicorn bằng `Ctrl+C` trong terminal chạy AI.

## Database

- Auth: `taskmind_auth_db.users`
- Task: `taskmind_task_db.tasks`, `tasklists`, `tasklistitems`
- Productivity: `taskmind_productivity_db.habits`, `habitlogs`, `journalentries`, `calendarevents`, `reminders`, `notifications`
- AI: `taskmind_ai_db.aichats`, `aichatmessages`

AI Service đọc dữ liệu nghiệp vụ qua API có xác thực của Auth, Task và Productivity; chỉ hội thoại được ghi trong database AI. Không tự tạo bảng hay chạy migration.
