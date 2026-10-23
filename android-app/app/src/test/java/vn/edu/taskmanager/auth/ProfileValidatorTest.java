package vn.edu.taskmanager.auth;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileValidatorTest {
    private ProfileModels.UpdateRequest valid() {
        return new ProfileModels.UpdateRequest("Nguyễn An", "an@example.com",
                "0912345678", "1998-04-12", "OTHER");
    }

    @Test public void acceptsValidProfileAndOptionalPhone() {
        assertNull(ProfileValidator.validate(valid()));
        ProfileModels.UpdateRequest request = valid();
        request.phoneNumber = "";
        request.birthDate = "";
        request.gender = "";
        assertNull(ProfileValidator.validate(request));
    }

    @Test public void rejectsInvalidEmailPhoneAndFutureDate() {
        ProfileModels.UpdateRequest request = valid();
        request.email = "invalid";
        assertTrue(ProfileValidator.validate(request).contains("Email"));
        request = valid();
        request.phoneNumber = "123";
        assertTrue(ProfileValidator.validate(request).contains("Số điện thoại"));
        request = valid();
        request.birthDate = java.time.LocalDate.now().plusDays(1).toString();
        assertTrue(ProfileValidator.validate(request).contains("tương lai"));
    }
}
