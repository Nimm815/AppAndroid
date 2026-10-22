# Admin Web

Giao diện quản trị hệ thống TaskMind, viết bằng React và Vite. Đây là ứng dụng web riêng, chạy độc lập với `android-app` và `backend-spring`.

## Chạy trên máy

1. Cài Node.js bản LTS nếu máy chưa có.
2. Mở Terminal tại thư mục `admin-web`.
3. Chạy `npm install` để cài thư viện.
4. Chạy `npm run dev` và mở địa chỉ được Vite hiển thị (thường là `http://localhost:5173`).

Sau khi thư viện đã được cài, bạn có thể bấm đúp vào `Mo-Admin.bat`; file này khởi động giao diện và mở địa chỉ Admin trên trình duyệt. Spring Boot vẫn cần được chạy riêng để xem và sửa dữ liệu thật.

## Phạm vi hiện tại

- UC14: đọc danh sách từ API; tìm kiếm, lọc và phân trang; sửa họ tên/vai trò; khóa/mở khóa; xóa mềm.
- UC15: thẻ tổng hợp, biểu đồ hoạt động 30 ngày và phân bố trạng thái lấy từ API.
- Tạo tài khoản đang tạm để sau: bảng hiện tại bắt buộc có `PasswordHash`, còn cách đăng nhập và cấp mật khẩu chưa được quyết định.
- Không lưu mật khẩu/token phía Admin Web. API quản trị hiện chỉ chấp nhận kết nối từ máy cục bộ trong giai đoạn phát triển; chưa bật đăng nhập/phân quyền quản trị để tránh tự chốt chính sách tài khoản.
- Nếu chưa kết nối được, hãy kiểm tra Spring Boot, MySQL và schema `task_management_app` đã chạy.

## Cấu trúc chính

- `src/App.jsx`: màn hình tổng quan, bảng người dùng và tương tác giao diện.
- `src/styles.css`: giao diện responsive cho màn hình lớn và điện thoại.
- `src/main.jsx`: điểm khởi chạy React.
- `package.json`: thư viện và lệnh chạy/build.

Admin Web gọi Spring Boot qua HTTP tại `http://localhost:8082/api/admin`. Spring Boot truy vấn cơ sở dữ liệu; React không kết nối trực tiếp MySQL.

Có thể đổi địa chỉ API bằng biến `VITE_ADMIN_API_URL` trước khi chạy Vite.
