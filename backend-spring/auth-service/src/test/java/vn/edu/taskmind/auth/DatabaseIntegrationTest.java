package vn.edu.taskmind.auth;

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
                + "/taskmind_auth_db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&sslMode=DISABLED&allowPublicKeyRetrieval=true");
        properties.add("spring.datasource.username", () -> required("MYSQL_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> required("MYSQL_TEST_PASSWORD"));
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test environment: " + name);
        return value;
    }
    @Test void schemaMatchesJpaAndHasNoForeignKeysAcrossServices() {
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND referenced_table_schema IS NOT NULL AND referenced_table_schema <> table_schema", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class));
    }
    @Test void duplicateEmailAndInvalidFlagsAreRejected() {
        jdbc.update("INSERT INTO users (FullName, Email, PasswordHash, Role) VALUES ('Test', 'test@example.test', 'test-only-hash', 'USER')");
        assertSqlError(1062, () -> jdbc.update(
            "INSERT INTO users (FullName, Email, PasswordHash, Role) VALUES ('Other', 'TEST@example.test', 'test-only-hash', 'USER')"));
        assertSqlError(3819, () -> jdbc.update("UPDATE users SET IsLocked = 2"));
    }
    private void assertSqlError(int expectedCode, Runnable operation) {
        org.springframework.dao.DataAccessException error = assertThrows(
                org.springframework.dao.DataAccessException.class, operation::run);
        java.sql.SQLException sql = assertInstanceOf(java.sql.SQLException.class, error.getMostSpecificCause());
        assertEquals(expectedCode, sql.getErrorCode());
    }
}
