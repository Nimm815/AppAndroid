package vn.edu.taskmind.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local development endpoints for the Admin Web; user data stays in the Auth database. */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class AdminController {
    private final JdbcTemplate database;

    public AdminController(JdbcTemplate database) { this.database = database; }

    private static void requireLocal(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin API chỉ dành cho máy phát triển.");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không xác định được địa chỉ truy cập.");
        }
    }

    @GetMapping("/users")
    public UserPage users(@RequestParam(defaultValue = "") String search,
                          @RequestParam(defaultValue = "ALL") String status,
                          @RequestParam(defaultValue = "ALL") String role,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "8") int size,
                          HttpServletRequest request) {
        requireLocal(request);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (FullName LIKE ? OR Email LIKE ?)");
            String pattern = "%" + search.trim() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
        }
        switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> where.append(" AND IsDeleted=0 AND IsLocked=0");
            case "LOCKED" -> where.append(" AND IsDeleted=0 AND IsLocked=1");
            case "DELETED" -> where.append(" AND IsDeleted=1");
            case "ALL" -> { }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc trạng thái không hợp lệ.");
        }
        String normalizedRole = role.toUpperCase(Locale.ROOT);
        if (!normalizedRole.equals("ALL")) {
            where.append(" AND Role=?");
            parameters.add(normalizedRole);
        }
        Long total = database.queryForObject("SELECT COUNT(*) FROM users" + where, Long.class, parameters.toArray());
        parameters.add(safeSize);
        parameters.add(safePage * safeSize);
        List<AdminUser> result = database.query("SELECT UserID,FullName,Email,Role,IsLocked,IsDeleted,CreatedAt "
                        + "FROM users" + where + " ORDER BY CreatedAt DESC,UserID DESC LIMIT ? OFFSET ?",
                (row, index) -> new AdminUser(row.getLong("UserID"), row.getString("FullName"), row.getString("Email"),
                        row.getString("Role"), row.getBoolean("IsDeleted") ? "DELETED" : row.getBoolean("IsLocked") ? "LOCKED" : "ACTIVE",
                        row.getTimestamp("CreatedAt").toLocalDateTime(), 0, null), parameters.toArray());
        return new UserPage(result, total == null ? 0 : total, safePage, safeSize);
    }

    @PatchMapping("/users/{id}")
    public AdminUser update(@PathVariable int id, @RequestBody UpdateUser body, HttpServletRequest request) {
        requireLocal(request);
        if (body.fullName() != null && (body.fullName().isBlank() || body.fullName().length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên không hợp lệ.");
        }
        database.update("UPDATE users SET FullName=COALESCE(?,FullName),Role=COALESCE(?,Role),UpdatedAt=UTC_TIMESTAMP() "
                + "WHERE UserID=? AND IsDeleted=0", body.fullName(), body.role(), id);
        return find(id);
    }

    @PatchMapping("/users/{id}/lock")
    @Transactional
    public AdminUser lock(@PathVariable int id, @RequestBody LockUser body, HttpServletRequest request) {
        requireLocal(request);
        database.update("UPDATE users SET IsLocked=?,UpdatedAt=UTC_TIMESTAMP() WHERE UserID=? AND IsDeleted=0",
                body.locked(), id);
        return find(id);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id, HttpServletRequest request) {
        requireLocal(request);
        int changed = database.update("UPDATE users SET IsDeleted=1,UpdatedAt=UTC_TIMESTAMP() WHERE UserID=? AND IsDeleted=0", id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.");
    }

    @GetMapping("/stats")
    public AuthStats stats(HttpServletRequest request) {
        requireLocal(request);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Counts counts = database.queryForObject("SELECT SUM(CASE WHEN IsDeleted=0 THEN 1 ELSE 0 END) AS totalUsers, "
                        + "COALESCE(SUM(CASE WHEN IsDeleted=0 AND CreatedAt>=? THEN 1 ELSE 0 END),0) AS newUsers, "
                        + "COALESCE(SUM(CASE WHEN IsDeleted=0 AND IsLocked=1 THEN 1 ELSE 0 END),0) AS lockedUsers, "
                        + "COALESCE(SUM(CASE WHEN IsDeleted=0 AND IsLocked=0 THEN 1 ELSE 0 END),0) AS enabledUsers "
                        + "FROM users",
                (row, index) -> new Counts(row.getLong("totalUsers"), row.getLong("newUsers"),
                        row.getLong("lockedUsers"), row.getLong("enabledUsers")), monthStart);
        return new AuthStats(counts.total(), counts.newUsers(), counts.locked(), counts.enabled(), counts.locked());
    }

    private AdminUser find(int id) {
        List<AdminUser> found = database.query("SELECT UserID,FullName,Email,Role,IsLocked,IsDeleted,CreatedAt FROM users WHERE UserID=? AND IsDeleted=0",
                (row, index) -> new AdminUser(row.getLong("UserID"), row.getString("FullName"), row.getString("Email"),
                        row.getString("Role"), row.getBoolean("IsDeleted") ? "DELETED" : row.getBoolean("IsLocked") ? "LOCKED" : "ACTIVE",
                        row.getTimestamp("CreatedAt").toLocalDateTime(), 0, null), id);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.");
        return found.get(0);
    }

    public record UserPage(List<AdminUser> content, long totalElements, int page, int size) { }
    public record AdminUser(long id, String fullName, String email, String role, String status,
                            LocalDateTime createdAt, long taskCount, LocalDateTime lastActivityAt) { }
    public record UpdateUser(String fullName, String role) { }
    public record LockUser(boolean locked) { }
    public record AuthStats(long totalUsers, long newUsersThisMonth, long lockedUsers,
                            long enabledUsers, long disabledUsers) { }
    private record Counts(long total, long newUsers, long locked, long enabled) { }
}
