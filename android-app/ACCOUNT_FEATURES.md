# Tài khoản và đồng bộ Android

Tài liệu cũ về backend đơn khối `task_management_app` đã lỗi thời. Cấu hình hiện tại nằm trong `app/build.gradle`; API tài khoản chạy qua Auth Service, còn công việc và dữ liệu năng suất đồng bộ qua Task Service và Productivity Service.

Xem hướng dẫn cài đặt, chạy backend, đồng bộ và giới hạn hiện tại trong [README.md](README.md). Các dịch vụ lưu lần lượt vào `taskmind_auth_db`, `taskmind_task_db` và `taskmind_productivity_db`. AI Service chưa được nối vào app. Không xóa database cũ hoặc nạp dump có lệnh `DROP TABLE` khi chưa đối chiếu dữ liệu.
