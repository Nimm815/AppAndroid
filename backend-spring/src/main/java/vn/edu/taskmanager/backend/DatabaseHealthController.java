package vn.edu.taskmanager.backend;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseHealthController {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthController(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @GetMapping("/api/health/database")
    public Map<String, Object> checkDatabase() {
        String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "connected", "database", databaseName, "check", one);
    }
}
