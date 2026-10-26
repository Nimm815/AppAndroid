package vn.edu.taskmind.productivity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Profile("migrate")
public class MigrationRunner implements ApplicationRunner {
    private final ConfigurableApplicationContext context;
    private final String ddlAuto;
    public MigrationRunner(ConfigurableApplicationContext context,
                           @Value("${spring.jpa.hibernate.ddl-auto:validate}") String ddlAuto) {
        this.context = context;
        this.ddlAuto = ddlAuto;
    }
    @Override public void run(ApplicationArguments args) {
        if ("validate".equalsIgnoreCase(ddlAuto)) {
            System.out.println("Flyway migration completed; JPA schema validation passed.");
        } else {
            System.out.println("Flyway migration completed; Hibernate ddl-auto=" + ddlAuto + ".");
        }
        context.close();
    }
}
