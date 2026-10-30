# Admin Web

Giao diện quản trị hệ thống TaskMind, viết bằng React và Vite. Đây là ứng dụng web riêng, chạy độc lập với `android-app` và `backend-spring`.

## Chạy trên máy

1. Cài Node.js bản LTS nếu máy chưa có.
2. Mở Terminal tại thư mục `admin-web`.
3. Chạy `npm install` để cài thư viện.
4. Chạy `npm run dev` và mở địa chỉ được Vite hiển thị (thường là `http://localhost:5173`).

Sau khi thư viện đã được cài, bạn có thể bấm đúp vào `Mo-Admin.bat`; file này khởi động giao diện và mở địa chỉ Admin trên trình duyệt. Trước đó cần build các service sau khi thay đổi code rồi chạy `backend-spring/tools/Start-TaskMindDev.ps1`; script sẽ khởi động Auth, Task và Productivity với API Admin chỉ dùng trên máy phát triển.

## Phạm vi hiện tại

- UC14: đọc danh sách từ API; tìm kiếm, lọc và phân trang; sửa họ tên/vai trò; khóa/mở khóa; xóa mềm.
- UC15: thẻ tổng hợp, biểu đồ hoạt động 30 ngày và phân bố trạng thái lấy từ các API của Auth, Task và Productivity service.
- Hồ sơ người dùng được đọc/cập nhật trong `taskmind_auth_db`; số công việc lấy từ `taskmind_task_db`; lịch sử hoạt động cá nhân lấy từ `taskmind_productivity_db`.
- React gọi HTTP API của từng service; React không kết nối trực tiếp MySQL và các service không truy vấn chéo database.
- Trang Hoạt động hiện đọc `daily_activity_history` (công việc/thói quen) từ Productivity service. Bảng này không phải nhật ký đăng nhập hoặc nhật ký thao tác quản trị.
- Tạo tài khoản đang tạm để sau: bảng hiện tại bắt buộc có `PasswordHash`, còn cách đăng nhập và cấp mật khẩu chưa được quyết định.
- Không lưu mật khẩu/token phía Admin Web. API quản trị hiện chỉ dùng trên máy cục bộ trong giai đoạn phát triển; chưa bật đăng nhập/phân quyền quản trị.
- `taskmind_ai_db` chưa được Admin Web dùng vì giao diện chưa có chức năng quản trị AI hoặc API thống kê AI. Không nối database này trực tiếp từ React.
- `task_management_app` cũ chưa bị xóa và dữ liệu cũ chưa được di chuyển. Các API Admin mới không đọc schema cũ.

## Cấu trúc chính

- `src/App.jsx`: màn hình tổng quan, bảng người dùng và tương tác giao diện.
- `src/styles.css`: giao diện responsive cho màn hình lớn và điện thoại.
- `src/main.jsx`: điểm khởi chạy React.
- `package.json`: thư viện và lệnh chạy/build.

Admin Web gọi ba API qua HTTP:

- Auth: `http://localhost:8081/api/admin` — tài khoản và thống kê người dùng.
- Task: `http://localhost:8082/api/admin` — số công việc và thống kê công việc.
- Productivity: `http://localhost:8083/api/admin` — thống kê số bản ghi công việc/thói quen (không trả nội dung riêng tư).

Địa chỉ có thể đổi bằng `VITE_AUTH_ADMIN_API_URL`, `VITE_TASK_ADMIN_API_URL` và `VITE_PRODUCTIVITY_ADMIN_API_URL` trước khi chạy Vite.

Các API Auth chỉ cho kết nối cục bộ; API Task và Productivity chỉ được nạp ở profile `dev`. Đây là tích hợp phát triển, chưa phải cơ chế phân quyền Admin để triển khai production.
