package vn.edu.taskmind.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.taskmind.backend.user.User;
import vn.edu.taskmind.backend.user.UserRepository;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecretKey jwtKey;
    private final long expirationMs;

    public AuthController(UserRepository users,
                          @Value("${taskmind.jwt.secret}") String secret,
                          @Value("${taskmind.jwt.expiration-ms:86400000}") long expirationMs) {
        this.users = users;
        this.jwtKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        String fullName = clean(request.fullName);
        String email = clean(request.email).toLowerCase(java.util.Locale.ROOT);
        String password = request.password == null ? "" : request.password;
        if (fullName.isEmpty() || fullName.length() > 100) return error(HttpStatus.BAD_REQUEST, "Họ tên không hợp lệ");
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) return error(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        if (password.length() < 8 || password.length() > 72) return error(HttpStatus.BAD_REQUEST, "Mật khẩu phải có từ 8 đến 72 ký tự");
        if (users.existsByEmailIgnoreCase(email)) return error(HttpStatus.CONFLICT, "Email đã được đăng ký");
        User user = new User(); user.setFullName(fullName); user.setEmail(email); user.setPasswordHash(encoder.encode(password));
        users.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(message("Đăng ký thành công. Hãy đăng nhập."));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        String email = clean(request.email).toLowerCase(java.util.Locale.ROOT);
        String password = request.password == null ? "" : request.password;
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.isDeleted() || !encoder.matches(password, user.getPasswordHash()))
            return error(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        if (user.isLocked()) return error(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên.");
        Instant now = Instant.now();
        String token = Jwts.builder().subject(String.valueOf(user.getUserId())).claim("email", user.getEmail())
                .issuedAt(java.util.Date.from(now)).expiration(java.util.Date.from(now.plusMillis(expirationMs)))
                .signWith(jwtKey).compact();
        Map<String, Object> result = message("Đăng nhập thành công");
        result.put("token", token); result.put("userId", user.getUserId()); result.put("fullName", user.getFullName());
        result.put("email", user.getEmail()); result.put("role", user.getRole());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        // JWT is stateless; the Android client must discard the token on logout.
        return ResponseEntity.ok(message("Đã đăng xuất trên thiết bị này"));
    }

    @PostMapping("/delete-account")
    public ResponseEntity<Map<String, Object>> deleteAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody DeleteRequest request) {
        Integer userId = getUserId(authorization);
        if (userId == null) return error(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        User user = users.findById(userId).orElse(null);
        if (user == null || user.isDeleted()) return error(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        String password = request.password == null ? "" : request.password;
        if (!encoder.matches(password, user.getPasswordHash())) return error(HttpStatus.UNAUTHORIZED, "Mật khẩu xác nhận không đúng");
        // Anonymize instead of hard-delete so existing task/habit foreign keys remain valid.
        user.setDeleted(true);
        user.setFullName("Tài khoản đã xóa");
        user.setEmail("deleted+" + user.getUserId() + "+" + System.currentTimeMillis() + "@deleted.taskmind.local");
        user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        users.save(user);
        return ResponseEntity.ok(message("Tài khoản đã được xóa và thông tin nhận dạng đã được ẩn"));
    }
    
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody ChangePasswordRequest request) {

        Integer userId = getUserId(authorization);

        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        }

        User user = users.findById(userId).orElse(null);

        if (user == null || user.isDeleted()) {
            return error(HttpStatus.NOT_FOUND,
                    "Không tìm thấy tài khoản");
        }

        if (user.isLocked()) {
            return error(HttpStatus.FORBIDDEN,
                    "Tài khoản đang bị khóa");
        }

        String currentPassword = request.currentPassword == null
                ? "" : request.currentPassword;
        String newPassword = request.newPassword == null
                ? "" : request.newPassword;

        if (!encoder.matches(
                currentPassword, user.getPasswordHash())) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Mật khẩu hiện tại không chính xác");
        }

        if (newPassword.length() < 8
                || newPassword.length() > 72) {
            return error(HttpStatus.BAD_REQUEST,
                    "Mật khẩu mới phải có từ 8 đến 72 ký tự");
        }

        if (encoder.matches(
                newPassword, user.getPasswordHash())) {
            return error(HttpStatus.BAD_REQUEST,
                    "Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);

        return ResponseEntity.ok(
                message("Đổi mật khẩu thành công"));
    }


    private Integer getUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        try {
            Claims claims = Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(authorization.substring(7)).getPayload();
            return Integer.parseInt(claims.getSubject());
        } catch (Exception e) { return null; }
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static Map<String, Object> message(String text) { Map<String, Object> m = new HashMap<>(); m.put("message", text); return m; }
    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String text) { return ResponseEntity.status(status).body(message(text)); }

    public static class RegisterRequest { public String fullName; public String email; public String password; }
    public static class LoginRequest { public String email; public String password; }
    public static class DeleteRequest { public String password; }
    public static class ChangePasswordRequest {
    public String currentPassword;
    public String newPassword;
}
}
