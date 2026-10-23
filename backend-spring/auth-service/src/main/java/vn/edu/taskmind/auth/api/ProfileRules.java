package vn.edu.taskmind.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

final class ProfileRules {
    private ProfileRules() { }

    static String fullName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw badRequest("Họ và tên phải có từ 1 đến 100 ký tự");
        }
        return normalized;
    }

    static String email(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 255 || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw badRequest("Email không hợp lệ");
        }
        return normalized;
    }

    static String phone(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return null;
        if (!normalized.matches("(?:0[35789][0-9]{8}|\\+84[35789][0-9]{8})")) {
            throw badRequest("Số điện thoại phải theo định dạng Việt Nam, ví dụ 0912345678");
        }
        return normalized;
    }

    static LocalDate birthDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.isAfter(LocalDate.now())) throw badRequest("Ngày sinh không được ở tương lai");
            return date;
        } catch (DateTimeParseException e) {
            throw badRequest("Ngày sinh không hợp lệ");
        }
    }

    static String gender(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("MALE") && !normalized.equals("FEMALE") && !normalized.equals("OTHER")) {
            throw badRequest("Giới tính không hợp lệ");
        }
        return normalized;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
