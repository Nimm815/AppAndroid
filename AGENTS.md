Tôi đang làm đề tài:
“Xây dựng hệ thống quản lý công việc cá nhân thông minh sử dụng
ReactJS, Spring Boot, FastAPI và kiến trúc Microservices”.

Bối cảnh:
- Sản phẩm chính là app Android.
- Tôi học Android bằng Java, không dùng Kotlin.
- App Android dùng Java + XML.
- Android Studio dùng để tạo dự án, xem giao diện, build và chạy app.
- VS Code + Codex dùng để đọc, viết và sửa code.
- Hai công cụ mở cùng thư mục android-app, nên dùng chung các file.
- Web ReactJS chỉ là phần phụ dành cho quản trị hệ thống.

Các phần dự kiến:
1. android-app: app Android bằng Java + XML.
2. backend-spring: backend Java bằng Spring Boot, quản lý tài khoản,
   công việc, kiểm tra quyền và cung cấp API.
3. ai-service: dịch vụ Python bằng FastAPI để gợi ý thứ tự làm việc.
4. admin-web: web quản trị bằng ReactJS.
5. Cơ sở dữ liệu: dự kiến MySQL, chưa chốt cấu hình.

App Android và backend chạy riêng, kết nối qua API.
Đặt chung thư mục hoặc repository không tự kết nối các phần này.

Chức năng app:
- Đăng ký, đăng nhập.
- Thêm, xem, sửa, xóa công việc.
- Hạn hoàn thành, mức ưu tiên, trạng thái.
- Tìm kiếm, lọc, nhắc việc.
- Gợi ý thứ tự thực hiện công việc.

Cách hướng dẫn:
- Tôi là người mới, hãy giải thích bằng tiếng Việt, dễ hiểu.
- Làm từng bước nhỏ chạy được, giải thích file nào có vai trò gì.
- Ưu tiên code đơn giản, dễ đọc.
- Bắt đầu với giao diện và quản lý công việc cơ bản trên Android,
  sau đó kết nối Spring Boot và cơ sở dữ liệu.
- Bổ sung FastAPI và web ReactJS sau.
- Không tự coi MySQL, cách đăng nhập hoặc thuật toán AI là đã chốt.
- Thiết kế ranh giới các dịch vụ rõ ràng để đáp ứng yêu cầu Microservices.

Hãy kiểm tra thư mục hiện tại trước, rồi tạo file AGENTS.md ghi lại
bối cảnh và các nguyên tắc trên. Chưa triển khai toàn bộ hệ thống.
Sau đó cho tôi biết bước đầu tiên cần làm với dự án hiện có.
## Phạm vi được bổ sung trong phiên làm việc
- Người dùng đã yêu cầu phát triển giao diện và chức năng tham khảo HelloHabit,
  sau đó yêu cầu thêm toàn bộ các màn hình trong 9 ảnh tham khảo.
- Tiếp tục dùng Java + XML, tiếng Việt và triển khai theo từng bước chạy được.
- Bản hiện tại ưu tiên đầy đủ các màn hình Android và thao tác cục bộ;
  đăng nhập, đồng bộ, Spring Boot, FastAPI và ReactJS vẫn thuộc giai đoạn sau.
- Giữ dữ liệu công việc Room hiện có khi thay giao diện; không gỡ app/xóa dữ liệu để cập nhật.

## Nhật ký hoạt động
- Nhật ký là lịch sử công việc và thói quen theo từng ngày, gồm ghi chú kết quả và mức hoàn thành; không thiết kế như trang viết nhật ký tự do.
- Tham khảo ảnh mẫu: thẻ ghi chú, ngày giờ, nhãn hoạt động, mức hoàn thành và menu tùy chọn.
- Ghi nhận thay đổi hoàn thành tự động; giữ ghi chú cũ và dữ liệu Room, không suy đoán thời điểm hoàn thành của dữ liệu trước khi có lịch sử.

- Làm rõ: Nhật ký tự tổng hợp toàn bộ công việc/thói quen của từng ngày đã qua từ Trang chủ, gồm chưa làm (0%), làm một phần và hoàn thành (100%). Mỗi ngày có tỷ lệ tổng hợp; kết quả ngày cũ phải giữ nguyên khi xử lý công việc vào ngày sau.
