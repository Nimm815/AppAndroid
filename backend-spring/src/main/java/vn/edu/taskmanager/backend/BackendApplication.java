package vn.edu.taskmanager.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class BackendApplication {
    private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);
    private final JdbcTemplate jdbcTemplate;

    public BackendApplication(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logDatabaseConnection() {
        String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        System.out.println("BACKEND CONNECTED TO MYSQL SUCCESSFULLY | Database: " + databaseName);
    }
}
