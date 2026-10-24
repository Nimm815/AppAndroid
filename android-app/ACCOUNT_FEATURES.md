# TaskMind - Chức năng tài khoản

Thư mục này là dự án Android hiện có của bạn. Các thay đổi Android cho đăng ký, đăng nhập, đăng xuất và xóa tài khoản nằm trong `app/`.

## API kết nối MySQL

Mã nguồn API Spring Boot được đặt trong thư mục con `backend/` để toàn bộ mã nguồn nằm bên trong thư mục `android-app`. Đây là tiến trình máy chủ riêng, không phải module Android và cần chạy độc lập để ứng dụng kết nối MySQL an toàn.

Cấu hình kết nối cơ sở dữ liệu `task_management_app` tại `backend/src/main/resources/application.properties` hoặc qua biến môi trường `DB_USERNAME`, `DB_PASSWORD` và `JWT_SECRET`.

Trình giả lập Android dùng `http://10.0.2.2:8080` để truy cập máy chủ đang chạy trên máy tính. Điện thoại thật cần thay địa chỉ này bằng IP LAN của máy tính. HTTP chỉ dùng khi phát triển; triển khai thực tế cần HTTPS.

## Giới hạn kiểm tra

XML đã được kiểm tra cú pháp và gói ZIP đã được kiểm tra tính toàn vẹn trước khi đóng gói. Chưa xác nhận build Android/backend hoặc kiểm thử kết nối MySQL thực tế trong môi trường này.
