package vn.edu.taskmind.productivity;

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
                + "/taskmind_productivity_db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&sslMode=DISABLED&allowPublicKeyRetrieval=true");
        properties.add("spring.datasource.username", () -> required("MYSQL_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> required("MYSQL_TEST_PASSWORD"));
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test environment: " + name);
        return value;
    }
    @Test void schemaMatchesJpaAndHasNoForeignKeysAcrossServices() {
        assertEquals(9, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND referenced_table_schema IS NOT NULL AND referenced_table_schema <> table_schema", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class));
    }
    @Test void scheduleHistoryAndLaterHabitMigrationsAreApplied() {
        assertEquals("5", jdbc.queryForObject("SELECT MAX(version) FROM flyway_schema_history WHERE success = 1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'habit_schedule_versions'", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'habit_schedule_versions'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'habit_schedule_versions' AND constraint_name = 'fk_habit_schedule' AND constraint_type = 'FOREIGN KEY'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'habit_schedule_versions' AND index_name = 'uq_habit_schedule_day' AND non_unique = 0", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'habits' AND column_name IN ('TargetUnit', 'ReminderTime', 'ReminderEnabled')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'device_workspaces'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'device_records'", Integer.class));
    }
    @Test void dailyLogsAreUniquePerHabitAndHistoryCannotBeCascadeDeleted() {
        jdbc.update("INSERT INTO habits (HabitID,UserID,HabitName,Frequency) VALUES (901,100,'Read','DAILY'), (902,100,'Run','DAILY')");
        jdbc.update("INSERT INTO habitlogs (HabitID,LogDate,Status,ActualCount) VALUES (901,'2026-10-22','DONE',1), (902,'2026-10-22','PENDING',0)");
        assertSqlError(1062, () -> jdbc.update("INSERT INTO habitlogs (HabitID,LogDate,Status) VALUES (901,'2026-10-22','PENDING')"));
        assertSqlError(1451, () -> jdbc.update("DELETE FROM habits WHERE HabitID=901"));
        assertSqlError(3819, () -> jdbc.update("UPDATE habits SET TargetCount=0 WHERE HabitID=901"));
    }
    @Test void reminderTargetsAndEventTimesAreValidated() {
        assertSqlError(3819, () -> jdbc.update("INSERT INTO reminders (UserID,RemindAt) VALUES (100,'2026-10-22 12:00:00')"));
        assertSqlError(3819, () -> jdbc.update("INSERT INTO calendarevents (UserID,Title,StartTime,EndTime) VALUES (100,'Invalid','2026-10-22 12:00:00','2026-10-22 11:00:00')"));
        // TaskID is a logical reference to task-service: deliberately no cross-database FK.
        jdbc.update("INSERT INTO reminders (UserID,TaskID,RemindAt) VALUES (100,999,'2026-10-22 12:00:00')");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reminders", Integer.class));
    }
    private void assertSqlError(int expectedCode, Runnable operation) {
        org.springframework.dao.DataAccessException error = assertThrows(
                org.springframework.dao.DataAccessException.class, operation::run);
        java.sql.SQLException sql = assertInstanceOf(java.sql.SQLException.class, error.getMostSpecificCause());
        assertEquals(expectedCode, sql.getErrorCode());
    }
}
