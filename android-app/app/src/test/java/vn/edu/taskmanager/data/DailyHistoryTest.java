package vn.edu.taskmanager.data;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.Assert.assertEquals;

public class DailyHistoryTest {
    @Test public void reconcileIncludesOnlyScheduledHabitDates() {
        LocalStore.Entry habit = new LocalStore.Entry();
        habit.id = "habit-a";
        habit.title = "Read";
        habit.created = "2026-10-19";
        habit.type = "habit";
        habit.scheduleConfigured = true;
        habit.weekdays = (1 << 0) | (1 << 2) | (1 << 4);

        java.util.List<DailyHistory.Row> rows = DailyHistory.reconcile(new ArrayList<>(),
                Collections.emptyList(), Collections.singletonList(habit), "2026-10-25");

        assertEquals(3, rows.size());
        assertEquals("2026-10-23", rows.get(0).date);
        assertEquals("2026-10-21", rows.get(1).date);
        assertEquals("2026-10-19", rows.get(2).date);
    }
}
