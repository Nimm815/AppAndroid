package vn.edu.taskmind.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AuthenticatedUser {
    private final JdbcTemplate database;

    public AuthenticatedUser(JdbcTemplate database) {
        this.database = database;
    }

    public int requireId(String authorization) {
        if (authorization == null || !authorization.matches("Bearer [A-Za-z0-9_-]{43}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại");
        }

        String token = authorization.substring("Bearer ".length());
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }

        var users = database.queryForList(
                "SELECT u.UserID FROM auth_sessions s JOIN users u ON u.UserID=s.UserID "
                        + "WHERE s.TokenHash=? AND s.ExpiresAt>UTC_TIMESTAMP() "
                        + "AND u.IsLocked=0 AND u.IsDeleted=0",
                hash);
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        }
        return ((Number) users.get(0).get("UserID")).intValue();
    }
}
