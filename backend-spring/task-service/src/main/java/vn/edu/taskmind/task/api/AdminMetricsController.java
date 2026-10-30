package vn.edu.taskmind.task.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/** Admin read-only summaries are served by the Task service and its own database. */
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
        long count = database.queryForObject("SELECT COUNT(*) FROM tasks", Long.class);
        return Map.of("taskCount", count);
    }

    @GetMapping("/user-task-counts")
    public Map<Integer, Long> userTaskCounts(HttpServletRequest request) {
        requireLocal(request);
        Map<Integer, Long> result = new LinkedHashMap<>();
        database.query("SELECT UserID,COUNT(*) AS TaskCount FROM tasks GROUP BY UserID ORDER BY UserID",
                (RowCallbackHandler) row -> {
                    result.put(row.getInt("UserID"), row.getLong("TaskCount"));
                });
        return result;
    }
}
