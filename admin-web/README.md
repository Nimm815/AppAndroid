# Admin Web

Giao diện quản trị hệ thống TaskFlow, viết bằng React và Vite. Đây là ứng dụng web riêng, chạy độc lập với `android-app` và `backend-spring`.

## Chạy trên máy

1. Cài Node.js bản LTS nếu máy chưa có.
2. Mở Terminal tại thư mục `admin-web`.
3. Chạy `npm install` để cài thư viện.
4. Chạy `npm run dev` và mở địa chỉ được Vite hiển thị (thường là `http://localhost:5173`).

## Phạm vi hiện tại

- UC14: xem danh sách người dùng mẫu; tìm kiếm, lọc theo trạng thái/vai trò; thêm và sửa hồ sơ; khóa/mở khóa; xóa mềm (đánh dấu trạng thái đã xóa).
- UC15: bảng tổng quan với các thẻ số liệu, biểu đồ hoạt động và phân bố trạng thái. Số liệu biểu đồ đang là dữ liệu minh họa.
- Không có kết nối API, đăng nhập quản trị hay cơ sở dữ liệu ở bước này. Thay đổi người dùng chỉ tồn tại trong bộ nhớ trình duyệt và sẽ mất khi tải lại trang.

## Cấu trúc chính

- `src/App.jsx`: màn hình tổng quan, bảng người dùng và tương tác giao diện.
- `src/styles.css`: giao diện responsive cho màn hình lớn và điện thoại.
- `src/main.jsx`: điểm khởi chạy React.
- `package.json`: thư viện và lệnh chạy/build.

Khi bổ sung API, React sẽ gọi Spring Boot qua HTTP. Spring Boot chịu trách nhiệm phân quyền, kiểm tra dữ liệu và lưu trữ; Admin Web không kết nối trực tiếp cơ sở dữ liệu.
