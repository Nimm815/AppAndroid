# AppAndroid — Quản lý công việc cá nhân

Ứng dụng Android **Java + XML**, thuộc đề tài “Xây dựng hệ thống quản lý công việc cá nhân thông minh sử dụng ReactJS, Spring Boot, FastAPI và kiến trúc Microservices”. Giao diện tham khảo HelloHabit, nội dung bằng tiếng Việt.

Bản Android vẫn chạy cục bộ trước; có thể bật đăng nhập và đồng bộ dữ liệu qua Auth, Task và Productivity Service khi backend/MySQL đang chạy. **Không cần MySQL hay backend để dùng các chức năng cục bộ.** Gợi ý AI chưa được tích hợp vào app.

## 1. Chuẩn bị

| Công cụ | Yêu cầu / mục đích |
| --- | --- |
| Git | Clone và cập nhật mã nguồn |
| Android Studio | Mở dự án, tải SDK, build và chạy app |
| JDK 17 | Dùng làm Gradle JDK; code của dự án dùng Java 17 |
| Android SDK Platform 35 | Theo `compileSdk 35` của dự án |
| SDK Build-Tools và Platform-Tools | Build app và kết nối thiết bị |
| Điện thoại hoặc máy ảo | Android 6.0 (API 23) trở lên |
| Internet | Tải SDK, Gradle và thư viện lần đầu |

VS Code là công cụ tùy chọn để sửa code. Android Studio và VS Code có thể mở cùng thư mục `android-app`, dùng chung các file.

## 2. Clone repository

Mở Terminal hoặc PowerShell tại thư mục muốn lưu dự án:

```powershell
git clone https://github.com/Nimm815/AppAndroid.git
cd AppAndroid
```

Nếu repository riêng tư, tài khoản GitHub của bạn cần được cấp quyền truy cập.

## 3. Mở trong Android Studio

1. Chọn **Open**, mở thư mục **`AppAndroid/android-app`** vừa clone. Không chọn thư mục `app` bên trong và không tạo dự án mới.
2. Vào **Tools > SDK Manager**. Trong **SDK Platforms**, cài **Android SDK Platform 35**. Trong **SDK Tools**, cài **Android SDK Build-Tools**, **Android SDK Platform-Tools** và **Android Emulator** nếu dùng máy ảo. Nếu Gradle yêu cầu một phiên bản Build-Tools cụ thể, cài đúng gói được báo thiếu. Chấp nhận giấy phép SDK khi được yêu cầu.
3. Vào **Settings > Build, Execution, Deployment > Build Tools > Gradle**, chọn **Gradle JDK 17**, dùng **Gradle Wrapper** của dự án. Trên macOS, Settings nằm trong menu Android Studio.
4. Chọn **Sync Project with Gradle Files**, chờ tải xong thư viện. Dự án khai báo Android Gradle Plugin **8.13.2** và Gradle Wrapper **8.13**; không cần cài Gradle riêng.

Android Studio thường tạo `android-app/local.properties` với đường dẫn SDK riêng của máy bạn. File này được bỏ qua trong Git.

Nếu báo thiếu SDK, xem **Android SDK Location** trong SDK Manager rồi tạo `android-app/local.properties`. Ví dụ Windows, thay `TEN_NGUOI_DUNG` bằng tài khoản của bạn:

```properties
sdk.dir=C:/Users/TEN_NGUOI_DUNG/AppData/Local/Android/Sdk
```

Không dùng đường dẫn SDK của thành viên khác và không commit `local.properties`.

## 4. Chạy ứng dụng

### Dùng máy ảo

1. Vào **Tools > Device Manager > Create Virtual Device**.
2. Chọn mẫu điện thoại, tải system image API 34 hoặc 35, hoàn tất và khởi động máy ảo.
3. Chọn cấu hình chạy **app**, chọn máy ảo trên thanh công cụ, nhấn **Run ▶**.

### Dùng điện thoại thật

1. Bật **Tùy chọn nhà phát triển** và **Gỡ lỗi USB (USB debugging)** trên điện thoại Android 6.0 trở lên.
2. Kết nối bằng cáp USB có truyền dữ liệu, chấp nhận yêu cầu cho phép gỡ lỗi trên điện thoại.
3. Chọn cấu hình **app**, chọn điện thoại trong Android Studio, nhấn **Run ▶**.

App không yêu cầu đăng nhập. Thử thêm công việc, đánh dấu hoàn thành, đóng rồi mở lại app để kiểm tra dữ liệu được giữ.

## 5. Build APK bằng Terminal

Sau khi cấu hình SDK và JDK, chạy từ thư mục gốc repository.

**Windows / PowerShell:**

```powershell
cd android-app
java -version
.\gradlew.bat --version
.\gradlew.bat assembleDebug
```

**macOS / Linux:**

```bash
cd android-app
chmod +x gradlew
java -version
./gradlew --version
./gradlew assembleDebug
```

Nếu Terminal không nhận Java, đặt `JAVA_HOME` trỏ tới thư mục JDK 17, thêm thư mục `bin` của JDK vào `PATH`, rồi mở lại Terminal. Gradle JDK trong Android Studio và Java trong Terminal được cấu hình riêng.

APK debug được tạo tại:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

Kiểm tra bổ sung, chạy trong `android-app` trên Windows:

```powershell
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

`lintDebug` kiểm tra mã nguồn và tài nguyên Android. `connectedDebugAndroidTest` cần máy ảo đang chạy hoặc điện thoại đã kết nối. Trên macOS/Linux, thay `.\gradlew.bat` bằng `./gradlew`.

## 6. Cấu trúc dự án

```text
AppAndroid/
├── README.md                 # Hướng dẫn cài đặt cho thành viên
├── AGENTS.md                 # Bối cảnh và nguyên tắc phát triển
└── android-app/              # Mở thư mục này trong Android Studio
    ├── README.md             # Chi tiết tính năng Android
    ├── settings.gradle       # Module và kho thư viện
    ├── build.gradle          # Phiên bản Android Gradle Plugin
    ├── gradlew / gradlew.bat  # Chạy Gradle Wrapper
    ├── gradle/wrapper/        # File và cấu hình Wrapper
    └── app/
        ├── build.gradle      # SDK, Java và thư viện ứng dụng
        ├── schemas/          # Schema Room phục vụ migration
        └── src/
            ├── main/
            │   ├── AndroidManifest.xml
            │   ├── java/vn/edu/taskmanager/
            │   └── res/      # Layout XML, chữ, màu và biểu tượng
            └── androidTest/  # Kiểm thử trên thiết bị Android
```

Các file Java nằm trong `android-app/app/src/main/java/vn/edu/taskmanager/`:

| File | Vai trò |
| --- | --- |
| `MainActivity.java` | Màn hình chính và thao tác công việc |
| `TaskAdapter.java` | Hiển thị danh sách công việc |
| `TaskEditorDialog.java` | Form thêm/sửa công việc |
| `TaskViewModel.java` | Theo dõi dữ liệu và xử lý ghi trên luồng nền |
| `data/Task.java`, `TaskDao.java`, `TaskDatabase.java` | Mô hình công việc, thao tác và database Room |
| `WorkspaceController.java` | Điều phối thói quen, nhật ký, lịch và cài đặt |
| `data/LocalStore.java` | Lưu dữ liệu cục bộ bổ sung |

Giao diện nằm trong `app/src/main/res/layout/`. Mở XML trong Android Studio, chọn **Split/Design** để xem. Xem thêm [tài liệu Android](android-app/README.md) và [nguyên tắc phát triển](AGENTS.md).

## 7. Dữ liệu và các dịch vụ

App lưu công việc bằng Room trong `personal_tasks.db`; nhật ký, thói quen, sự kiện, lời nhắc và lịch sử ngày được lưu cục bộ bằng SharedPreferences. Khi cấu hình backend, worker đồng bộ công việc/danh sách lên Task Service và dữ liệu năng suất lên Productivity Service; Auth Service xác thực tài khoản. Mất mạng thì dữ liệu cục bộ vẫn được giữ và worker thử lại khi có mạng.

**Không gỡ app hoặc xóa dữ liệu để cập nhật giao diện.** Chạy lại bằng Android Studio để cập nhật khi chữ ký tương thích. Room có migration qua schema 6 để giữ công việc và danh sách cũ. Nếu thay cấu trúc database, phải bổ sung migration và giữ schema trong Git.

Các service hiện có trong repository:

| Thành phần dự kiến | Vai trò |
| --- | --- |
| `backend-spring/auth-service` | Tài khoản, phiên đăng nhập và hồ sơ; MySQL `taskmind_auth_db` |
| `backend-spring/task-service` | Công việc, danh sách và API; MySQL `taskmind_task_db` |
| `backend-spring/productivity-service` | Thói quen, nhật ký, lịch, lời nhắc và lịch sử; MySQL `taskmind_productivity_db` |
| `backend-spring/ai-service` | Microservice Python + FastAPI; gọi Gemini và lưu hội thoại trong MySQL `taskmind_ai_db` |
| `admin-web` | Giao diện quản trị ReactJS; gọi API Auth, Task và Productivity theo từng service |

Các service chạy riêng và kết nối qua API. Script `backend-spring/tools/Start-TaskMindDev.ps1` chạy Auth, Task và Productivity trên các cổng 8081–8083. AI Service là ứng dụng FastAPI riêng, chạy cổng 8084 theo hướng dẫn trong `backend-spring/ai-service/README.md`. Trên Android, nút **AI** ở thanh trên Trang chủ mở cửa sổ chat và gọi AI Service; người dùng cần đăng nhập để AI đọc đúng dữ liệu tài khoản. `task_management_app` và backend đơn khối trong `android-app/backend` / `backend-spring/src/main` còn được giữ để đối chiếu, không được app hiện tại sử dụng. Admin Web gọi API mới; dữ liệu ở database cũ chưa được di chuyển hoặc xóa. Đừng xóa database hoặc chạy dump có `DROP TABLE` trước khi hoàn tất đối chiếu dữ liệu.

Để dùng Admin Web với các database microservices, build các service khi source thay đổi rồi chạy `backend-spring/tools/Start-TaskMindDev.ps1`; script này bật API phát triển trên Auth, Task và Productivity. Sau đó chạy `npm run dev` trong `admin-web`. Admin lấy hồ sơ từ Auth, số công việc từ Task và thống kê lịch sử từ Productivity. Trang quản trị AI và đăng nhập/quyền Admin production chưa được triển khai.

## 8. Lỗi thường gặp

| Lỗi / hiện tượng | Cách xử lý |
| --- | --- |
| `SDK location not found` | Kiểm tra SDK Manager và cấu hình `local.properties` theo mục 3 |
| Thiếu `android-35` | Cài Android SDK Platform 35 rồi Sync lại |
| Thiếu Build-Tools / giấy phép SDK | Cài gói được báo thiếu và chấp nhận giấy phép trong SDK Manager |
| `JAVA_HOME is not set` / Java không phù hợp | Chọn Gradle JDK 17; cấu hình `JAVA_HOME` riêng nếu dùng Terminal |
| Không tải được Gradle hoặc thư viện | Kiểm tra mạng, proxy/firewall; tắt Gradle Offline Mode nếu đang bật rồi Sync lại |
| Android Studio không hỗ trợ plugin | Dùng bản Android Studio hỗ trợ AGP 8.13; không tự đổi phiên bản plugin/Gradle để bỏ qua lỗi |
| Không thấy điện thoại | Kiểm tra cáp, USB debugging, quyền gỡ lỗi và driver USB của hãng trên Windows |
| Máy ảo không chạy | Kiểm tra ảo hóa phần cứng và cấu hình Emulator; có thể dùng điện thoại thật |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Chữ ký bản cài khác với bản mới. Cần dùng lại keystore/chữ ký cũ để giữ dữ liệu; trao đổi với nhóm trước khi gỡ app |

Nếu vẫn lỗi, gửi nội dung lỗi trong cửa sổ **Build/Sync**, phiên bản Java và bước đang thực hiện cho nhóm.

## 9. Làm việc cùng nhóm

Trước khi lấy code mới, kiểm tra thay đổi bằng `git status`. Commit công việc hoặc cất tạm bằng `git stash` nếu cần, rồi chạy tại thư mục repository:

```powershell
git status
git pull --ff-only
git switch -c feature/ten-chuc-nang
```

Nếu pull báo nhánh đã phân kỳ, trao đổi với nhóm để xử lý merge/rebase. Sau khi pull, Sync lại Android Studio nếu cấu hình Gradle thay đổi.

`.gitignore` đã bỏ qua `local.properties`, `.gradle/`, `build/`, `.idea/` và `*.iml`. Chỉ commit mã nguồn, tài nguyên và schema cần thiết; không đưa mật khẩu, token hoặc keystore cá nhân lên Git.

Trước khi gửi thay đổi, build app và thử chức năng vừa sửa. Tiếp tục dùng Java + XML, tiếng Việt và giữ dữ liệu Room hiện có.
