package vn.edu.taskmanager;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.widget.ViewFlipper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import vn.edu.taskmanager.data.LocalStore;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WorkspaceTest {
    @Test public void journalAndDailyHabitHistorySurviveReloadAndEdits() {
        Context base = ApplicationProvider.getApplicationContext();
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("test-workspace-" + name, mode);
            }
        };
        SharedPreferences prefs = isolated.getSharedPreferences("personal_workspace", 0);
        prefs.edit().clear().commit();
        try {
            LocalStore store = new LocalStore(isolated);
            LocalStore.Entry note = new LocalStore.Entry();
            note.type = "note"; note.title = "Ghi chú thử"; note.body = "Nội dung tiếng Việt";
            note.date = LocalStore.today(); note.time = "09:00"; note.bookmarked = true;
            store.save(note);
            LocalStore.Entry habit = new LocalStore.Entry();
            habit.type = "habit"; habit.title = "Đọc sách"; habit.body = "Mỗi ngày";
            habit.date = LocalStore.today(); habit.time = "09:00"; store.save(habit);
            store.checkHabit(habit, "2026-10-04", true);
            store.checkHabit(habit, "2026-10-05", true);
            store.checkHabit(habit, "2026-10-05", true);
            store = new LocalStore(isolated);
            assertTrue(store.find(note.id).bookmarked);
            assertEquals("Nội dung tiếng Việt", store.find(note.id).body);
            assertEquals(2, store.find(habit.id).days.size());
            habit = store.find(habit.id); habit.title = "Đọc Java"; store.save(habit);
            assertEquals(2, new LocalStore(isolated).find(habit.id).days.size());
            store.checkHabit(habit, "2026-10-05", false);
            assertEquals("2026-10-04", store.find(habit.id).days.get(0));
            store.delete(note.id);
            assertNull(store.find(note.id));
            assertEquals(1, store.entries("habit").size());
        } finally { prefs.edit().clear().commit(); }
    }

    @Test public void everyScreenOpensAndSelectedTabSurvivesRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.navJournal).performClick();
                assertEquals(1, ((ViewFlipper) activity.findViewById(R.id.screens)).getDisplayedChild());
                activity.findViewById(R.id.navCalendar).performClick();
                assertEquals(2, ((ViewFlipper) activity.findViewById(R.id.screens)).getDisplayedChild());
                assertTrue(((android.widget.LinearLayout) activity.findViewById(R.id.calendarGrid)).getChildCount() == 24);
                activity.findViewById(R.id.navSettings).performClick();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(3, ((ViewFlipper) activity.findViewById(R.id.screens)).getDisplayedChild());
                activity.findViewById(R.id.settingsTimer).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
                assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("timer"));
                ((TimerDialog) activity.getSupportFragmentManager().findFragmentByTag("timer")).dismissNow();
                activity.findViewById(R.id.settingsReport).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
                assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("report"));
                ((ReportDialog) activity.getSupportFragmentManager().findFragmentByTag("report")).dismissNow();
                activity.findViewById(R.id.navTasks).performClick();
            });
        }
    }
    @Test public void noteDraftAndRunningTimerSurviveRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.navJournal).performClick();
                activity.findViewById(R.id.addNote).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
                EntryEditorDialog editor = (EntryEditorDialog) activity.getSupportFragmentManager().findFragmentByTag("entry_editor");
                ((android.widget.EditText) editor.requireDialog().findViewById(R.id.entryTitle)).setText("Bản nháp chưa lưu");
                ((android.widget.EditText) editor.requireDialog().findViewById(R.id.entryBody)).setText("Nội dung khi xoay máy");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                EntryEditorDialog editor = (EntryEditorDialog) activity.getSupportFragmentManager().findFragmentByTag("entry_editor");
                assertNotNull(editor);
                assertEquals("Bản nháp chưa lưu", ((android.widget.EditText) editor.requireDialog().findViewById(R.id.entryTitle)).getText().toString());
                assertEquals("Nội dung khi xoay máy", ((android.widget.EditText) editor.requireDialog().findViewById(R.id.entryBody)).getText().toString());
                editor.dismissNow();
                activity.findViewById(R.id.navTasks).performClick();
                activity.findViewById(R.id.openTimer).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
                TimerState model = new androidx.lifecycle.ViewModelProvider(activity).get(TimerState.class);
                model.reset(); model.countdown = false; model.elapsed = 3000; model.start();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                TimerState model = new androidx.lifecycle.ViewModelProvider(activity).get(TimerState.class);
                assertTrue(model.running); assertTrue(model.elapsedNow() >= 3000);
                TimerDialog timer = (TimerDialog) activity.getSupportFragmentManager().findFragmentByTag("timer");
                assertNotNull(timer);
                model.reset(); timer.dismissNow();
            });
        }
    }
}
