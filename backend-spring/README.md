# Backend Spring Boot

Giai đoạn hiện tại chỉ khởi động server và kiểm tra kết nối tới MySQL. Chưa có API nghiệp vụ.

## Chạy backend

Mở PowerShell tại thư mục gốc `C:\PTUDAndroid` và chạy một lệnh:

```powershell
.\backend-spring\mvnw.cmd -f .\backend-spring\pom.xml spring-boot:run
```

Maven Wrapper tự tải Maven 3.10.0 nếu máy chưa có, nên không cần cài Maven toàn máy. Lần đầu cần Internet để tải Maven và các thư viện. Tài khoản/mật khẩu MySQL đọc từ `backend-spring/src/main/resources/application.properties`; các thành viên cần cấu hình file này theo cùng database.

Server dùng cổng 8082 mặc định để tránh các phiên backend cũ đang giữ 8080/8081. Nếu cổng bị chiếm, dừng server cũ bằng `Ctrl+C` hoặc đặt `SERVER_PORT` khác trước khi chạy.

## Kiểm tra kết nối database

Khi thấy log `✅ KẾT NỐI BACKEND VÀ MYSQL THÀNH CÔNG`, backend đã sẵn sàng. Có thể kiểm tra bằng PowerShell mới:

```powershell
Invoke-RestMethod -Uri "http://localhost:8082/api/health/database"
```

Kết quả thành công có trạng thái `connected` và tên database. Hibernate đặt `ddl-auto=validate`, chỉ kiểm tra schema, không tự sửa hay xóa bảng.
