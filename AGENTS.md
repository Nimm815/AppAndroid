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

## Hướng dẫn lần đầu
- Luồng hiện tại: mở lần đầu → hướng dẫn → Trang chủ; các lần sau vào Trang chủ. Chưa đưa đăng nhập vào luồng sử dụng.
- Lưu riêng `onboarding_completed` trong SharedPreferences `app_preferences`, không coi đây là trạng thái đăng nhập.
- Bốn trang giới thiệu Trang chủ (công việc và thói quen), Nhật ký, Lịch và Cài đặt; có thể xem lại từ Cài đặt mà không xóa dữ liệu.
- Dùng ảnh chụp thực tế các màn hình chính của app, kèm chú thích XML để người dùng hình dung giao diện. Không thay bằng các thẻ ví dụ chung chung.

## Triển khai Kế hoạch UC09 - UC12 (Đã được duyệt)
Đã hoàn thành phát triển 4 use case chính theo tài liệu `Ke_hoach_UC09_UC12_HelloHabit.pdf`:
1. **UC09 — Quản lý nhật ký hoạt động**:
   - Dòng thời gian phân biệt ghi chú người dùng và hoạt động hệ thống.
   - Tìm kiếm tiếng Việt không dấu (thuật toán `VietnameseUtils.containsIgnoreAccents`) với debounce 300ms.
   - 3 bộ lọc kết hợp đồng thời (AND): Khoảng ngày, Thói quen liên quan, Đánh dấu.
   - Thao tác đánh dấu bookmark 1 chạm trực tiếp trên thẻ bài viết.
   - Form thêm/sửa ghi chú chặn thời điểm tương lai, kiểm tra độ dài 1-2.000 ký tự.
   - Hộp thoại xác nhận xóa: "Xóa ghi chú này?".
   - Phân trang lazy-load 30 mục mỗi lần và thông báo trạng thái trống chuẩn.
2. **UC10 — Quản lý lịch**:
   - Lưới lịch tháng 42 ô (6 hàng x 7 cột), hiển thị chấm báo ngày có sự kiện.
   - Danh sách sự kiện ngày được chọn theo giờ bắt đầu, tự động phát hiện và gắn nhãn "Trùng giờ".
   - Form thêm/sửa sự kiện: mốc nửa giờ tiếp theo nếu là hôm nay, sự kiện cả ngày, kiểm tra kết thúc > bắt đầu, mô tả tối đa 500 ký tự, liên kết thói quen.
   - Tự động tạo, cập nhật hoặc hủy lời nhắc đi kèm trong `ReminderScheduler`.
   - Hộp thoại xác nhận xóa: "Xóa sự kiện và lời nhắc liên quan?".
3. **UC11 — Quản lý nhắc việc**:
   - Thẻ Nhắc việc trong màn hình Lịch, công tắc Bật/Tắt trực tiếp trên từng dòng.
   - Chế độ lặp: Một lần, Hằng ngày, Chọn các thứ trong tuần (T2..CN).
   - Kiểm tra và xin quyền `POST_NOTIFICATIONS` trên Android 13+, kiểm tra quyền exact alarm trên Android 12+.
   - `ReminderScheduler` lên lịch qua `AlarmManager` (hỗ trợ `RTC_WAKEUP` và `allowWhileIdle`).
   - `ReminderReceiver` tạo `NotificationChannel`, phát thông báo chuông/rung và tự động tính lượt lặp kế tiếp.
   - `BootReceiver` tự động khôi phục và lên lịch lại tất cả lời nhắc đang bật khi khởi động lại máy.
   - Cơ chế kiểm tra chống cấu hình trùng lặp.
4. **UC12 — Báo cáo cá nhân & Xuất/Chia sẻ PDF**:
   - 3 kỳ báo cáo: 7 ngày gần nhất (mặc định), 30 ngày, Tùy chọn khoảng ngày (tối đa 366 ngày).
   - Bộ lọc theo từng thói quen hoặc tất cả thói quen.
   - 3 chỉ số bắt buộc: Lượt ghi nhận, Ngày có hoạt động, Tỷ lệ hoàn thành (kỳ đã kết thúc, không gồm kỳ đang diễn ra).
   - Biểu đồ cột hoạt động theo ngày `DailyChartView`.
   - Bảng chi tiết từng thói quen với số lượt, số ngày và tỷ lệ %.
   - Xuất file PDF chuẩn A4 bằng Android `PdfDocument` với tên file `Bao_cao_YYYYMMDD_YYYYMMDD.pdf`.
   - Hỗ trợ Lưu PDF qua `CreateDocument` và Chia sẻ PDF qua `FileProvider` (`Intent.ACTION_SEND`).

