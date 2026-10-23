package vn.edu.taskmanager.backend;

import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class AdminController {
    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Admin routes are local-development only until an authentication policy is selected. */
    private void requireLocalRequest(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin API chỉ được dùng trên máy phát triển.");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không xác định được địa chỉ truy cập.");
        }
    }

    @GetMapping("/users")
    public UserPage getUsers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            HttpServletRequest request) {
        requireLocalRequest(request);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            where.append(" AND (u.FullName LIKE ? OR u.Email LIKE ?)");
            String pattern = "%" + search.trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }

        String normalizedStatus = status.toUpperCase(Locale.ROOT);
        switch (normalizedStatus) {
            case "ACTIVE" -> where.append(" AND u.IsDeleted=0 AND u.IsLocked=0");
            case "LOCKED" -> where.append(" AND u.IsDeleted=0 AND u.IsLocked=1");
            case "DELETED" -> where.append(" AND u.IsDeleted=1");
            case "ALL" -> { }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc trạng thái không hợp lệ.");
        }

        String normalizedRole = role.toUpperCase(Locale.ROOT);
        if (!normalizedRole.equals("ALL")) {
            if (!normalizedRole.equals("USER") && !normalizedRole.equals("ADMIN")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc vai trò không hợp lệ.");
            }
            where.append(" AND u.Role=?");
            params.add(normalizedRole);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users u" + where, Long.class, params.toArray());
        String sql = """
                SELECT u.UserID, u.FullName, u.Email, u.Role, u.IsLocked, u.IsDeleted, u.CreatedAt,
                       COALESCE(t.TaskCount, 0) AS TaskCount, a.LastActivityAt
                FROM users u
                LEFT JOIN (SELECT UserID, COUNT(*) AS TaskCount FROM tasks GROUP BY UserID) t ON t.UserID=u.UserID
                LEFT JOIN (SELECT UserID, MAX(CreatedAt) AS LastActivityAt FROM activitylogs GROUP BY UserID) a ON a.UserID=u.UserID
                """ + where + " ORDER BY u.CreatedAt DESC, u.UserID DESC LIMIT ? OFFSET ?";
        params.add(safeSize);
        params.add(safePage * safeSize);
        List<AdminUser> users = jdbc.query(sql, USER_ROW_MAPPER, params.toArray());
        return new UserPage(users, total == null ? 0 : total, safePage, safeSize);
    }

    @PatchMapping("/users/{id}")
    public AdminUser updateUser(@PathVariable int id, @Valid @RequestBody UpdateUserRequest body,
                                HttpServletRequest request) {
        requireLocalRequest(request);
        if (body.fullName() != null && body.fullName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên không được để trống.");
        }
        if (body.role() != null && !body.role().equals("USER") && !body.role().equals("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò phải là USER hoặc ADMIN.");
        }
        jdbc.update("UPDATE users SET FullName=COALESCE(?, FullName), Role=COALESCE(?, Role) WHERE UserID=? AND IsDeleted=0",
                body.fullName(), body.role(), id);
        return findUser(id);
    }

    @PatchMapping("/users/{id}/lock")
    public AdminUser setLocked(@PathVariable int id, @Valid @RequestBody LockUserRequest body,
                               HttpServletRequest request) {
        requireLocalRequest(request);
        jdbc.update("UPDATE users SET IsLocked=? WHERE UserID=? AND IsDeleted=0", body.locked(), id);
        return findUser(id);
    }

    @DeleteMapping("/users/{id}")
    public void softDelete(@PathVariable int id, HttpServletRequest request) {
        requireLocalRequest(request);
        int changed = jdbc.update("UPDATE users SET IsDeleted=1 WHERE UserID=? AND IsDeleted=0", id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.");
    }

    @GetMapping("/stats")
    public AdminStats getStats(HttpServletRequest request) {
        requireLocalRequest(request);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime from = LocalDate.now().minusDays(29).atStartOfDay();
        StatsCounts counts = jdbc.queryForObject("""
                SELECT
                  COALESCE(SUM(CASE WHEN IsDeleted=0 THEN 1 ELSE 0 END), 0) AS totalUsers,
                  COALESCE(SUM(CASE WHEN IsDeleted=0 AND CreatedAt>=? THEN 1 ELSE 0 END), 0) AS newUsersThisMonth,
                  COALESCE(SUM(CASE WHEN IsDeleted=0 AND IsLocked=1 THEN 1 ELSE 0 END), 0) AS lockedUsers,
                  COALESCE(SUM(CASE WHEN IsDeleted=0 AND IsLocked=0 THEN 1 ELSE 0 END), 0) AS enabledUsers
                FROM users
                """, (rs, rowNum) -> new StatsCounts(rs.getLong("totalUsers"),
                rs.getLong("newUsersThisMonth"), rs.getLong("lockedUsers"), rs.getLong("enabledUsers")), monthStart);
        Long activeUsers = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.UserID)
                FROM activitylogs l JOIN users u ON u.UserID=l.UserID
                WHERE l.CreatedAt>=? AND u.IsDeleted=0 AND u.IsLocked=0
                """, Long.class, from);
        Long taskCount = jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class);
        Long activityCount = jdbc.queryForObject("SELECT COUNT(*) FROM activitylogs WHERE CreatedAt>=?", Long.class, from);
        List<ActivityPoint> activity = jdbc.query("""
                SELECT DATE(CreatedAt) AS activityDate, COUNT(*) AS activityCount
                FROM activitylogs WHERE CreatedAt>=?
                GROUP BY DATE(CreatedAt) ORDER BY DATE(CreatedAt)
                """, (rs, rowNum) -> new ActivityPoint(rs.getDate("activityDate").toLocalDate(), rs.getLong("activityCount")), from);
        return new AdminStats(counts.totalUsers(), counts.newUsersThisMonth(), activeUsers == null ? 0 : activeUsers,
                counts.lockedUsers(), taskCount == null ? 0 : taskCount,
                activityCount == null ? 0 : activityCount, counts.enabledUsers(), counts.lockedUsers(), activity);
    }

    private AdminUser findUser(int id) {
        List<AdminUser> found = jdbc.query("""
                SELECT u.UserID, u.FullName, u.Email, u.Role, u.IsLocked, u.IsDeleted, u.CreatedAt,
                       COALESCE(t.TaskCount, 0) AS TaskCount, a.LastActivityAt
                FROM users u
                LEFT JOIN (SELECT UserID, COUNT(*) AS TaskCount FROM tasks GROUP BY UserID) t ON t.UserID=u.UserID
                LEFT JOIN (SELECT UserID, MAX(CreatedAt) AS LastActivityAt FROM activitylogs GROUP BY UserID) a ON a.UserID=u.UserID
                WHERE u.UserID=?
                """, USER_ROW_MAPPER, id);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.");
        return found.get(0);
    }

    private static final RowMapper<AdminUser> USER_ROW_MAPPER = (rs, rowNum) -> new AdminUser(
            rs.getLong("UserID"), rs.getString("FullName"), rs.getString("Email"), rs.getString("Role"),
            userStatus(rs), toLocalDateTime(rs.getTimestamp("CreatedAt")), rs.getLong("TaskCount"),
            toLocalDateTime(rs.getTimestamp("LastActivityAt")));

    private static String userStatus(ResultSet rs) throws SQLException {
        if (rs.getBoolean("IsDeleted")) return "DELETED";
        return rs.getBoolean("IsLocked") ? "LOCKED" : "ACTIVE";
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record UserPage(List<AdminUser> content, long totalElements, int page, int size) { }
    public record AdminUser(long id, String fullName, String email, String role, String status,
                            LocalDateTime createdAt, long taskCount, LocalDateTime lastActivityAt) { }
    public record UpdateUserRequest(@Size(max = 100) String fullName, String role) { }
    public record LockUserRequest(boolean locked) { }
    public record ActivityPoint(LocalDate date, long count) { }
    public record AdminStats(long totalUsers, long newUsersThisMonth, long activeUsers30Days, long lockedUsers,
                             long taskCount, long activityCount30Days, long enabledUsers, long disabledUsers,
                             List<ActivityPoint> dailyActivity) { }
    private record StatsCounts(long totalUsers, long newUsersThisMonth, long lockedUsers, long enabledUsers) { }
}
