> Cập nhật luồng đăng nhập: [Hướng dẫn hiện hành](../../android-app/LOGIN_AUTOSAVE.md). Không còn bước nhập máy chủ trong Android. Auth V2 thêm phiên đăng nhập; mọi API dữ liệu cần bearer token thật. Script Update-DevelopmentBackend cập nhật cả Auth/Task/Productivity. Các hướng dẫn tài khoản cố định hoặc cấu hình URL trong app bên dưới là lịch sử.

> Hướng dẫn hiện hành: [Lưu từ giao diện Android gốc](../../android-app/NATIVE_WORKSPACE_SYNC.md). Đã gỡ màn hình MySQL riêng. Cần Task V3 / Productivity V4 và profile dev. Hướng dẫn màn hình riêng bên dưới là lịch sử, không còn áp dụng.

> Sửa trường hợp app chưa lưu MySQL: chạy .\tools\Update-DevelopmentBackend.ps1 từ backend-spring.
> Script tái sử dụng biến môi trường hoặc hỏi mật khẩu ẩn, build Task/Productivity, dừng đúng hai service được quản lý,
> chạy migration mới, khởi động profile dev và kiểm tra GET HTTP 200. Không xóa dữ liệu hoặc thay đổi Auth/AI.
> Android: cấu hình kết nối một lần, sau đó thao tác ngay trên giao diện gốc.
> Thay đổi công việc và thói quen tự gửi MySQL; mất mạng sẽ giữ cục bộ và thử lại.
> Start-AllServices hiện chạy từ bản sao JAR theo SHA256 để build mới không thay JAR của tiến trình đang chạy.
> Cập nhật 23/10/2026: Task/Habit REST API và màn hình Android dùng MySQL đã được bổ sung. Xem [hướng dẫn triển khai mới](../../android-app/TASK_HABIT_IMPLEMENTATION.md). API cần profile dev và migration Task V2 / Productivity V3. Các ghi chú bên dưới về chưa có controller mô tả giai đoạn cơ sở dữ liệu trước đó.

# Chạy backend trên Windows

Mở PowerShell tại thư mục backend-spring. Yêu cầu Java 21 trên PATH và MySQL đã chạy migration.
Các script dùng Windows PowerShell 5.1 hoặc PowerShell 7 trên Windows.

## Khởi động, kiểm tra, dừng

    .\tools\Start-AllServices.ps1
    .\tools\Check-Services.ps1 | Format-Table -AutoSize
    .\tools\Stop-AllServices.ps1

To apply pending Flyway migrations and validate the selected service schemas before startup,
explicitly add `-ApplyMigrations`. This runs only for services named by `-Services`; it refuses
to migrate a service that is already running. For example, to update only Productivity:

    .\tools\Start-AllServices.ps1 -Services productivity -DevelopmentApi -ApplyMigrations

Start tự yêu cầu username và nhập mật khẩu ẩn khi thiếu biến môi trường.
Nếu đã cấu hình đủ trong cùng terminal, script không hỏi lại. Không gửi mật khẩu qua chat.
Biến môi trường đặt trong terminal khác không tự truyền sang terminal hiện tại hay Codex.
Nếu tài khoản đã có nhưng sai mật khẩu, kiểm tra log rồi nhập lại bằng:

    .\tools\Set-DatabaseEnvironment.ps1 -Service auth -ReplacePassword

Nếu host/port MySQL khác mặc định:

    .\tools\Set-DatabaseEnvironment.ps1 -Service auth -Username taskmind_auth -HostName localhost -Port 3306

Làm tương tự với task, productivity, ai bằng tài khoản được cấp quyền cho database tương ứng.
Không tự cấp quyền, đổi mật khẩu MySQL hoặc bật allowPublicKeyRetrieval để chữa lỗi xác thực.
Giữ cấu hình SSL phù hợp môi trường của bạn.

Dùng -NoPrompt cho môi trường tự động: thiếu thông tin sẽ báo lỗi, không chờ nhập.
Start báo lỗi tổng hợp nếu một service thất bại; các service khác vẫn được thử khởi động.
Có thể tăng thời gian chờ bằng -TimeoutSeconds 180.

| Service | Biến cổng | Mặc định |
|---|---|---|
| auth | AUTH_PORT | 8081 |
| task | TASK_PORT | 8082 |
| productivity | PRODUCTIVITY_PORT | 8083 |
| ai | AI_PORT | 8084 |

Start khởi chạy Java độc lập trong nền rồi theo dõi tất cả service. Cửa sổ bị ẩn.
Các đối số ép profile default và web servlet, không chạy MigrationRunner của profile migrate.
JAR được chọn từ từng build/libs, bỏ qua *-plain.jar; nhiều executable JAR thì yêu cầu xử lý thủ công.
Cổng đã chiếm hoặc cùng JAR đã chạy thủ công thì báo lỗi và không khởi động trùng.
Stop chỉ dừng PID được ghi nhận có cùng thời điểm tạo, đường dẫn JAR và mã nhận diện riêng.
Stop dùng kết thúc tiến trình trên Windows, không phải graceful shutdown; chỉ dùng khi đã ngừng gửi request.
Script không tự khởi động lại sau reboot/crash và không cài Windows Service.

## Log và trạng thái

Log stdout/stderr riêng từng lần chạy nằm trong build/services; JSON chỉ chứa PID, thời điểm tạo,
JAR, cổng, đường dẫn log và token nhận diện. Thư mục build được Git bỏ qua.
Thông tin xác thực chỉ nằm trong môi trường tiến trình, không đặt trong đối số Java hoặc JSON.
Không bật log debug chứa thông tin nhạy cảm hoặc lưu toàn bộ biến môi trường ra file.

RUNNING nghĩa là tiến trình sở hữu cổng và đã xuất hiện dòng Started ...Application.
Start còn kiểm tra service tiếp tục sống ít nhất 5 giây.
Đây là kiểm tra khởi động, không phải kiểm tra database liên tục hay API health.
STARTING_OR_UNHEALTHY cần xem log; PORT_OCCUPIED_UNMANAGED không bị script dừng.
Timeout giữ tiến trình lại để chẩn đoán; dùng Stop nếu muốn dừng.

    Get-Content .\build\services\auth-<token>.stdout.log -Tail 60
    Test-NetConnection 127.0.0.1 -Port 8081

## Database và API

Start giữ ddl-auto=validate, SQL init=never, Flyway clean bị vô hiệu hóa và baseline tự động bị tắt.
Flyway chạy với `target=latest` và áp dụng các migration versioned còn thiếu khi service khởi động.
Không đặt `target=current`, vì tùy chọn đó ngăn migration versioned mới chạy dù Flyway vẫn báo đã validate migration.
`-ApplyMigrations` là preflight tùy chọn để chạy migration và Hibernate validation trước khi mở
web service; thông thường không cần vì startup cũng chạy Flyway rồi validate schema.
Nếu sau này thêm repeatable migration/callback, chúng cũng được Flyway xử lý theo mặc định.
Không sửa SQL migration đã áp dụng để chữa lỗi checksum.

Source hiện chưa có REST controller nghiệp vụ hay Actuator.
GET / trả 404 là phản hồi HTTP hợp lệ cho đường dẫn chưa được định nghĩa,
không phải bằng chứng API đăng nhập, CRUD hoặc health đã hoạt động.
Khởi động thành công với Flyway/JPA validate xác nhận kết nối và schema tại thời điểm khởi động.
Chưa có kiểm tra nghiệp vụ HTTP trên dữ liệu thật.

## Build và kiểm thử biệt lập

    ..\android-app\gradlew.bat -p . bootJar
    .\tools\Test-MySql.ps1 -LifecycleChecks

Lệnh kiểm thử tạo MySQL tạm cổng 13316, database thử mới và mật khẩu ngẫu nhiên trong RAM;
chạy integration tests hai lượt, build JAR, chạy bốn service trên các cổng cấu hình,
kiểm tra chống chạy trùng, GET / = 404, còn sống sau 10 giây và dừng đúng các service thử.
Cần bốn cổng service trống. Kiểm thử dừng MySQL tạm khi xong, không dùng dữ liệu MySQL 3306.
Không dùng lệnh này như lệnh khởi động backend để sử dụng app.

Tài liệu:
- https://docs.spring.io/spring-boot/3.5/reference/features/profiles.html
- https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-target-setting
