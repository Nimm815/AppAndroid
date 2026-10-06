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
    @Test public void prioritySelectionSurvivesRotationAndColorsScheduledTaskOnly() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                TaskEditorDialog.newInstance(0, "Test màu", LocalStore.today(), 1)
                        .show(activity.getSupportFragmentManager(), "task_editor");
                activity.getSupportFragmentManager().executePendingTransactions();
                TaskEditorDialog editor = (TaskEditorDialog) activity.getSupportFragmentManager().findFragmentByTag("task_editor");
                ((android.widget.Spinner) editor.requireDialog().findViewById(R.id.taskPriority)).setSelection(3);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                TaskEditorDialog editor = (TaskEditorDialog) activity.getSupportFragmentManager().findFragmentByTag("task_editor");
                assertEquals(3, ((android.widget.Spinner) editor.requireDialog().findViewById(R.id.taskPriority)).getSelectedItemPosition());
                editor.dismissNow();
                TaskAdapter adapter = new TaskAdapter(activity);
                android.widget.FrameLayout parent = new android.widget.FrameLayout(activity);
                TaskAdapter.Holder holder = adapter.onCreateViewHolder(parent, 0);
                vn.edu.taskmanager.data.Task task = new vn.edu.taskmanager.data.Task("Màu");
                task.scheduledDate = LocalStore.today();
                java.util.Set<Integer> colors = new java.util.HashSet<>();
                for (int priority = 1; priority <= 4; priority++) {
                    task.priority = priority;
                    adapter.submitList(java.util.Collections.singletonList(task));
                    adapter.onBindViewHolder(holder, 0);
                    assertEquals(android.view.View.VISIBLE, holder.priorityStripe.getVisibility());
                    int color = ((android.graphics.drawable.ColorDrawable) holder.priorityStripe.getBackground()).getColor();
                    assertEquals(TaskPriority.color(priority), color); colors.add(color);
                }
                assertEquals(4, colors.size());
                task.scheduledDate = "";
                adapter.onBindViewHolder(holder, 0);
                assertEquals(android.view.View.INVISIBLE, holder.priorityStripe.getVisibility());
            });
        }
    }

    @Test public void journalReaderPreservesParagraphsAndOpensEditorAfterRotation() {
        LocalStore store = new LocalStore(ApplicationProvider.getApplicationContext());
        LocalStore.Entry note = new LocalStore.Entry();
        note.type = "note"; note.title = "test-journal-book";
        note.body = "Đoạn thứ nhất.\n\nĐoạn thứ hai.\nDòng tiếp theo.";
        note.label = "Học tập"; note.date = LocalStore.today(); note.time = "09:00";
        store.save(note);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectWorkspaceTab(1);
                JournalReaderDialog.create(note.id).show(activity.getSupportFragmentManager(), "journal_reader");
                activity.getSupportFragmentManager().executePendingTransactions();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                JournalReaderDialog reader = (JournalReaderDialog) activity.getSupportFragmentManager().findFragmentByTag("journal_reader");
                assertNotNull(reader);
                android.widget.TextView body = reader.requireDialog().findViewById(R.id.readerBody);
                assertEquals(note.body, body.getText().toString());
                reader.requireDialog().findViewById(R.id.readerBookmark).performClick();
                assertTrue(store.find(note.id).bookmarked);
                reader.requireDialog().findViewById(R.id.readerEdit).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
                EntryEditorDialog editor = (EntryEditorDialog) activity.getSupportFragmentManager().findFragmentByTag("entry_editor");
                assertNotNull(editor);
                android.widget.EditText input = editor.requireDialog().findViewById(R.id.entryBody);
                assertEquals(note.body, input.getText().toString());
                editor.dismissNow();
            });
        } finally { store.delete(note.id); }
    }

    @Test public void taskSchedulingRejectsNewPastDatesButKeepsExistingHistory() {
        assertFalse(TaskSchedule.canSaveDate("2026-10-05", "", "2026-10-06"));
        assertFalse(TaskSchedule.canSaveDate("2026-10-04", "2026-10-05", "2026-10-06"));
        assertTrue(TaskSchedule.canSaveDate("2026-10-05", "2026-10-05", "2026-10-06"));
        assertTrue(TaskSchedule.canSaveDate("2026-10-06", "", "2026-10-06"));
        assertTrue(TaskSchedule.canSaveDate("2026-10-07", "", "2026-10-06"));
        assertTrue(TaskSchedule.canSaveDate("", "2026-10-05", "2026-10-06"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                java.util.Calendar yesterday = java.util.Calendar.getInstance();
                yesterday.add(java.util.Calendar.DAY_OF_MONTH, -1);
                TaskEditorDialog editor = TaskEditorDialog.newInstance(0, "", LocalStore.day(yesterday.getTime()));
                editor.show(activity.getSupportFragmentManager(), "task_editor");
                activity.getSupportFragmentManager().executePendingTransactions();
                android.widget.Button date = editor.requireDialog().findViewById(R.id.taskDate);
                assertEquals(MainActivity.displayDate(LocalStore.today()), date.getText().toString());
                editor.dismissNow();
            });
        }
    }

    @Test public void dailyOverviewKeepsTasksAndHabitsAfterSavingAndChangingDay() {
        Context context = ApplicationProvider.getApplicationContext();
        LocalStore store = new LocalStore(context);
        LocalStore.Entry habit = new LocalStore.Entry();
        habit.type = "habit"; habit.title = "test-daily-overview-habit";
        habit.body = ""; habit.date = LocalStore.today(); habit.time = "09:00";
        store.save(habit);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.entrySaved(habit);
                assertDailyOverview(activity, habit.title);
                java.util.Calendar tomorrow = java.util.Calendar.getInstance();
                tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
                activity.taskSaved(LocalStore.day(tomorrow.getTime()));
                assertDailyOverview(activity, habit.title);
                android.widget.CheckBox check = findHabitCheck(activity, habit.title);
                assertFalse(check.isChecked());
                assertFalse(check.isEnabled());
                activity.taskSaved("");
                assertDailyOverview(activity, habit.title);
                check = findHabitCheck(activity, habit.title);
                assertTrue(check.isEnabled());
                check.performClick();
                assertTrue(store.find(habit.id).days.contains(LocalStore.today()));
                activity.taskSaved(LocalStore.day(tomorrow.getTime()));
                assertFalse(findHabitCheck(activity, habit.title).isChecked());
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertDailyOverview(activity, habit.title));
        } finally { store.delete(habit.id); }
    }

    private void assertDailyOverview(MainActivity activity, String title) {
        assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.taskListContainer).getVisibility());
        assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.habitScroll).getVisibility());
        assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.habitHeading).getVisibility());
        assertNotNull(findHabitCheck(activity, title));
    }

    private android.widget.CheckBox findHabitCheck(MainActivity activity, String title) {
        android.widget.LinearLayout rows = activity.findViewById(R.id.habitEntries);
        for (int i = 0; i < rows.getChildCount(); i++) {
            android.view.View row = rows.getChildAt(i);
            if (!(row instanceof android.view.ViewGroup)) continue;
            android.view.ViewGroup card = (android.view.ViewGroup) row;
            for (int j = 0; j < card.getChildCount(); j++) {
                android.view.View child = card.getChildAt(j);
                if (child instanceof android.widget.CheckBox
                        && ("Hoàn thành " + title).contentEquals(child.getContentDescription()))
                    return (android.widget.CheckBox) child;
            }
        }
        throw new AssertionError("Không thấy thói quen " + title);
    }

    @Test public void switchingDatesFiltersTasksAndAllTasksStillKeepsHistory() {
        Context context = ApplicationProvider.getApplicationContext();
        vn.edu.taskmanager.data.TaskDao dao = vn.edu.taskmanager.data.TaskDatabase.getInstance(context).taskDao();
        vn.edu.taskmanager.data.Task first = new vn.edu.taskmanager.data.Task("test-date-flow-5");
        first.scheduledDate = "2100-01-05"; first.completed = true;
        vn.edu.taskmanager.data.Task second = new vn.edu.taskmanager.data.Task("test-date-flow-6");
        second.scheduledDate = "2100-01-06";
        long firstId = dao.insert(first), secondId = dao.insert(second);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.taskSaved("2100-01-05"));
            awaitTaskTitles(scenario, "test-date-flow-5", "test-date-flow-6");
            scenario.onActivity(activity -> activity.taskSaved("2100-01-06"));
            awaitTaskTitles(scenario, "test-date-flow-6", "test-date-flow-5");
            scenario.onActivity(activity -> activity.useTaskFilter(0, "test-date-flow-", ""));
            awaitTaskTitles(scenario, "test-date-flow-5", "");
            awaitTaskTitles(scenario, "test-date-flow-6", "");
            scenario.recreate();
            awaitTaskTitles(scenario, "test-date-flow-5", "");
            assertTrue(dao.findById(firstId).completed);
            assertEquals("2100-01-05", dao.findById(firstId).scheduledDate);
        } finally { dao.delete(firstId); dao.delete(secondId); }
    }

    private void awaitTaskTitles(ActivityScenario<MainActivity> scenario, String included, String excluded) {
        boolean[] matches = {false};
        long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
        do {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.taskList);
                androidx.recyclerview.widget.ConcatAdapter combined = (androidx.recyclerview.widget.ConcatAdapter) list.getAdapter();
                TaskAdapter tasks = (TaskAdapter) combined.getAdapters().get(0);
                boolean hasIncluded = false, hasExcluded = false;
                for (vn.edu.taskmanager.data.Task task : tasks.getCurrentList()) {
                    hasIncluded |= task.title.equals(included);
                    hasExcluded |= !excluded.isEmpty() && task.title.equals(excluded);
                }
                matches[0] = hasIncluded && !hasExcluded;
            });
            if (matches[0]) return;
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.elapsedRealtime() < deadline);
        assertTrue("Expected " + included + ", excluded " + excluded, matches[0]);
    }

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
            note.label = "Học tập";
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
            assertEquals("Học tập", store.find(note.id).label);
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
                TimerDialog openedTimer = (TimerDialog) activity.getSupportFragmentManager().findFragmentByTag("timer");
                openedTimer.requireDialog().findViewById(R.id.stopwatchMode).performClick();
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
