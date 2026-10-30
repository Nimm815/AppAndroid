# Legacy backend — không dùng cho bản Android hiện tại

Thư mục này là backend đơn khối cũ, được cấu hình với schema `task_management_app` và cổng 8080. App hiện tại dùng các service trong `backend-spring`: Auth (`taskmind_auth_db`), Task (`taskmind_task_db`) và Productivity (`taskmind_productivity_db`).

Không chạy backend này để đồng bộ app hiện tại và không nạp dump database vào đó. Giữ mã nguồn cũ để đối chiếu cho đến khi hoàn tất việc rà soát dữ liệu; việc chuyển Admin Web khỏi backend/schema cũ là một phần việc riêng.
