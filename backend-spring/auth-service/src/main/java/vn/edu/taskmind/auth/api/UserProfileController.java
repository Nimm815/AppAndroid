package vn.edu.taskmind.auth.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.time.LocalDate;

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
        String phoneNumber = ProfileRules.phone(request.phoneNumber());
        LocalDate birthDate = ProfileRules.birthDate(request.birthDate());
        String gender = ProfileRules.gender(request.gender());
        try {
            database.update("UPDATE users SET FullName=?,Email=?,PhoneNumber=?,BirthDate=?,Gender=?,UpdatedAt=UTC_TIMESTAMP() "
                            + "WHERE UserID=? AND IsLocked=0 AND IsDeleted=0",
                    fullName, email, phoneNumber, birthDate, gender, userId);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác");
        }
        return profile(userId);
    }

    private ProfileResponse profile(int userId) {
        var rows = database.queryForList(
                "SELECT UserID,FullName,Email,PhoneNumber,BirthDate,Gender "
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
                row.get("PhoneNumber") == null ? null : row.get("PhoneNumber").toString(),
                row.get("BirthDate") == null ? null : row.get("BirthDate").toString(),
                (String) row.get("Gender"), null);
    }
}
