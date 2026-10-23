package vn.edu.taskmind.auth.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
    private final AvatarStorage avatars;

    public UserProfileController(JdbcTemplate database, AuthenticatedUser authenticatedUser, AvatarStorage avatars) {
        this.database = database;
        this.authenticatedUser = authenticatedUser;
        this.avatars = avatars;
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
        String phone = ProfileRules.phone(request.phoneNumber());
        LocalDate birthDate = ProfileRules.birthDate(request.birthDate());
        String gender = ProfileRules.gender(request.gender());
        try {
            database.update("UPDATE users SET FullName=?,Email=?,PhoneNumber=?,BirthDate=?,Gender=?,"
                            + "UpdatedAt=UTC_TIMESTAMP() WHERE UserID=? AND IsLocked=0 AND IsDeleted=0",
                    fullName, email, phone, birthDate, gender, userId);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác");
        }
        return profile(userId);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ProfileResponse uploadAvatar(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestPart("file") MultipartFile file) {
        int userId = authenticatedUser.requireId(authorization);
        String reference = avatars.store(file);
        String previous = database.queryForObject("SELECT AvatarRef FROM users WHERE UserID=?", String.class, userId);
        try {
            database.update("UPDATE users SET AvatarRef=?,UpdatedAt=UTC_TIMESTAMP() "
                    + "WHERE UserID=? AND IsLocked=0 AND IsDeleted=0", reference, userId);
        } catch (RuntimeException e) {
            avatars.delete(reference);
            throw e;
        }
        if (previous != null && !previous.equals(reference)) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { avatars.delete(previous); }
                    });
        }
        return profile(userId);
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> getAvatar(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        int userId = authenticatedUser.requireId(authorization);
        String reference = database.queryForObject("SELECT AvatarRef FROM users WHERE UserID=?", String.class, userId);
        if (reference == null || reference.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài khoản chưa có ảnh đại diện");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(avatars.read(reference));
    }

    private ProfileResponse profile(int userId) {
        var rows = database.queryForList(
                "SELECT UserID,FullName,Email,PhoneNumber,BirthDate,Gender,AvatarRef "
                        + "FROM users WHERE UserID=? AND IsLocked=0 AND IsDeleted=0",
                userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn hoạt động");
        }
        Map<String, Object> row = rows.get(0);
        Object birthDate = row.get("BirthDate");
        Object avatarRef = row.get("AvatarRef");
        return new ProfileResponse(
                ((Number) row.get("UserID")).intValue(),
                (String) row.get("FullName"),
                (String) row.get("Email"),
                (String) row.get("PhoneNumber"),
                birthDate == null ? null : birthDate.toString(),
                (String) row.get("Gender"),
                avatarRef == null ? null : "/api/users/me/avatar");
    }
}
