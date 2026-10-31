package vn.edu.taskmind.auth.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/** Registration and login over the existing users table; sessions are stateless. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long TOKEN_SECONDS = 30L * 24L * 60L * 60L;

    private final JdbcTemplate database;
    private final SignedBearerTokens tokens;

    public AuthController(JdbcTemplate database, SignedBearerTokens tokens) {
        this.database = database;
        this.tokens = tokens;
    }

    public record Credentials(String email, String password, String fullName) { }

    private static String email(Credentials credentials) {
        if (credentials == null || credentials.email() == null || credentials.email().length() > 255
                || !credentials.email().trim().matches("[^ @]+@[^ @]+\\.[^ @]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        }
        if (credentials.password() == null || credentials.password().length() < 8
                || credentials.password().length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu cần 8–128 ký tự");
        }
        return credentials.email().trim().toLowerCase(Locale.ROOT);
    }

    private static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$600000$" + Base64.getEncoder().encodeToString(salt) + "$" + derive(password, salt);
    }

    private static String derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        try {
            return Base64.getEncoder().encodeToString(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean matches(String password, String hash) {
        try {
            String[] parts = hash.split("\\$");
            return parts.length == 4 && parts[0].equals("pbkdf2-sha256") && parts[1].equals("600000")
                    && MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                    Base64.getDecoder().decode(derive(password, Base64.getDecoder().decode(parts[2]))));
        } catch (RuntimeException error) {
            return false;
        }
    }

    private Map<String, Object> response(int userId, String email, String fullName, String passwordHash) {
        long expiresAt = System.currentTimeMillis() / 1000L + TOKEN_SECONDS;
        return Map.of("token", tokens.issue(userId, email, passwordHash, expiresAt), "userId", userId,
                "email", email, "fullName", fullName, "expiresIn", TOKEN_SECONDS);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> register(@RequestBody Credentials credentials) {
        String normalizedEmail = email(credentials);
        if (credentials.fullName() == null || credentials.fullName().isBlank() || credentials.fullName().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên không hợp lệ");
        }
        String fullName = credentials.fullName().trim();
        String passwordHash = hashPassword(credentials.password());
        try {
            database.update("INSERT INTO users(FullName,Email,PasswordHash,Role) VALUES (?,?,?,'USER')",
                    fullName, normalizedEmail, passwordHash);
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được đăng ký");
        }
        Integer userId = database.queryForObject("SELECT UserID FROM users WHERE Email=?", Integer.class, normalizedEmail);
        return response(userId, normalizedEmail, fullName, passwordHash);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Credentials credentials) {
        String normalizedEmail = email(credentials);
        // Force MySQL TINYINT(1) flags to numeric values; Connector/J may otherwise return Boolean.
        var users = database.queryForList("SELECT UserID,FullName,PasswordHash,IsLocked+0 AS IsLocked,IsDeleted+0 AS IsDeleted "
                + "FROM users WHERE Email=?", normalizedEmail);
        if (users.isEmpty()) {
            derive(credentials.password(), new byte[16]);
            throw unauthorized();
        }
        Map<String, Object> user = users.get(0);
        String passwordHash = (String) user.get("PasswordHash");
        if (!matches(credentials.password(), passwordHash)
                || ((Number) user.get("IsLocked")).intValue() != 0
                || ((Number) user.get("IsDeleted")).intValue() != 0) {
            throw unauthorized();
        }
        return response(((Number) user.get("UserID")).intValue(), normalizedEmail,
                (String) user.get("FullName"), passwordHash);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        SignedBearerTokens.Claims claims = tokens.claims(authorization);
        if (claims == null) throw unauthorized();
        var users = database.queryForList("SELECT UserID,Email,FullName,PasswordHash FROM users "
                        + "WHERE UserID=? AND IsLocked=0 AND IsDeleted=0", claims.userId());
        if (users.isEmpty()) throw unauthorized();
        Map<String, Object> user = users.get(0);
        if (!tokens.verify(authorization, (String) user.get("PasswordHash"))) throw unauthorized();
        return Map.of("userId", ((Number) user.get("UserID")).intValue(), "email", user.get("Email"),
                "fullName", user.get("FullName"));
    }

    /** The Android client discards its local token on logout; no session table is required. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        me(authorization);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không hợp lệ");
    }
}
