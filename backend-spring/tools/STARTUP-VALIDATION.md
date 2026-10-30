> Hướng dẫn hiện hành về đăng nhập và đồng bộ: [README Android](../../android-app/README.md). Bản ghi kiểm tra bên dưới là lịch sử, không phải kết quả xác minh mã nguồn hiện tại.

# Kết quả chẩn đoán và kiểm thử ngày 22/10/2026

## Đã kiểm tra

- Cấu trúc bốn module, settings.gradle, build.gradle, .gitignore, README.
- Cả 8 application.properties/application-migrate.properties.
- Bốn Application và MigrationRunner; tìm controller và các annotation route trên toàn bộ Java source.
- Set-DatabaseEnvironment.ps1, Test-MySql.ps1; TaskListAssignmentService.
- Bốn executable JAR, Java, Gradle, cổng TCP, tiến trình Java và log của lần chạy thử.
- Kết quả XML của bốn bộ DatabaseIntegrationTest.

## Phát hiện và xử lý

| Phát hiện | Nguyên nhân / xử lý |
|---|---|
| migrate chạy xong tự đóng | MigrationRunner @Profile("migrate") gọi context.close(); đây là hành vi dự kiến. Launcher mới ép profile default và servlet. Không sửa migration runner. |
| Chưa biết chắc nguyên nhân Auth dừng lần trước | Không có log lần chạy trước. Nếu đã có Tomcat 8081 thì cần log shutdown/exception để phân biệt Ctrl+C, đóng terminal, crash hoặc bị dừng từ bên ngoài. Không kết luận migrate là nguyên nhân đã xác nhận. |
| Phiên Codex không có biến xác thực của bốn service | Biến Process chỉ truyền từ terminal cha sang tiến trình con, không chia sẻ giữa các terminal. Không đoán password. Start yêu cầu nhập ẩn nếu thiếu, -NoPrompt báo lỗi. |
| Script cũ luôn hỏi password | Helper mới tái sử dụng biến đã có; -ReplacePassword cho phép nhập lại chủ động. |
| Gradle khai báo Java 17 | Đã đổi source/target Java 21; máy thực tế Temurin 21.0.8, Gradle 8.13, Spring Boot 3.5.7. |
| Chưa có REST controller hoặc Actuator | Không giả định /login, /tasks hay /actuator/health. GET / được kiểm tra HTTP 404, không gọi đó là API nghiệp vụ thành công. |

Trong quá trình phát triển script đã sửa hai lỗi được phát hiện bằng chạy thật:
PowerShell 5.1 xem stderr của java -version là lỗi khi ErrorActionPreference=Stop
(đổi sang java --version), và BOM UTF-8 gây Gradle parse lỗi (ghi build.gradle không BOM).

## Kết quả thực nghiệm

Test-MySql.ps1 -LifecycleChecks hoàn tất với exit code 0.
MySQL tạm riêng cổng 13316, database thử mới; không dùng MySQL thật 3306.

- 10 integration tests qua hai lượt: auth 2, task 3, productivity 3, ai 2; failures/errors/skipped đều 0.
- Build bốn executable JAR thành công với Java 21.
- Bốn log xác nhận profile default, Flyway validate thành công, schema up to date, JPA và Tomcat khởi động.
- Start xác nhận sở hữu cổng và tồn tại ít nhất 5 giây sau khi sẵn sàng.
- Chạy Start lần hai giữ nguyên bốn PID: auth 13656, task 25068, productivity 24496, ai 4900.
- GET http://127.0.0.1:8081/ đến :8084/ đều trả 404 theo source hiện tại.
- Kiểm tra lại sau 10 giây: cả bốn vẫn RUNNING.
- Stop dừng đúng bốn tiến trình thử; cả bốn cổng được giải phóng.
- Từ chối cổng 8081 khi có TCP listener thử; không tạo Java mới.
- Thiếu credentials: báo riêng bốn service, không kết nối bằng mật khẩu tự đoán.
- Helper tái sử dụng secret đã có mà không hỏi lại; thử nghiệm không xác thực secret giả với MySQL.
- Hàm nhận diện từ chối tiến trình PowerShell ngoài dự án.
- MySQL tạm đã shutdown. MySQL thật vẫn lắng nghe 3306, PID 6420.

| Service | Cổng | Trong thử nghiệm | Sau kiểm thử |
|---|---|---|---|
| auth | 8081 | RUNNING | STOPPED |
| task | 8082 | RUNNING | STOPPED |
| productivity | 8083 | RUNNING | STOPPED |
| ai | 8084 | RUNNING | STOPPED |

Chưa chạy bốn service với database thật: phiên Codex không có credentials.
Chưa xác nhận kết nối, quyền hoặc nội dung schema MySQL thật.
Không thay đổi database/bảng/dữ liệu thật và không sửa SQL migration.
Không có API nghiệp vụ nào được tuyên bố kiểm thử thành công.

## File tạo/sửa

Tạo: tools/Start-AllServices.ps1, Stop-AllServices.ps1, Check-Services.ps1,
ServiceTools.ps1, Test-ServiceLifecycle.ps1, RUNNING.md, STARTUP-VALIDATION.md.
Sửa: tools/Set-DatabaseEnvironment.ps1, tools/Test-MySql.ps1, build.gradle, README.md, FILES.md.
Không sửa application.properties, Entity, Repository, SQL migration hoặc Android.


## Kiểm chứng đăng nhập và tự lưu (23/10/2026)

- Android assembleDebug/lintDebug/testDebugUnitTest: thành công; 4 bài API cũ đạt.
- MySQL biệt lập: 26 bài backend đạt, hai lần chạy migration/validate đều thành công.
- HTTP Auth/Task/Productivity trên cổng tạm: đăng ký lấy token, gửi native snapshot, từ chối không token (401), cách ly hai tài khoản (404), CRUD và giữ dữ liệu sau restart đều đạt.
- Đã cài đè APK bằng adb install -r; điện thoại mở LoginActivity. Không xóa dữ liệu app.
- Backend thật 8081/8082/8083 chưa chạy; biến môi trường xác thực MySQL không có trong phiên công cụ. Chưa kiểm chứng đăng nhập từ điện thoại tới database thật.
