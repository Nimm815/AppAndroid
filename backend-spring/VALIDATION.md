# Kết quả kiểm tra — 22/10/2026

- Build bốn service Spring Boot: thành công; có JAR thực thi độc lập.
- MySQL kiểm thử: MySQL Community Server **8.0.46**, instance riêng ở cổng 13316,
  dữ liệu trong thư mục build/mysql-it-*, không dùng database của MySQL80.
- **10 integration tests chạy đạt, 0 failed, 0 skipped**, chạy hai lần trên cùng
  instance để kiểm tra Flyway không tạo trùng bảng và Hibernate validate lại được.
- Auth: 2 tests; Task: 3 tests; Productivity: 3 tests; AI: 2 tests.
- Đã kiểm tra thực tế 4 database, đủ 12 bảng nghiệp vụ và 4 bảng flyway_schema_history.
- Không có khóa ngoại xuyên database; cấu trúc Entity/JPA khớp schema MySQL.
- Đã kiểm tra UNIQUE email, liên kết một danh sách/công việc, giữ tiến độ khi chuyển,
  chặn chuyển sang danh sách của người khác, CHECK số lượng/tiến độ/cờ boolean,
  thời gian sự kiện, đích reminder, khóa ngoại và log thói quen cùng ngày.
- Instance kiểm thử được dừng sau kiểm tra. Test data chỉ nằm trong build/ được
  gitignore; không có tài khoản mẫu được tạo trên MySQL chính.
- Chưa chạy migration trên MySQL chính: chưa có biến môi trường/tài khoản kết nối
  hợp lệ được cung cấp. Làm theo README để triển khai vào connection Workbench của bạn.
- Không thay đổi source Android, Room hoặc dữ liệu trên điện thoại.

Chi tiết báo cáo của từng service nằm ở `build/reports/tests/test/index.html` và
`build/test-results/test/TEST-*.xml`. File build là artifact cục bộ, không cần commit.
