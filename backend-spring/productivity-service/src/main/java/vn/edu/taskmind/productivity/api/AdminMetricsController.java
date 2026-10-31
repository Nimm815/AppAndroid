package vn.edu.taskmind.productivity.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.time.LocalDate;
import java.util.*;

/** Read-only Admin views over real productivity history; no synthetic audit events are generated. */
@RestController
@Profile("dev")
@RequestMapping("/api/admin")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class AdminMetricsController {
    private final JdbcTemplate database;
    public AdminMetricsController(JdbcTemplate database) { this.database = database; }

    private static void requireLocal(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress())
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        } catch (org.springframework.web.server.ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN); }
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(HttpServletRequest request) {
        requireLocal(request);
        LocalDate from = LocalDate.now().minusDays(29);
        long activityCount = database.queryForObject("SELECT (SELECT COUNT(*) FROM habitlogs WHERE LogDate>=?) + "
                + "(SELECT COUNT(*) FROM journalentries WHERE EntryDate>=?) + "
                + "(SELECT COUNT(*) FROM calendarevents WHERE StartTime>=?)", Long.class, from, from.atStartOfDay(), from.atStartOfDay());
        long activeUsers = database.queryForObject("SELECT COUNT(DISTINCT UserID) FROM (" +
                "SELECT h.UserID FROM habitlogs hl JOIN habits h ON h.HabitID=hl.HabitID WHERE hl.LogDate>=? UNION " +
                "SELECT UserID FROM journalentries WHERE EntryDate>=? UNION " +
                "SELECT UserID FROM calendarevents WHERE StartTime>=?) activity_users", Long.class,
                from, from.atStartOfDay(), from.atStartOfDay());
        List<Map<String, Object>> daily = database.query("SELECT ActivityDate,COUNT(*) AS ActivityCount FROM (" +
                        "SELECT DATE(LogDate) ActivityDate FROM habitlogs WHERE LogDate>=? UNION ALL " +
                        "SELECT DATE(EntryDate) ActivityDate FROM journalentries WHERE EntryDate>=? UNION ALL " +
                        "SELECT DATE(StartTime) ActivityDate FROM calendarevents WHERE StartTime>=?) activity " +
                        "GROUP BY ActivityDate ORDER BY ActivityDate",
                (row, index) -> Map.of("date", row.getDate("ActivityDate").toLocalDate(), "count", row.getLong("ActivityCount")),
                from, from.atStartOfDay(), from.atStartOfDay());
        return Map.of("activityCount30Days", activityCount, "activeUsers30Days", activeUsers, "dailyActivity", daily);
    }

    @GetMapping("/user-activity-summary")
    public Map<Integer, String> userActivitySummary(HttpServletRequest request) {
        requireLocal(request);
        Map<Integer, String> result = new LinkedHashMap<>();
        database.query("SELECT UserID,MAX(ActivityDate) AS LastActivity FROM (" +
                        "SELECT h.UserID,DATE(hl.LogDate) ActivityDate FROM habitlogs hl " +
                        "JOIN habits h ON h.HabitID=hl.HabitID UNION ALL " +
                        "SELECT UserID,DATE(EntryDate) ActivityDate FROM journalentries UNION ALL " +
                        "SELECT UserID,DATE(StartTime) ActivityDate FROM calendarevents) activity GROUP BY UserID",
                (RowCallbackHandler) row -> {
                    result.put(row.getInt("UserID"), row.getDate("LastActivity").toLocalDate().toString());
                });
        return result;
    }

    @GetMapping("/activity")
    public ActivityPage activity(@RequestParam(defaultValue = "") String search,
                                 @RequestParam(defaultValue = "all") String kind,
                                 @RequestParam(defaultValue = "30") String range,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 HttpServletRequest request) {
        requireLocal(request);
        int safePage = Math.max(page, 0), safeSize = Math.max(1, Math.min(size, 100));
        LocalDate from = "all".equalsIgnoreCase(range) ? LocalDate.of(2000, 1, 1)
                : LocalDate.now().minusDays(Math.max(1, Math.min(3650, parseDays(range))) - 1L);
        StringBuilder where = new StringBuilder(" WHERE ActivityDate>=?");
        List<Object> params = new ArrayList<>(List.of(from.atStartOfDay()));
        if (search != null && !search.isBlank()) {
            where.append(" AND (CAST(ActivityDate AS CHAR) LIKE ? OR Kind LIKE ?)");
            String pattern = "%" + search.trim() + "%";
            params.add(pattern); params.add(pattern);
        }
        String activityUnion = "SELECT DATE(LogDate) ActivityDate,'habit' Kind FROM habitlogs " +
                "UNION ALL SELECT DATE(EntryDate) ActivityDate,'journal' Kind FROM journalentries " +
                "UNION ALL SELECT DATE(StartTime) ActivityDate,'event' Kind FROM calendarevents";
        if (kind != null && !kind.equalsIgnoreCase("all")) { where.append(" AND Kind=?"); params.add(kind.toLowerCase(Locale.ROOT)); }
        List<Object> countParams = new ArrayList<>(params);
        Long total = database.queryForObject("SELECT COUNT(*) FROM (SELECT ActivityDate,Kind FROM (" + activityUnion + ") raw_activity"
                + where + " GROUP BY ActivityDate,Kind) grouped_activity", Long.class, countParams.toArray());
        params.add(safeSize); params.add(safePage * safeSize);
        List<ActivityRow> rows = database.query("SELECT ActivityDate,Kind,COUNT(*) AS ActivityCount "
                        + "FROM (" + activityUnion + ") raw_activity" + where + " GROUP BY ActivityDate,Kind "
                        + "ORDER BY ActivityDate DESC,Kind LIMIT ? OFFSET ?",
                (row, index) -> new ActivityRow(row.getDate("ActivityDate").toLocalDate(), row.getString("Kind"),
                        row.getLong("ActivityCount")),
                params.toArray());
        return new ActivityPage(rows, total == null ? 0 : total, safePage, safeSize);
    }

    private static int parseDays(String range) {
        try { return Integer.parseInt(range); }
        catch (NumberFormatException ignored) { return 30; }
    }

    public record ActivityRow(java.time.LocalDate date, String type, long count) { }
    public record ActivityPage(List<ActivityRow> content, long totalElements, int page, int size) { }
}
