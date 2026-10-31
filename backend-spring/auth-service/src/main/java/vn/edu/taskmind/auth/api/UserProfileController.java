package vn.edu.taskmind.auth.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {
    public record ProfileRequest(String fullName, String email, String phoneNumber,
                                String birthDate, String gender) { }

    public record ProfileResponse(int userId, String fullName, String email, String phoneNumber,
                                  String birthDate, String gender, String avatarUrl) { }

    private final JdbcTemplate database;
    private final AuthenticatedUser authenticatedUser;
    public UserProfileController(JdbcTemplate database, AuthenticatedUser authenticatedUser) {
        this.database = database;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public ProfileResponse get(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return profile(authenticatedUser.requireId(authorization));
    }

    @PutMapping
    @Transactional
    public ProfileResponse update(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                  @RequestBody ProfileRequest request) {
        int userId = authenticatedUser.requireId(authorization);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thông tin hồ sơ");
        String fullName = ProfileRules.fullName(request.fullName());
        String email = ProfileRules.email(request.email());
        if ((request.phoneNumber() != null && !request.phoneNumber().isBlank())
                || (request.birthDate() != null && !request.birthDate().isBlank())
                || (request.gender() != null && !request.gender().isBlank())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bảng users hiện tại chỉ lưu họ tên và email; avatar không có cột trong schema đã xác nhận.");
        }
        try {
            database.update("UPDATE users SET FullName=?,Email=?,UpdatedAt=UTC_TIMESTAMP() "
                            + "WHERE UserID=? AND IsLocked=0 AND IsDeleted=0", fullName, email, userId);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác");
        }
        return profile(userId);
    }

    private ProfileResponse profile(int userId) {
        var rows = database.queryForList(
                "SELECT UserID,FullName,Email "
                        + "FROM users WHERE UserID=? AND IsLocked=0 AND IsDeleted=0",
                userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn hoạt động");
        }
        Map<String, Object> row = rows.get(0);
        return new ProfileResponse(
                ((Number) row.get("UserID")).intValue(),
                (String) row.get("FullName"),
                (String) row.get("Email"),
                null, null, null, null);
    }
}
