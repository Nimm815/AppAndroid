package vn.edu.taskmanager.auth;

import java.time.LocalDate;

public final class ProfileValidator {
    private ProfileValidator() { }

    public static String validate(ProfileModels.UpdateRequest request) {
        String name = request.fullName == null ? "" : request.fullName.trim();
        if (name.isEmpty() || name.length() > 100) return "Họ và tên phải có từ 1 đến 100 ký tự.";

        String email = request.email == null ? "" : request.email.trim();
        if (email.length() > 255 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            return "Email không hợp lệ.";
        }

        String phone = request.phoneNumber == null ? "" : request.phoneNumber.trim();
        if (!phone.isEmpty() && !phone.matches("(?:0[35789][0-9]{8}|\\+84[35789][0-9]{8})")) {
            return "Số điện thoại phải có định dạng Việt Nam, ví dụ 0912345678.";
        }

        if (request.birthDate != null && !request.birthDate.isBlank()) {
            try {
                if (LocalDate.parse(request.birthDate).isAfter(LocalDate.now())) {
                    return "Ngày sinh không được ở tương lai.";
                }
            } catch (java.time.format.DateTimeParseException e) {
                return "Ngày sinh không hợp lệ.";
            }
        }

        if (request.gender != null && !request.gender.isBlank()
                && !request.gender.equals("MALE") && !request.gender.equals("FEMALE")
                && !request.gender.equals("OTHER")) {
            return "Vui lòng chọn giới tính hợp lệ.";
        }
        return null;
    }
}
