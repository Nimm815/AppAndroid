package vn.edu.taskmind.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ProfileRulesTest {
    @Test void normalizesNamesAndEmail() {
        assertEquals("Nguyễn An", ProfileRules.fullName(" Nguyễn An "));
        assertEquals("user@example.com", ProfileRules.email(" User@Example.COM "));
        assertThrows(ResponseStatusException.class, () -> ProfileRules.fullName(" "));
        assertThrows(ResponseStatusException.class, () -> ProfileRules.email("not-an-email"));
    }

    @Test void validatesVietnamesePhoneAndOptionalFields() {
        assertEquals("0912345678", ProfileRules.phone("0912345678"));
        assertEquals("+84912345678", ProfileRules.phone("+84912345678"));
        assertNull(ProfileRules.phone(""));
        assertThrows(ResponseStatusException.class, () -> ProfileRules.phone("12345"));
    }

    @Test void rejectsFutureBirthdaysAndUnknownGenders() {
        assertNull(ProfileRules.birthDate(""));
        assertNull(ProfileRules.gender(null));
        assertEquals("FEMALE", ProfileRules.gender("female"));
        assertThrows(ResponseStatusException.class,
                () -> ProfileRules.birthDate(LocalDate.now().plusDays(1).toString()));
        assertThrows(ResponseStatusException.class, () -> ProfileRules.gender("unspecified"));
    }
}
