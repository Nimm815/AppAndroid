package vn.edu.taskmind.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.web-application-type=none")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
@Transactional
class DatabaseIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + required("MYSQL_TEST_PORT")
                + "/taskmind_task_db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&sslMode=DISABLED&allowPublicKeyRetrieval=true");
        properties.add("spring.datasource.username", () -> required("MYSQL_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> required("MYSQL_TEST_PASSWORD"));
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test environment: " + name);
        return value;
    }
    @Test void schemaMatchesJpaAndHasNoForeignKeysAcrossServices() {
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND referenced_table_schema IS NOT NULL AND referenced_table_schema <> table_schema", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class));
    }
    @Autowired vn.edu.taskmind.task.service.TaskListAssignmentService assignment;
    @Test void movePreservesTaskAndRejectsAnotherOwner() {
        jdbc.update("INSERT INTO tasks (TaskID, UserID, Title, Priority, Status, Progress) VALUES (901, 100, 'Test task', 'SCHEDULE', 'IN_PROGRESS', 50)");
        jdbc.update("INSERT INTO tasklists (ListID, UserID, ListName) VALUES (901,100,'A'), (902,100,'B'), (903,200,'Other owner')");
        assignment.move(100, 901, 901, 0);
        assignment.move(100, 901, 902, 1);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tasklistitems WHERE ListID=901", Integer.class));
        assertEquals(902, jdbc.queryForObject("SELECT ListID FROM tasklistitems WHERE TaskID=901", Integer.class));
        assertEquals(50, jdbc.queryForObject("SELECT Progress FROM tasks WHERE TaskID=901", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> assignment.move(100, 901, 903, 0));
        assertEquals(902, jdbc.queryForObject("SELECT ListID FROM tasklistitems WHERE TaskID=901", Integer.class));
    }
    @Test void uniqueMembershipAndProgressChecksAreEnforced() {
        jdbc.update("INSERT INTO tasks (TaskID, UserID, Title, Priority, Status) VALUES (911,100,'Test','SCHEDULE','PENDING')");
        jdbc.update("INSERT INTO tasklists (ListID, UserID, ListName) VALUES (911,100,'A'), (912,100,'B')");
        jdbc.update("INSERT INTO tasklistitems (ListID,TaskID) VALUES (911,911)");
        assertSqlError(1062, () -> jdbc.update("INSERT INTO tasklistitems (ListID,TaskID) VALUES (912,911)"));
        assertSqlError(3819, () -> jdbc.update("UPDATE tasks SET Progress=101 WHERE TaskID=911"));
        jdbc.update("DELETE FROM tasklists WHERE ListID=911");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE TaskID=911", Integer.class));
    }
    private void assertSqlError(int expectedCode, Runnable operation) {
        org.springframework.dao.DataAccessException error = assertThrows(
                org.springframework.dao.DataAccessException.class, operation::run);
        java.sql.SQLException sql = assertInstanceOf(java.sql.SQLException.class, error.getMostSpecificCause());
        assertEquals(expectedCode, sql.getErrorCode());
    }
}
