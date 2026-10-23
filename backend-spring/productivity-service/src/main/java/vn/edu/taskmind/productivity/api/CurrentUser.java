package vn.edu.taskmind.productivity.api;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
/** All data APIs require a verified Auth session. */
@Component @Profile("dev")
public class CurrentUser {
    private final NativeIdentity identity;
    public CurrentUser(Environment environment, NativeIdentity identity) {
        this.identity=identity;
        if (environment.matchesProfiles("prod", "production")) throw new IllegalStateException("Development identity forbidden in production");
    }
    public int id() { return identity.id(); }
}
