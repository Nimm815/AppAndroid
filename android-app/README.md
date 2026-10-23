# App quản lý công việc cá nhân

## Quản lý danh sách công việc (Room, schema 6)

- Menu ☰ → Quản lý danh sách / bộ lọc → Tạo danh sách: nhập tên, chọn công việc đã có hoặc để danh sách trống.
- Chạm danh sách để mở. Nút + trong danh sách tạo công việc mới và tự đưa vào danh sách đó.
- Chạm Sửa danh sách hoặc nút ⋯ của danh sách để đổi tên, chọn/bỏ chọn thành viên hoặc xóa danh sách.
- Xóa danh sách/bỏ chọn thành viên không xóa công việc. Xóa công việc có xác nhận sẽ xóa việc khỏi mọi danh sách.
- Số việc hoàn thành cập nhật tự động; mỗi công việc chỉ thuộc một danh sách riêng. Chọn công việc ở danh sách khác rồi lưu sẽ chuyển công việc sang danh sách đang sửa. Công việc vẫn hiện trong các chế độ tổng hợp như Tất cả công việc/Hôm nay.
- Migration 5→6 thêm ràng buộc duy nhất theo công việc. Nếu dữ liệu bản cũ có liên kết trùng, giữ danh sách tạo mới nhất (cùng thời điểm thì chọn ID giảm dần); giữ nguyên công việc và tất cả danh sách. Không suy đoán lần chuyển gần nhất vì bản cũ chưa lưu thông tin này.
- Room lưu `task_lists` và `task_list_members` cùng database `personal_tasks.db`. Migration 4→5 giữ nguyên bảng công việc.
- Lần mở sau cập nhật, `LegacyTaskListImporter` nhập danh sách JSON cũ một lần trong transaction, giữ ID và nguồn JSON cũ. Dấu nhập xong nằm trong Room nên danh sách đã xóa không bị nhập lại.
- Bộ lọc, thói quen, lịch, nhật ký và cài đặt vẫn dùng cơ chế hiện có. Các mô tả SharedPreferences cho danh sách ở phần lịch sử bên dưới đã được thay thế bởi phần này.
- Cập nhật đè ứng dụng, không gỡ app/xóa dữ liệu. Kiểm tra trên điện thoại: tạo việc trong A, mở Sửa danh sách B và chọn việc đó rồi lưu; việc biến mất khỏi A và xuất hiện ở B, giữ tên/tiến độ/ghi chú. Kiểm tra số hoàn thành ở cả hai danh sách và đóng/mở lại app.

Kiểm tra build: `./gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug`.
`TaskListDatabaseTest` dùng database/SharedPreferences riêng để kiểm tra migration, liên kết, số hoàn thành, lưu lại và nhập dữ liệu cũ.

Phiên bản đầu tiên dùng Java + XML, RecyclerView và Room. Có thêm, sửa tên,
xóa (có xác nhận), đánh dấu hoàn thành và bỏ đánh dấu. Tên được bỏ khoảng trắng
ở hai đầu và không được trống. Công việc chưa hoàn thành hiển thị trước.
Dữ liệu nằm trong `personal_tasks.db` trên điện thoại, không cần mạng.
Đóng app hoặc khởi động lại điện thoại không xóa dữ liệu; gỡ app hoặc xóa dữ
liệu ứng dụng sẽ xóa công việc. Ứng dụng chưa có màn hình đăng nhập; riêng hồ sơ cá nhân có thể lưu cục bộ hoặc đồng bộ qua Auth Service khi có bearer token hợp lệ.

## Mở và chạy bằng Android Studio

1. Cài Android Studio nếu chưa có, hoàn thành Setup Wizard để cài Android SDK.
2. Chọn **Open**, mở thư mục `C:\PTUDAndroid\android-app` (không mở thư mục `app`).
3. Trong **Tools > SDK Manager**, cài **Android SDK Platform 35**; ở tab
   **SDK Tools**, cài **Android SDK Build-Tools 34.0.0**, **Android SDK Platform-Tools**
   và **Android Emulator** nếu dùng máy ảo. Android Studio tạo `local.properties`
   chứa đường dẫn SDK riêng của máy; không đưa file đó lên Git.
4. Trong **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**,
   dùng Gradle từ **Wrapper**, chọn **Gradle JDK 17** hoặc JDK 21 đi kèm Android
   Studio. Code Java của app được cấu hình ở mức Java 17.
5. Chọn **Sync Project with Gradle Files** và chờ tải thư viện. Lần đầu cần Internet.
6. Mở **Tools > Device Manager > Create Virtual Device**, chọn điện thoại và tải
   system image API 34 hoặc 35. Hoặc bật Developer options và USB debugging trên
   điện thoại Android 6.0 trở lên, cắm USB và chấp nhận quyền gỡ lỗi.
7. Chọn cấu hình **app**, chọn thiết bị, nhấn **Run ▶**.

Không cần tạo lại dự án. VS Code và Android Studio cùng sửa các file ở thư mục này.
Để xem XML, mở `app/src/main/res/layout/activity_main.xml` và chọn **Split/Design**.

## Vai trò các file

- `MainActivity.java`: nối màn hình với danh sách và các nút thao tác.
- `TaskAdapter.java`: hiển thị từng công việc trong RecyclerView.
- `TaskEditorDialog.java`: nhập tên, kiểm tra tên và gửi lệnh lưu; giữ bản nháp khi xoay máy.
- `DeleteTaskDialog.java`: xác nhận trước khi xóa.
- `TaskViewModel.java`: theo dõi dữ liệu và chạy lệnh ghi trên luồng nền.
- `data/Task.java`: một công việc gồm mã, tên và trạng thái hoàn thành.
- `data/TaskDao.java`: các câu lệnh đọc, thêm, sửa và xóa trong Room.
- `data/TaskDatabase.java`: tạo và mở cơ sở dữ liệu trên thiết bị.
- `res/layout/`: giao diện XML; `res/values/strings.xml`: chữ tiếng Việt.
- `app/build.gradle`: cấu hình Android và thư viện. Room dùng Java annotationProcessor.

## Kiểm tra

Trong Terminal tại `android-app`, sau khi cài SDK và cấu hình Java:

```powershell
.\gradlew.bat assembleDebug lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

Lệnh thứ hai cần máy ảo đang chạy hoặc điện thoại đã kết nối. Bài kiểm tra
`TaskDatabaseTest` kiểm tra dữ liệu sau khi đóng/mở lại database, sửa tên, đổi
trạng thái và xóa. Schema Room sẽ được xuất vào `app/schemas` sau khi build;
cần giữ schema trong Git để hỗ trợ nâng cấp database về sau.

Kiểm tra thủ công trên app:

1. Thêm tên rỗng hoặc chỉ dấu cách: hộp thoại phải báo lỗi và vẫn mở.
2. Thêm hai công việc, sửa tên một công việc.
3. Đánh dấu hoàn thành rồi bỏ đánh dấu; trạng thái và cách hiển thị phải đổi.
4. Xóa: thử Hủy trước, sau đó xác nhận Xóa.
5. Đóng app, mở lại; công việc còn lại phải giữ tên và trạng thái.
6. Xoay máy khi đang nhập tên; bản nháp phải còn.

## Thông tin cá nhân

Trong tab **Cài đặt**, chọn **Thông tin cá nhân → Chỉnh sửa**. Có thể nhập họ tên/email, số điện thoại Việt Nam, ngày sinh và giới tính; chọn ảnh qua bộ chọn ảnh hệ thống, không cần cấp quyền đọc toàn bộ bộ nhớ.

Khi chưa có phiên đăng nhập, nút **Lưu thay đổi** lưu hồ sơ và ảnh trong vùng dữ liệu riêng của ứng dụng trên điện thoại. Màn hình ghi rõ trạng thái chưa đồng bộ MySQL; không tạo tài khoản hoặc user ID giả. Android hiện chưa có luồng đăng nhập/đăng ký, vì vậy muốn đọc/ghi hồ sơ MySQL cần tích hợp bearer token hợp lệ trước.

Khi có token, Android gọi Auth Service tại `AUTH_URL` (mặc định debug `http://127.0.0.1:8081/`): `GET/PUT /api/users/me` và `POST/GET /api/users/me/avatar`. Để thử trên USB, chạy `backend-spring\tools\Start-TaskMindDev.ps1` trước; script sẽ thử chuyển tiếp cổng Auth `8081` cùng Task `8082` và Productivity `8083`. Nếu ADB báo cổng 8081 trên điện thoại đang được ứng dụng khác sử dụng, Auth reverse không hoạt động cho đến khi giải phóng cổng đó; các hồ sơ cục bộ vẫn dùng được.

`PersonalProfileActivity` là giao diện, `PersonalProfileViewModel` giữ trạng thái khi xoay màn hình và gọi Retrofit; `ProfileValidator` kiểm tra dữ liệu trước khi gửi. Nếu API chưa xác nhận thành công, app không hiển thị trạng thái đã lưu máy chủ.

## Các màn hình tham khảo HelloHabit

App dùng Java + XML, nội dung tiếng Việt, nền trắng/xám, điểm nhấn tím nhạt,
bốn tab Công việc, Nhật ký, Lịch, Cài đặt và nút thêm hình tròn.

| Màn hình | Thao tác hiện có |
| --- | --- |
| Hôm nay / Công việc | Thêm, sửa, xóa, hoàn thành, tìm kiếm, lọc trạng thái, thống kê |
| Menu danh sách / bộ lọc | Chọn công việc hoặc thói quen; tạo/sửa/xóa danh sách; chọn công việc trong danh sách; bộ lọc theo tên và trạng thái |
| Thói quen | Tạo/sửa/xóa thói quen hằng ngày; chọn ngày trong tuần và ghi nhận hoàn thành |
| Nhật ký (UC09) | Dòng thời gian, tìm kiếm không dấu (debounce 300ms), 3 bộ lọc AND (khoảng ngày, thói quen, đánh dấu), đánh dấu 1 chạm, phân trang 30 mục, xác nhận xóa |
| Lịch (UC10) | Lưới lịch tháng 42 ô có chấm báo sự kiện; chọn ngày xem sự kiện; phát hiện trùng giờ; thêm/sửa/xóa sự kiện kèm lời nhắc tự động |
| Nhắc việc (UC11) | Danh sách lịch nhắc sắp tới; công tắc Bật/Tắt trực tiếp; lặp 1 lần / hằng ngày / thứ trong tuần; kiểm tra quyền thông báo POST_NOTIFICATIONS và exact alarm; khôi phục sau khởi động máy |
| Báo cáo (UC12) | 3 kỳ (7 ngày, 30 ngày, tùy chọn); 3 chỉ số bắt buộc (Lượt ghi nhận, Ngày hoạt động, Tỷ lệ hoàn thành); biểu đồ cột Canvas; xuất/chia sẻ file PDF chuẩn `Bao_cao_YYYYMMDD_YYYYMMDD.pdf` |
| Cài đặt | Giao diện sáng/tối/theo hệ thống, tìm kiếm chung, hướng dẫn, mở danh sách, hẹn giờ và báo cáo |
| Bộ hẹn giờ | Bấm giờ, ghi vòng, đếm ngược giờ/phút/giây, bắt đầu/tạm dừng/đặt lại; chọn thói quen làm nội dung tập trung |

Dải ngày trong tab Công việc chọn **ngày thực hiện** và ngày ghi nhận thói quen.
Chế độ xem theo ngày hiển thị cả công việc và thói quen trong hai nhóm riêng.
Thêm công việc hoặc thói quen không tự chuyển sang chế độ chỉ xem một loại.
Thói quen hằng ngày hiện từ ngày tạo trở đi, gồm cả ngày tương lai; mỗi ngày
có dấu hoàn thành riêng và chỉ được ghi nhận từ ngày tạo đến hôm nay.
Menu vẫn cho phép chọn riêng tất cả công việc hoặc tất cả thói quen.
Thêm công việc mặc định dùng ngày đang chọn; form cho phép đổi ngày hoặc để chưa lên lịch.
Form thêm/sửa công việc chỉ cho chọn ngày thực hiện từ hôm nay trở đi.
Nếu thêm từ màn hình ngày quá khứ, form mặc định về hôm nay. Công việc cũ
có ngày quá khứ vẫn được giữ; có thể sửa tên mà không đổi ngày cũ.
Chuyển ngày chỉ hiện công việc có ngày thực hiện tương ứng, gồm cả việc đã hoàn thành.
Màn hình Hôm nay có nhóm Quá hạn (ngày trước hôm nay, chưa hoàn thành) và Chưa lên lịch.
Ngày tương lai không hiển thị nhóm quá hạn; không tự xóa hoặc chuyển ngày công việc.
Menu ☰ > Tất cả công việc và các bộ lọc/danh sách xem dữ liệu của mọi ngày.
Chạm ngày trên dải ngày để trở về chế độ xem theo ngày; ☰ > Hôm nay trở về ngày hiện tại.
Lịch hiện hiển thị **sự kiện được tạo trong tab Lịch**, chưa tự lấy công việc.
Thói quen hiện có một mục tiêu hoàn thành mỗi ngày, chưa có mục tiêu số lần/số phút
hoặc lịch lặp tùy chọn. Báo cáo chỉ tính ngày từ ngày tạo đến hôm nay trong kỳ đã chọn.
Chọn thói quen trong bộ hẹn giờ không tự đánh dấu thói quen hoàn thành.
Bộ hẹn giờ giữ thời gian khi chuyển màn hình và xoay máy; chưa có thông báo nền khi
đóng app. Danh sách ba vòng gần nhất chỉ giữ trong phiên chạy, chưa xuất lịch sử tập trung.

Công việc vẫn nằm trong Room (`personal_tasks.db`). Ghi chú, thói quen, lịch sử ngày,
sự kiện, danh sách và bộ lọc được lưu bằng JSON trong SharedPreferences
(`personal_workspace`) cho bản giao diện cục bộ có ít dữ liệu. Khi mở rộng và nối
backend, cần chuyển phần dữ liệu này sang Room/API qua lớp quản lý dữ liệu.
Không cần gỡ app để cập nhật. Room nâng từ schema 1 lên 2 bằng `MIGRATION_1_2`:
thêm `scheduledDate` và `createdAt`, giữ nguyên mã, tên và trạng thái công việc cũ.
Công việc cũ để Chưa lên lịch; không đoán ngày tạo/ngày thực hiện của dữ liệu cũ.

### Vai trò các file mới

Công việc có bốn nhóm mức độ quan trọng theo ma trận quan trọng/khẩn cấp:
Làm ngay (xanh lá), Lên lịch (vàng), Ủy quyền (xanh ngọc), Loại bỏ (cam đỏ).
Chọn nhóm trong form thêm/sửa; vạch màu ở viền trái chỉ hiện khi có ngày thực hiện.
Tên nhóm cũng hiện cạnh ngày để không phải chỉ phân biệt bằng màu.
“Ủy quyền” và “Loại bỏ” chỉ là nhãn phân loại, không tự giao việc hoặc xóa.
Room schema 3 dùng MIGRATION_2_3 thêm priority, giữ mọi dữ liệu cũ;
công việc cũ mặc định nhóm Lên lịch và có thể đổi trong form Sửa.

Nhật ký hiển thị thẻ xem trước với ngày, nhãn, nút đánh dấu và sửa.
Chạm thẻ hoặc “Đọc nhật ký” để mở trang đọc toàn màn hình, chữ serif,
lề rộng và giãn dòng; giữ nguyên các dòng/đoạn đã nhập. Trang đọc có
đánh dấu, sửa và xóa có xác nhận. Form viết nhật ký có vùng nhập lớn hơn.
`item_journal.xml` định nghĩa thẻ; `dialog_journal_reader.xml` và
`JournalReaderDialog.java` phụ trách trang đọc. Dữ liệu cũ vẫn dùng LocalStore.

- `res/layout/activity_main.xml`: khung bốn tab và thanh điều hướng.
- `res/layout/screen_*.xml`: giao diện từng tab.
- `res/layout/dialog_*.xml`: form thêm/sửa, menu danh sách, hẹn giờ và báo cáo.
- `WorkspaceController.java`: điều hướng, thói quen, nhật ký, lịch, danh sách và cài đặt.
- `data/LocalStore.java`: đọc/ghi dữ liệu cục bộ bổ sung.
- `EntryEditorDialog.java`: form ghi chú/thói quen/sự kiện; giữ bản nháp khi xoay máy.
- `TimerState.java` và `TimerDialog.java`: trạng thái thời gian và giao diện bộ hẹn giờ.
- `ReportDialog.java`: tổng hợp tiến độ thói quen, xuất ảnh và chia sẻ văn bản.
- `res/values-night/`: màu cho giao diện tối.
- `WorkspaceTest.java`: kiểm tra lưu/đọc lại dữ liệu, điều hướng, xoay máy và bản nháp.

### Kiểm tra bản giao diện

Giao diện đã được chỉnh theo 9 ảnh HelloHabit: tiêu đề giữa màn hình, biểu tượng
đen, dải ngày, hàng công việc gọn, ô hoàn thành bên phải, nút + đen và thanh
điều hướng chỉ có biểu tượng. Menu danh sách mở từ cạnh trái; quản lý danh sách
mở trong hộp riêng. Bộ hẹn giờ và báo cáo có thanh điều hướng phía dưới.

Chạm tên công việc hoặc thói quen để sửa/xóa. Tìm công việc từ menu ☰.
Nhật ký có nút tìm kiếm, bộ lọc nhãn và bộ lọc mục đã đánh dấu; nhãn được nhập
trong form thêm/sửa ghi chú. `ReferenceUi.java` nối các nút điều hướng trong
màn hình hẹn giờ/báo cáo về bốn tab chính; `res/values/design.xml` định nghĩa
kiểu nút và `res/drawable/ic_*.xml` chứa biểu tượng.

Đây là bản triển khai Java + XML theo các màn hình đã cung cấp, chưa phải toàn bộ
sản phẩm HelloHabit. Các tính năng máy chủ vẫn theo phạm vi giai đoạn sau.

1. Chạy app bằng Run trong Android Studio, không tạo lại dự án.
2. Công việc: thử thêm/sửa/xóa/hoàn thành và tìm kiếm; dữ liệu cũ phải còn.
3. Chạm +, chọn Thói quen; đánh dấu hôm nay; mở báo cáo kiểm tra số ngày.
4. Nhật ký: thêm nội dung tiếng Việt, đánh dấu trang, lọc và tìm kiếm; đóng/mở lại app.
5. Lịch: chạm một ô giờ, thêm sự kiện, kiểm tra đúng cột ngày/giờ; thử sửa và xóa.
6. Menu ☰: tạo danh sách, chọn công việc, tạo bộ lọc và thử áp dụng.
7. Hẹn giờ: thử bấm giờ, tạm dừng, ghi vòng, đặt lại và đếm ngược; xoay máy khi đang chạy.
8. Báo cáo: thử các kỳ, lưu PNG qua trình chọn tệp và chia sẻ văn bản.
9. Cài đặt: đổi sáng/tối rồi đổi lại; kiểm tra chữ và các nút đọc được.

Chưa có đăng nhập, đồng bộ máy chủ, nhắc việc bằng thông báo, tích hợp Health Connect,
lịch bên ngoài hoặc gói Premium. Spring Boot, FastAPI và ReactJS triển khai ở giai đoạn sau.
### Kết quả xác minh bản hiện tại

- `assembleDebug` và `lintDebug`: thành công; Android Lint không có lỗi, còn các cảnh báo.
- Bảy kiểm thử instrumentation đã chạy thành công trên Pixel 5 API 30 bằng AndroidJUnitRunner,
  gồm migration giữ dữ liệu cũ và chuyển ngày/giữ lịch sử công việc.
- Kiểm tra trực quan các tab, menu danh sách, bấm giờ, đếm ngược, báo cáo và giao diện tối.
- APK mới đã được cài cập nhật trên máy ảo, giữ dữ liệu công việc cũ.
- Luồng chọn tệp lưu PNG và trình chia sẻ có mã xử lý nhưng chưa kiểm tra trọn vẹn với ứng dụng nhận bên ngoài.

Nếu Gradle không tải được plugin trong Terminal của Codex, mở Android Studio với
Gradle JDK 17/21 và Sync/Run. Không cần đổi phiên bản plugin hoặc gỡ app chỉ để cập nhật giao diện.
# Hướng dẫn lần đầu

App mở `OnboardingActivity` trước: nếu chưa có `onboarding_completed = true`
trong SharedPreferences `app_preferences`, hiển thị bốn trang có ảnh chụp thực tế:
Trang chủ, Nhật ký, Lịch và Cài đặt. Bấm **Bỏ qua** hoặc **Bắt đầu** sẽ lưu trạng thái rồi vào Trang chủ.
Các lần mở tiếp theo vào thẳng Trang chủ, chưa cần đăng nhập.

- `OnboardingActivity.java`: chuyển trang, lưu trạng thái và mở Trang chủ.
- `res/layout/activity_onboarding.xml`: ảnh giao diện, chú thích và các nút.
- `res/drawable-nodpi/onboarding_*.png`: ảnh chụp các màn hình chính từ máy ảo.
- `res/values/onboarding_strings.xml`: nội dung hướng dẫn bằng tiếng Việt.
- Trong **Cài đặt → Xem lại hướng dẫn ban đầu**, có thể mở lại hướng dẫn.

Ảnh minh họa không tạo công việc hoặc thay đổi dữ liệu Room. Cài bản cập nhật
đè lên app hiện có; không gỡ app hoặc xóa dữ liệu để xem hướng dẫn.
