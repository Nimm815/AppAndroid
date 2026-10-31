package vn.edu.taskmind.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthenticatedUser {
    private final JdbcTemplate database;
    private final SignedBearerTokens tokens;

    public AuthenticatedUser(JdbcTemplate database, SignedBearerTokens tokens) {
        this.database = database;
        this.tokens = tokens;
    }

    public int requireId(String authorization) {
        SignedBearerTokens.Claims claims = tokens.claims(authorization);
        if (claims == null) throw unauthorized();
        var users = database.queryForList("SELECT PasswordHash FROM users "
                        + "WHERE UserID=? AND IsLocked=0 AND IsDeleted=0", claims.userId());
        if (users.isEmpty() || !tokens.verify(authorization, (String) users.get(0).get("PasswordHash"))) {
            throw unauthorized();
        }
        return claims.userId();
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }
}
