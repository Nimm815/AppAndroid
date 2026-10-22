package vn.edu.taskmanager.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Tiện ích hỗ trợ tìm kiếm tiếng Việt không phân biệt dấu và chữ hoa/thường (UC09).
 */
public class VietnameseUtils {

    private static final Pattern DIACRITICS_PATTERN =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Chuyển chuỗi tiếng Việt có dấu thành không dấu và chữ thường.
     * Ví dụ: "Đọc Sách" -> "doc sach".
     */
    public static String stripDiacritics(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutMarks = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'd')
                .trim().toLowerCase();
    }

    /**
     * Kiểm tra xem chuỗi nguồn có chứa từ khóa tìm kiếm hay không (không phân biệt dấu và hoa/thường).
     */
    public static boolean containsIgnoreAccents(String source, String query) {
        if (source == null || query == null) return false;
        String normalizedSource = stripDiacritics(source);
        String normalizedQuery = stripDiacritics(query);
        return normalizedSource.contains(normalizedQuery);
    }
}
