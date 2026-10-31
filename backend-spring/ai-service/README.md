# AI Service (FastAPI)

AI Service dùng Python + FastAPI, chạy độc lập ở cổng `8084`. Service gọi Gemini, lấy dữ liệu của tài khoản đang đăng nhập qua Auth/Task/Productivity API và ghi hội thoại vào `taskmind_ai_db` (`aichats`, `aichatmessages`). Service không truy vấn trực tiếp database nghiệp vụ và không tạo/sửa bảng.

## Chuẩn bị môi trường

Yêu cầu Python 3.11 trở lên. Trong PowerShell, tại thư mục `backend-spring/ai-service`:

```powershell
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
```

Điền thông tin kết nối MySQL của `taskmind_ai_db` và `GEMINI_API_KEY` vào `.env` trên máy cá nhân. Không commit `.env`, không đặt Gemini key trong Android. `GEMINI_MODEL` có thể đổi theo model Gemini được tài khoản Google AI Studio/API của bạn hỗ trợ.

## Chạy

Khởi động Auth, Task và Productivity như mô tả trong `../README.md`, sau đó tại `ai-service` chạy:

```powershell
python -m uvicorn app.main:app --host 127.0.0.1 --port 8084
```

Kiểm tra `http://127.0.0.1:8084/health`. Tài liệu API tương tác ở `http://127.0.0.1:8084/docs`.

## API chat

`POST /api/ai/chat` yêu cầu `Authorization: Bearer <token>` và JSON:

```json
{"message":"Hôm nay tôi nên ưu tiên công việc nào?","chat_id":null}
```

`chat_id` có thể bỏ qua ở cuộc trò chuyện mới; các lượt sau gửi lại ID nhận được. Service xác thực token qua Auth Service, rồi chuyển tiếp token đến Task và Productivity để các service lọc dữ liệu theo chủ sở hữu. Dữ liệu nghiệp vụ được gửi tới Google Gemini để tạo câu trả lời; hội thoại được lưu theo UserID trong `taskmind_ai_db`. `GET /api/ai/chats` liệt kê hội thoại của tài khoản; `GET /api/ai/chats/{chat_id}/messages` đọc lại tin nhắn trong hội thoại đó.

API hiện lấy danh sách công việc, danh sách công việc, thói quen, nhật ký, sự kiện lịch, nhắc việc và thông báo qua các endpoint hiện có. Các API nghiệp vụ hiện chỉ bật bằng profile phát triển và cần Auth/Task/Productivity đang chạy. Trước khi dùng với người dùng thật, cần triển khai profile xác thực/ủy quyền phù hợp môi trường production.

Database phải có sẵn các bảng chat theo schema đã có. Backend không tự tạo bảng; nếu schema thực tế khác entity cũ, cần đối chiếu thủ công trước khi bật ghi hội thoại.
