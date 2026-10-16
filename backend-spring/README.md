# Backend Spring Boot

Backend REST API riêng cho ứng dụng Android. Java 17, Spring Boot, Spring Data JPA và MySQL.

## Kết nối MySQL

1. Tạo database từ `database/task_management_app.sql` nếu bạn chưa có database. File này dùng `CREATE TABLE IF NOT EXISTS`, không xóa bảng hiện có.
2. Mở PowerShell trong thư mục `backend-spring` và đặt thông tin kết nối trong phiên hiện tại:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/task_management_app?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "MẬT_KHẨU_MYSQL_CỦA_BẠN"
```

Không commit mật khẩu. Có thể thay host, port, tên database và username theo cấu hình MySQL của bạn.

3. Cần JDK 17 và Maven cài trên máy, chạy:

```powershell
mvn spring-boot:run
```

Ứng dụng chạy tại `http://localhost:8080`. Hibernate được cấu hình `ddl-auto=validate`: chỉ đối chiếu schema, không tự tạo/sửa/xóa bảng. Nếu thiếu bảng/cột hoặc sai kiểu dữ liệu, ứng dụng sẽ báo lỗi khi khởi động.

## API công việc ban đầu

Tạm thời API nhận `userId` trong đường dẫn vì đăng nhập chưa được xây dựng. Đây là cách thử nghiệm cho máy cá nhân, chưa phải cơ chế xác thực/phân quyền để triển khai thật.

- `GET /api/users/{userId}/tasks` — danh sách công việc
- `GET /api/users/{userId}/tasks/{taskId}` — xem một công việc
- `POST /api/users/{userId}/tasks` — tạo công việc
- `PUT /api/users/{userId}/tasks/{taskId}` — cập nhật
- `DELETE /api/users/{userId}/tasks/{taskId}` — xóa

Ví dụ nội dung POST:

```json
{
  "userId": 1,
  "title": "Hoàn thành bài tập Android",
  "description": "Làm màn hình danh sách",
  "priority": "HIGH",
  "status": "TODO",
  "progress": 0,
  "dueDate": "2026-10-20T18:00:00"
}
```

`userId` phải tồn tại trong bảng `users` do khóa ngoại của MySQL kiểm tra. Trước khi kết nối app hoặc đưa lên máy chủ, cần thay cách truyền `userId` bằng xác thực người dùng phù hợp.
