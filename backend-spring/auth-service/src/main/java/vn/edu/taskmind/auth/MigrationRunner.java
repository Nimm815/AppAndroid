package vn.edu.taskmind.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("migrate")
public class MigrationRunner implements ApplicationRunner {
    private final ConfigurableApplicationContext context;
    public MigrationRunner(ConfigurableApplicationContext context) { this.context = context; }
    @Override public void run(ApplicationArguments args) {
        System.out.println("Flyway migration completed; JPA schema validation passed.");
        context.close();
    }
}
