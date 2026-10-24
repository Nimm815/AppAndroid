package vn.edu.taskmanager;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import vn.edu.taskmanager.data.LocalStore;
import vn.edu.taskmanager.data.Reminder;
import vn.edu.taskmanager.data.Task;
import vn.edu.taskmanager.AuthActivity;
import vn.edu.taskmanager.auth.AccountSession;
import vn.edu.taskmanager.auth.AuthApi;
import vn.edu.taskmanager.reminder.ReminderScheduler;
import vn.edu.taskmanager.util.VietnameseUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Điều phối các màn hình: Trang chủ (Task/Habit), Nhật ký (UC09), Lịch (UC10), Nhắc việc (UC11), Báo cáo (UC12), Cài đặt.
 */
public class WorkspaceController {
    private final MainActivity activity;
    private final LocalStore store;
    private final vn.edu.taskmanager.data.DailyHistory history;

    // UC09 Nhật ký
    private String journalDateFrom = "";
    private String journalDateTo = "";
    private String journalHabitId = "";
    private boolean bookmarkedOnly;
    private String noteQuery = "";
    private int journalLimit = 30;
    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable searchDebounceRunnable;

    // UC10 & UC11 Lịch & Nhắc việc
    private int calendarSubTab = 0; // 0: Lịch, 1: Nhắc việc
    private final Calendar calendarMonth = Calendar.getInstance();
    private final Calendar calendarDay = Calendar.getInstance();

    // Trang chủ
    private final Calendar selected = Calendar.getInstance();
    private String lastDay = LocalStore.today();
    private final SharedPreferences preferences;
    private final Locale vietnamese = new Locale("vi", "VN");
    private int tab;
    private boolean habitsOnly;
    private boolean collapsed;
    private List<Task> tasks = new ArrayList<>();
    private final vn.edu.taskmanager.cloud.CloudRepository habitCloud;
    private boolean habitFetchInProgress;
    private long habitMutationVersion;

    public WorkspaceController(MainActivity activity, Bundle state) {
        this.activity = activity;
        store = new LocalStore(activity);
        history = new vn.edu.taskmanager.data.DailyHistory(activity);
        preferences = activity.getSharedPreferences("workspace_settings", 0);
        habitCloud = vn.edu.taskmanager.BuildConfig.HABIT_URL.isEmpty() ? null
                : new vn.edu.taskmanager.cloud.CloudRepository(vn.edu.taskmanager.BuildConfig.TASK_URL,
                vn.edu.taskmanager.BuildConfig.HABIT_URL);

        if (state != null) {
            tab = state.getInt("tab", 0);
            calendarSubTab = state.getInt("calendarSubTab", 0);
            habitsOnly = state.getBoolean("habitsOnly", false);
            bookmarkedOnly = state.getBoolean("bookmarkedOnly", false);
            noteQuery = state.getString("noteQuery", "");
            journalDateFrom = state.getString("journalDateFrom", "");
            journalDateTo = state.getString("journalDateTo", "");
            journalHabitId = state.getString("journalHabitId", "");
            journalLimit = state.getInt("journalLimit", 30);
            collapsed = state.getBoolean("collapsed", false);
            selected.setTimeInMillis(state.getLong("selectedDay", selected.getTimeInMillis()));
            calendarDay.setTimeInMillis(state.getLong("calendarDay", calendarDay.getTimeInMillis()));
            calendarMonth.setTimeInMillis(calendarDay.getTimeInMillis());
        }

        initNavigation();
        initJournalViews();
        initCalendarViews();
        initSettingsViews();

        selectTab(tab);
    }

    public void refreshHabitsFromServer() {
        if (habitCloud == null || habitFetchInProgress) return;
        habitFetchInProgress = true;
        long requestedMutationVersion = habitMutationVersion;
        habitCloud.request(habitCloud.habits.habits(),
                new vn.edu.taskmanager.cloud.CloudRepository.Result<List<vn.edu.taskmanager.cloud.CloudModels.Habit>>() {
            @Override public void success(List<vn.edu.taskmanager.cloud.CloudModels.Habit> values) {
                habitFetchInProgress = false;
                if (requestedMutationVersion != habitMutationVersion) {
                    refreshHabitsFromServer();
                    return;
                }
                Set<String> retained = new HashSet<>();
                for (vn.edu.taskmanager.cloud.CloudModels.Habit value : values) {
                    retained.add(String.valueOf(value.id));
                    cacheRemoteHabit(value);
                }
                for (LocalStore.Entry entry : store.entries("habit")) {
                    if ("server_api".equals(entry.sourceType) && !retained.contains(entry.sourceId)) {
                        cancelHabitReminder(entry.sourceId);
                        store.delete(entry.id);
                    }
                }
                refresh();
            }
            @Override public void error(String message) {
                habitFetchInProgress = false;
                Toast.makeText(activity, "Không tải được thói quen từ máy chủ: " + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    public void cacheRemoteHabit(vn.edu.taskmanager.cloud.CloudModels.Habit remote) {
        String id = "api-habit-" + remote.id;
        LocalStore.Entry entry = store.find(id);
        if (entry == null) entry = new LocalStore.Entry();
        entry.id = id;
        entry.type = "habit";
        entry.sourceType = "server_api";
        entry.sourceId = String.valueOf(remote.id);
        entry.title = remote.name;
        entry.body = remote.description == null ? "" : remote.description;
        entry.icon = remote.icon == null || remote.icon.isEmpty() ? "🌱" : remote.icon;
        entry.color = remote.color == null || remote.color.isEmpty() ? "#5B6FDE" : remote.color;
        entry.created = remote.startDate == null ? LocalStore.today() : remote.startDate;
        entry.date = entry.created;
        entry.weekdays = remote.weekdays;
        entry.scheduleConfigured = remote.scheduleConfigured;
        entry.targetCount = Math.max(1, remote.targetCount);
        entry.targetUnit = remote.targetUnit == null || remote.targetUnit.isEmpty() ? "lần" : remote.targetUnit;
        entry.currentStreak = remote.currentStreak;
        entry.longestStreak = remote.longestStreak;
        entry.completedDays = remote.completedDays;
        entry.scheduledDays = remote.scheduledDays;
        entry.completionRate = remote.completionRate;
        entry.reminderEnabled = remote.reminderEnabled;
        entry.reminderTime = remote.reminderTime == null ? "" : remote.reminderTime.substring(0, 5);
        int todayProgress = Math.min(100, remote.actualCountToday * 100 / entry.targetCount);
        entry.dailyProgress.put(LocalStore.today(), todayProgress);
        entry.days.remove(LocalStore.today());
        if (remote.completedToday) entry.days.add(LocalStore.today());
        store.save(entry);
        syncHabitReminder(remote);
    }

    private void syncHabitReminder(vn.edu.taskmanager.cloud.CloudModels.Habit habit) {
        LocalStore store = new LocalStore(activity);
        String reminderId = "habit-" + habit.id;
        Reminder old = store.findReminder(reminderId);
        if (!habit.reminderEnabled || habit.reminderTime == null) {
            if (old != null) {
                ReminderScheduler.cancel(activity, old);
                store.deleteReminder(reminderId);
            }
            return;
        }
        Reminder reminder = old == null ? new Reminder() : old;
        reminder.id = reminderId;
        reminder.title = habit.name;
        reminder.targetType = Reminder.TARGET_HABIT;
        reminder.targetId = String.valueOf(habit.id);
        reminder.targetName = habit.name;
        reminder.time = habit.reminderTime.substring(0, 5);
        reminder.repeatType = habit.weekdays == 127 ? Reminder.REPEAT_DAILY : Reminder.REPEAT_WEEKDAYS;
        reminder.isEnabled = true;
        reminder.isFromEvent = false;
        reminder.daysOfWeek = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            if ((habit.weekdays & (1 << index)) == 0) continue;
            reminder.daysOfWeek.add(index == 6 ? Calendar.SUNDAY : index + Calendar.MONDAY);
        }
        ReminderScheduler.schedule(activity, reminder);
        if (!ReminderScheduler.hasNotificationPermission(activity)) {
            new AlertDialog.Builder(activity).setTitle("Cần cấp quyền thông báo")
                    .setMessage("Đã lưu giờ nhắc. Hãy bật quyền thông báo để nhận nhắc việc.")
                    .setPositiveButton("Cài đặt", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                        activity.startActivity(intent);
                    }).setNegativeButton("Để sau", null).show();
        }
    }

    private void cancelHabitReminder(String habitId) {
        Reminder reminder = store.findReminder("habit-" + habitId);
        if (reminder != null) {
            ReminderScheduler.cancel(activity, reminder);
            store.deleteReminder(reminder.id);
        }
    }

    public void saveHabitToServer(Integer id, Map<String, Object> body,
            vn.edu.taskmanager.cloud.CloudRepository.Result<vn.edu.taskmanager.cloud.CloudModels.Habit> callback) {
        if (habitCloud == null) {
            callback.error("Bản ứng dụng chưa cấu hình Productivity API");
        } else if (id == null) habitCloud.request(habitCloud.habits.create(body), callback);
        else habitCloud.request(habitCloud.habits.update(id, body), callback);
    }

    public void onRemoteHabitSaved(vn.edu.taskmanager.cloud.CloudModels.Habit habit) {
        habitMutationVersion++;
        cacheRemoteHabit(habit);
        activity.showSelectedDay();
        selectTab(0);
    }

    public void close() {
        if (habitCloud != null) habitCloud.close();
    }

    private void initNavigation() {
        int[] nav = {R.id.navTasks, R.id.navJournal, R.id.navCalendar, R.id.navSettings};
        for (int i = 0; i < nav.length; i++) {
            final int index = i;
            activity.findViewById(nav[i]).setOnClickListener(v -> selectTab(index));
        }
    }

    private void initJournalViews() {
        // Toggle Bookmark nhanh
        click(R.id.journalBookmark, () -> {
            bookmarkedOnly = !bookmarkedOnly;
            journalLimit = 30;
            refreshJournal();
        });

        // Nút bộ lọc nâng cao (UC09: AND của Khoảng ngày, Thói quen, Đánh dấu)
        click(R.id.journalFilterBtn, this::showJournalFilterDialog);

        // Nút xóa bộ lọc
        click(R.id.journalClearFilters, () -> {
            journalDateFrom = "";
            journalDateTo = "";
            journalHabitId = "";
            bookmarkedOnly = false;
            noteQuery = "";
            journalLimit = 30;
            EditText searchEt = activity.findViewById(R.id.journalSearch);
            if (searchEt != null) searchEt.setText("");
            refreshJournal();
        });

        // Tìm kiếm (UC09: debounce 300ms, không dấu)
        click(R.id.journalSearchToggle, () -> {
            View searchContainer = activity.findViewById(R.id.journalSearchContainer);
            if (searchContainer != null) {
                int vis = searchContainer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
                searchContainer.setVisibility(vis);
                if (vis == View.VISIBLE) {
                    EditText search = activity.findViewById(R.id.journalSearch);
                    search.requestFocus();
                }
            }
        });

        View closeSearch = activity.findViewById(R.id.journalSearchClose);
        if (closeSearch != null) {
            closeSearch.setOnClickListener(v -> {
                EditText search = activity.findViewById(R.id.journalSearch);
                if (search != null) search.setText("");
                View searchContainer = activity.findViewById(R.id.journalSearchContainer);
                if (searchContainer != null) searchContainer.setVisibility(View.GONE);
                noteQuery = "";
                journalLimit = 30;
                refreshJournal();
            });
        }

        EditText search = activity.findViewById(R.id.journalSearch);
        if (search != null) {
            search.setText(noteQuery);
            search.addTextChangedListener(watcher(value -> {
                if (searchDebounceRunnable != null) {
                    searchDebounceHandler.removeCallbacks(searchDebounceRunnable);
                }
                searchDebounceRunnable = () -> {
                    noteQuery = value;
                    journalLimit = 30;
                    refreshJournal();
                };
                searchDebounceHandler.postDelayed(searchDebounceRunnable, 300);
            }));
        }

        // Tải thêm 30 mục
        click(R.id.journalLoadMore, () -> {
            journalLimit += 30;
            refreshJournal();
        });

        // Nút thêm ghi chú nhanh
        click(R.id.addJournalNote, () -> {
            if (activity.getSupportFragmentManager().findFragmentByTag("entry_editor") == null) {
                EntryEditorDialog.create("note", null, LocalStore.today())
                        .show(activity.getSupportFragmentManager(), "entry_editor");
            }
        });

        // Tùy chọn menu Nhật ký
        click(R.id.journalMore, () -> new AlertDialog.Builder(activity)
                .setTitle("Nhật ký hoạt động")
                .setItems(new String[]{"Thêm ghi chú", "Bộ lọc nâng cao", "Xóa toàn bộ bộ lọc"}, (dialog, which) -> {
                    if (which == 0) activity.findViewById(R.id.addJournalNote).performClick();
                    else if (which == 1) showJournalFilterDialog();
                    else activity.findViewById(R.id.journalClearFilters).performClick();
                }).show());
    }

    private void initCalendarViews() {
        // Chuyển đổi giữa 2 thẻ: Lịch và Nhắc việc
        Button btnTabCal = activity.findViewById(R.id.calendarTabCalendar);
        Button btnTabRem = activity.findViewById(R.id.calendarTabReminders);

        if (btnTabCal != null && btnTabRem != null) {
            btnTabCal.setOnClickListener(v -> {
                calendarSubTab = 0;
                updateCalendarTabButtons();
                refreshCalendar();
            });
            btnTabRem.setOnClickListener(v -> {
                calendarSubTab = 1;
                updateCalendarTabButtons();
                refreshCalendar();
            });
        }

        // Chuyển tháng trong lịch
        click(R.id.calendarPrevMonth, () -> {
            calendarMonth.add(Calendar.MONTH, -1);
            refreshCalendar();
        });
        click(R.id.calendarNextMonth, () -> {
            calendarMonth.add(Calendar.MONTH, 1);
            refreshCalendar();
        });
        click(R.id.calendarTodayBtn, () -> {
            calendarDay.setTime(new Date());
            calendarMonth.setTime(new Date());
            refreshCalendar();
        });

        // Nút thêm sự kiện & thêm nhắc việc
        click(R.id.addEvent, () -> {
            if (activity.getSupportFragmentManager().findFragmentByTag("event_editor") == null) {
                EventEditorDialog.create(null, day(calendarDay))
                        .show(activity.getSupportFragmentManager(), "event_editor");
            }
        });

        click(R.id.addReminder, () -> {
            if (activity.getSupportFragmentManager().findFragmentByTag("reminder_editor") == null) {
                ReminderEditorDialog.create(null)
                        .show(activity.getSupportFragmentManager(), "reminder_editor");
            }
        });

        click(R.id.calendarMenu, this::lists);
        click(R.id.calendarMore, () -> new AlertDialog.Builder(activity).setTitle("Lịch & Nhắc việc")
                .setItems(new String[]{"Thêm sự kiện mới", "Thêm nhắc việc mới", "Về hôm nay"}, (dialog, which) -> {
                    if (which == 0) activity.findViewById(R.id.addEvent).performClick();
                    else if (which == 1) activity.findViewById(R.id.addReminder).performClick();
                    else activity.findViewById(R.id.calendarTodayBtn).performClick();
                }).show());

        View openPerm = activity.findViewById(R.id.btnOpenNotificationSettings);
        if (openPerm != null) {
            openPerm.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                activity.startActivity(intent);
            });
        }
    }

    private void updateCalendarTabButtons() {
        Button btnTabCal = activity.findViewById(R.id.calendarTabCalendar);
        Button btnTabRem = activity.findViewById(R.id.calendarTabReminders);
        if (btnTabCal == null || btnTabRem == null) return;

        if (calendarSubTab == 0) {
            btnTabCal.setBackgroundResource(R.drawable.control_black);
            btnTabCal.setTextColor(Color.WHITE);
            btnTabRem.setBackgroundResource(R.drawable.control_gray);
            btnTabRem.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
        } else {
            btnTabRem.setBackgroundResource(R.drawable.control_black);
            btnTabRem.setTextColor(Color.WHITE);
            btnTabCal.setBackgroundResource(R.drawable.control_gray);
            btnTabCal.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
        }
    }

    private void initSettingsViews() {
        click(R.id.openTimer, this::timer);
        click(R.id.openReport, this::report);
        click(R.id.settingsTimer, this::timer);
        click(R.id.settingsReport, this::report);
        click(R.id.settingsProfile, () -> activity.startActivity(
                new Intent(activity, PersonalProfileActivity.class)));
        click(R.id.manageLists, this::manageCollections);
        click(R.id.settingsSearch, this::globalSearch);
        click(R.id.replayOnboarding, () -> activity.startActivity(
                new Intent(activity, OnboardingActivity.class)
                        .putExtra(OnboardingActivity.EXTRA_REPLAY, true)));
        click(R.id.help, () -> new AlertDialog.Builder(activity).setTitle(R.string.help)
                .setMessage("Công việc: chạm + để thêm; chạm tên để sửa/xóa, chạm ô bên phải để hoàn thành.\n\n"
                        + "Menu ☰: chọn công việc, thói quen, danh sách hoặc bộ lọc.\n\n"
                        + "Thói quen: chọn ngày rồi đánh dấu hoàn thành. Báo cáo tính theo kỳ đã kết thúc.\n\n"
                        + "Nhật ký: dòng thời gian hoạt động, tìm kiếm không dấu, 3 bộ lọc kết hợp và đánh dấu 1 chạm.\n\n"
                        + "Lịch: lịch tháng, kiểm tra trùng giờ, thêm/sửa/xóa sự kiện và lời nhắc kèm theo.\n\n"
                        + "Nhắc việc: quản lý chuông báo hằng ngày/thứ trong tuần, hỗ trợ khởi động lại máy.\n\n"
                        + "Báo cáo: 3 chỉ số bắt buộc, biểu đồ cột và xuất/chia sẻ file PDF chuẩn.")
                .setPositiveButton(R.string.close, null).show());

        click(R.id.themeChoice, () -> new AlertDialog.Builder(activity).setTitle("Giao diện")
                .setSingleChoiceItems(new String[]{"Sáng", "Tối", "Theo hệ thống"},
                        preferences.getInt("theme", 0), (dialog, which) -> {
                            preferences.edit().putInt("theme", which).apply();
                            dialog.dismiss();
                            applyTheme(activity);
                        }).setNegativeButton(R.string.cancel, null).show());

        click(R.id.settingsMenu, this::lists);
        click(R.id.settingsStar, () -> new AlertDialog.Builder(activity).setTitle(R.string.app_name)
                .setMessage(R.string.local_account_detail).setPositiveButton(R.string.close, null).show());
        click(R.id.settingsAccountLogin, () -> activity.startActivity(new android.content.Intent(activity, AuthActivity.class)));
        click(R.id.settingsAccountLogout, this::confirmLogout);
        click(R.id.settingsAccountDelete, this::confirmDeleteAccount);
        click(R.id.settingsAccountChangePassword, this::changePassword);
                .setMessage(activity.getString(R.string.local_account_detail) + "\n\n"
                        + vn.edu.taskmanager.sync.WorkspaceSync.state(activity).getString("status", "Chưa kết nối MySQL"))
                .setPositiveButton(R.string.close, null).show());
        click(R.id.collapseTasks, () -> { collapsed = !collapsed; refresh(); });

        click(R.id.currentDate, () -> new DatePickerDialog(activity, (picker, year, month, date) -> {
            selected.set(year, month, date); activity.showSelectedDay(); refresh();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show());
    }

    public static void applyTheme(MainActivity activity) {
        int mode = activity.getSharedPreferences("workspace_settings", 0).getInt("theme", 0);
        int desired = mode == 0 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 1 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != desired) AppCompatDelegate.setDefaultNightMode(desired);
    }

    public void saveState(Bundle state) {
        state.putInt("tab", tab);
        state.putInt("calendarSubTab", calendarSubTab);
        state.putBoolean("habitsOnly", habitsOnly);
        state.putBoolean("bookmarkedOnly", bookmarkedOnly);
        state.putString("noteQuery", noteQuery);
        state.putString("journalDateFrom", journalDateFrom);
        state.putString("journalDateTo", journalDateTo);
        state.putString("journalHabitId", journalHabitId);
        state.putInt("journalLimit", journalLimit);
        state.putBoolean("collapsed", collapsed);
        state.putLong("selectedDay", selected.getTimeInMillis());
        state.putLong("calendarDay", calendarDay.getTimeInMillis());
    }

    public boolean isHabitsOnly() { return habitsOnly; }
    public void showDailyOverview() { habitsOnly = false; collapsed = false; }
    public String selectedDate() { return day(selected); }
    public void refreshSyncStatus() {
        TextView statusView = activity.findViewById(R.id.syncStatus);
        String status = vn.edu.taskmanager.sync.WorkspaceSync.state(activity).getString("status", "");
        if (status.isEmpty()) {
            status = activity.getString(vn.edu.taskmanager.sync.WorkspaceSync.configured(activity)
                    ? R.string.sync_ready : R.string.sync_not_configured);
        }
        statusView.setText(status);
    }
    public boolean back() {
        if (tab == 0) return false;
        selectTab(0); return true;
    }
    public void updateTasks(List<Task> value) { tasks = value; refresh(); }

    public void entrySaved(LocalStore.Entry entry) {
        if ("habit".equals(entry.type)) {
            activity.showSelectedDay(); selectTab(0);
        } else if ("note".equals(entry.type)) {
            selectTab(1);
        } else if ("event".equals(entry.type)) {
            String[] parts = entry.date.split("-");
            calendarDay.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            calendarMonth.setTime(calendarDay.getTime());
            selectTab(2);
        }
    }

    public void taskSaved(String date) {
        habitsOnly = false; collapsed = false;
        if (date.isEmpty()) {
            selected.setTime(new Date()); activity.showSelectedDay();
        } else {
            String[] parts = date.split("-");
            selected.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            activity.showSelectedDay();
        }
        selectTab(0);
    }

    public void selectTab(int index) {
        tab = index;
        ((ViewFlipper) activity.findViewById(R.id.screens)).setDisplayedChild(index);
        int[] nav = {R.id.navTasks, R.id.navJournal, R.id.navCalendar, R.id.navSettings};
        int[] indicators = {R.id.navTasksIndicator, R.id.navJournalIndicator, R.id.navCalendarIndicator, R.id.navSettingsIndicator};
        for (int i = 0; i < nav.length; i++) {
            activity.findViewById(indicators[i]).setVisibility(i == index ? View.VISIBLE : View.INVISIBLE);
            View button = activity.findViewById(nav[i]);
            button.setSelected(i == index);
            int color = ContextCompat.getColor(activity, i == index ? R.color.navigation_blue : R.color.workspace_muted);
            ((ImageButton) button).setImageTintList(android.content.res.ColorStateList.valueOf(color));
        }
        refresh();
    }

    public void refresh() {
        refreshAccountUi();
        history.sync(tasks, store.entries("habit"), LocalStore.today());
        week(activity.findViewById(R.id.taskWeek), selected, () -> { activity.showSelectedDay(); refresh(); });
        refreshDate();
        refreshHabits();
        refreshJournal();
        refreshCalendar();

        activity.findViewById(R.id.taskListContainer).setVisibility(habitsOnly || collapsed ? View.GONE : View.VISIBLE);
        boolean showHabits = (habitsOnly || activity.isDailyOverview()) && !collapsed;
        activity.findViewById(R.id.habitScroll).setVisibility(showHabits ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.habitHeading).setVisibility(showHabits && !habitsOnly ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.collapseTasks).setRotation(collapsed ? 180 : 0);
        ((Button) activity.findViewById(R.id.themeChoice)).setText(new String[]{"Sáng  ﹀", "Tối  ﹀", "Hệ thống  ﹀"}[preferences.getInt("theme", 0)]);
        activity.renderTaskDates();
    }
    private void refreshAccountUi() {
        TextView status = activity.findViewById(R.id.accountStatus);
        View login = activity.findViewById(R.id.settingsAccountLogin);
        View logout = activity.findViewById(R.id.settingsAccountLogout);
        View delete = activity.findViewById(R.id.settingsAccountDelete);
        View changePassword =
                activity.findViewById(R.id.settingsAccountChangePassword);

        boolean loggedIn = AccountSession.isLoggedIn(activity);
        status.setText(loggedIn
                ? AccountSession.fullName(activity) + "\n" + AccountSession.email(activity)
                : "Đang dùng trên thiết bị · chưa đăng nhập");
        login.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        logout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        delete.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        changePassword.setVisibility(
                loggedIn ? View.VISIBLE : View.GONE
        );
    }

    private void confirmLogout() {
        new AlertDialog.Builder(activity).setTitle("Đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất khỏi TaskMind trên thiết bị này?")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    AccountSession.clear(activity);
                    refreshAccountUi();
                    Toast.makeText(activity, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void changePassword() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        int padding = (int) (20 * activity.getResources()
                .getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        EditText currentPassword = new EditText(activity);
        currentPassword.setSingleLine(true);
        currentPassword.setHint("Mật khẩu hiện tại");
        currentPassword.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        EditText newPassword = new EditText(activity);
        newPassword.setSingleLine(true);
        newPassword.setHint("Mật khẩu mới (ít nhất 8 ký tự)");
        newPassword.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        EditText confirmPassword = new EditText(activity);
        confirmPassword.setSingleLine(true);
        confirmPassword.setHint("Xác nhận mật khẩu mới");
        confirmPassword.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        layout.addView(currentPassword);
        layout.addView(newPassword);
        layout.addView(confirmPassword);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Đổi mật khẩu")
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Cập nhật", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String current = currentPassword.getText()
                                    .toString();
                            String next = newPassword.getText().toString();
                            String confirm = confirmPassword.getText()
                                    .toString();

                            if (current.isEmpty() || next.isEmpty()
                                    || confirm.isEmpty()) {
                                Toast.makeText(activity,
                                        "Vui lòng nhập đầy đủ thông tin",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            if (next.length() < 8 || next.length() > 72) {
                                newPassword.setError(
                                        "Mật khẩu phải có từ 8 đến 72 ký tự");
                                return;
                            }

                            if (!next.equals(confirm)) {
                                confirmPassword.setError(
                                        "Mật khẩu xác nhận không khớp");
                                return;
                            }

                            if (current.equals(next)) {
                                newPassword.setError(
                                        "Mật khẩu mới phải khác mật khẩu hiện tại");
                                return;
                            }

                            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(false);

                            AuthApi.changePassword(
                                    current,
                                    next,
                                    AccountSession.token(activity),
                                    (ok, data, message) -> {
                                        if (activity.isFinishing()
                                                || activity.isDestroyed()) {
                                            return;
                                        }

                                        dialog.getButton(
                                                        AlertDialog.BUTTON_POSITIVE)
                                                .setEnabled(true);

                                        if (ok) {
                                            dialog.dismiss();
                                            Toast.makeText(activity,
                                                    "Đổi mật khẩu thành công",
                                                    Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(activity,
                                                    message,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }));

        dialog.show();
    }


    private void confirmDeleteAccount() {
        EditText password = new EditText(activity);
        password.setSingleLine(true);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint("Nhập mật khẩu để xác nhận");
        int padding = (int) (20 * activity.getResources().getDisplayMetrics().density);
        password.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(activity).setTitle("Xóa tài khoản")
                .setMessage("Tài khoản sẽ bị vô hiệu hóa và thông tin nhận dạng sẽ được ẩn. Thao tác này không thể hoàn tác.")
                .setView(password).setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Xóa tài khoản", (dialog, which) -> {
                    String pass = password.getText().toString();
                    if (pass.isEmpty()) { Toast.makeText(activity, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show(); return; }
                    activity.findViewById(R.id.settingsAccountDelete).setEnabled(false);
                    AuthApi.deleteAccount(pass, AccountSession.token(activity), (ok, data, message) -> {
                        activity.findViewById(R.id.settingsAccountDelete).setEnabled(true);
                        if (ok) {
                            AccountSession.clear(activity); refreshAccountUi();
                            Toast.makeText(activity, "Tài khoản đã được xóa", Toast.LENGTH_LONG).show();
                        } else Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    });
                }).show();
    }

    private void refreshDate() {
        ((TextView) activity.findViewById(R.id.currentDate)).setText(
                day(selected).equals(LocalStore.today()) ? "Hôm nay" : new SimpleDateFormat("dd/MM", vietnamese).format(selected.getTime()));
    }

    public void add() {
        if (tab == 0 && !activity.currentTaskListId().isEmpty() && !habitsOnly) {
            activity.createTask(); return;
        }
        new AlertDialog.Builder(activity).setTitle("Thêm mới")
                .setItems(new String[]{"Công việc", "Thói quen hằng ngày", "Sự kiện lịch", "Nhắc việc mới"}, (dialog, which) -> {
                    if (which == 0) activity.createTask();
                    else if (which == 1) showHabitEditor(null);
                    else if (which == 2) {
                        EventEditorDialog.create(null, day(selected)).show(activity.getSupportFragmentManager(), "event_editor");
                    } else {
                        ReminderEditorDialog.create(null).show(activity.getSupportFragmentManager(), "reminder_editor");
                    }
                }).show();
    }

    private void editor(String type, String id, String date) {
        if (activity.getSupportFragmentManager().findFragmentByTag("entry_editor") == null)
            EntryEditorDialog.create(type, id, date).show(activity.getSupportFragmentManager(), "entry_editor");
    }

    private void refreshHabits() {
        LinearLayout rows = activity.findViewById(R.id.habitEntries);
        rows.removeAllViews();
        List<LocalStore.Entry> habits = store.entries("habit");
        String date = day(selected);
        int done = 0;
        String keyword = ((EditText) activity.findViewById(R.id.searchTasks)).getText().toString().trim().toLowerCase(Locale.ROOT);
        int visible = 0;
        for (LocalStore.Entry habit : habits) {
            if (!habitsOnly && (date.compareTo(habit.created) < 0 || !isScheduledDay(habit, date))) continue;
            if (!habit.title.toLowerCase(Locale.ROOT).contains(keyword)) continue;
            visible++;
            boolean completed = habit.days.contains(date) || habit.progressOn(date) >= 100;
            if (completed) done++;
            LinearLayout card = card(rows);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            TextView icon = new TextView(activity);
            icon.setText(habit.icon);
            icon.setTextSize(22);
            icon.setGravity(Gravity.CENTER);
            icon.setBackgroundResource(R.drawable.control_purple);
            try { icon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(habit.color))); }
            catch (IllegalArgumentException ignored) { }
            card.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout details = new LinearLayout(activity);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setGravity(Gravity.CENTER_VERTICAL);
            details.setPadding(dp(10), 0, dp(6), 0);
            card.addView(details, new LinearLayout.LayoutParams(0, dp(56), 1));
            TextView title = text(details, habit.title, 16);
            title.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
            int progress = habit.progressOn(date);
            TextView progressLabel = text(details, habit.targetCount > 1
                    ? progressCount(habit, date) + " / " + habit.targetCount + " " + habit.targetUnit
                    : progress + "% hoàn thành", 12);
            progressLabel.setTextColor(ContextCompat.getColor(activity, R.color.workspace_muted));
            card.setOnClickListener(v -> {
                if ("server_api".equals(habit.sourceType)) showRemoteHabitDetails(habit.sourceId);
                else habitActions(habit, date);
            });
            CheckBox check = new CheckBox(activity);
            check.setButtonDrawable(null);
            check.setBackgroundResource(R.drawable.completion_box);
            check.setContentDescription("Hoàn thành " + habit.title);
            check.setChecked(completed);
            boolean available = date.compareTo(LocalStore.today()) <= 0 && date.compareTo(habit.created) >= 0
                    && isScheduledDay(habit, date);
            check.setEnabled(available);
            check.setOnCheckedChangeListener((button, checked) -> {
                if ("server_api".equals(habit.sourceType)) {
                    int count = checked ? habit.targetCount : 0;
                    updateRemoteCheck(habit, date, count);
                } else {
                    history.sync(tasks, store.entries("habit"), LocalStore.today());
                    store.checkHabit(habit, date, checked); history.setHabitResult(habit, date); refresh();
                }
            });
            card.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        if (visible == 0) text(rows, habits.isEmpty() ? "Chưa có thói quen\nChạm + để tạo thói quen hằng ngày" : "Không có thói quen phù hợp", 18);
        ((TextView) activity.findViewById(R.id.habitHeading)).setText("Thói quen hằng ngày · " + visible + " · " + done + " đã hoàn thành");
        if (habitsOnly) {
            ((TextView) activity.findViewById(R.id.listHeading)).setText("Thói quen hằng ngày");
            ((TextView) activity.findViewById(R.id.taskSummary)).setText(visible + " thói quen · " + done + " đã hoàn thành");
            ((TextView) activity.findViewById(R.id.taskCount)).setText(String.valueOf(visible));
        }
    }

    public void searchChanged() { refreshHabits(); }

    private int progressCount(LocalStore.Entry habit, String date) {
        return Math.min(habit.targetCount, (habit.progressOn(date) * habit.targetCount + 50) / 100);
    }

    private boolean isScheduledDay(LocalStore.Entry habit, String date) {
        if (!habit.scheduleConfigured) return true;
        String[] parts = date.split("-");
        Calendar value = Calendar.getInstance();
        value.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        int index = value.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? 6 : value.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
        return (habit.weekdays & (1 << index)) != 0;
    }

    public void checkDayChange() {
        String today = LocalStore.today();
        if (!today.equals(lastDay)) {
            if (day(selected).equals(lastDay)) selected.setTime(new Date());
            if (day(calendarDay).equals(lastDay)) calendarDay.setTime(new Date());
            lastDay = today; refresh();
        }
    }

    private void habitActions(LocalStore.Entry habit, String date) {
        if ("server_api".equals(habit.sourceType)) {
            showRemoteHabitActions(habit, date);
            return;
        }
        new AlertDialog.Builder(activity).setTitle(habit.title)
                .setItems(new String[]{"Cập nhật mức hoàn thành ngày " + MainActivity.displayDate(date), "Sửa thói quen", "Xóa thói quen"}, (dialog, which) -> {
                    if (which == 1) editor("habit", habit.id, habit.date);
                    else if (which == 2) entryActions(habit);
                    else if (date.compareTo(LocalStore.today()) > 0) Toast.makeText(activity, "Chưa thể ghi kết quả cho ngày tương lai", Toast.LENGTH_SHORT).show();
                    else {
                        EditText input = new EditText(activity); input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                        input.setHint("Mức hoàn thành 0–100%"); input.setText(String.valueOf(habit.progressOn(date)));
                        AlertDialog progress = new AlertDialog.Builder(activity).setTitle("Mức hoàn thành")
                                .setView(input).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
                        progress.setOnShowListener(ignored -> progress.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                            int value;
                            try { value = Integer.parseInt(input.getText().toString().trim()); }
                            catch (NumberFormatException error) { input.setError("Nhập số từ 0 đến 100"); return; }
                            if (value < 0 || value > 100) { input.setError("Nhập số từ 0 đến 100"); return; }
                            history.sync(tasks, store.entries("habit"), LocalStore.today());
                            store.setHabitProgress(habit, date, value); history.setHabitResult(habit, date); refresh(); progress.dismiss();
                        })); progress.show();
                    }
                }).show();
    }

    private void showHabitEditor(vn.edu.taskmanager.cloud.CloudModels.Habit habit) {
        if (activity.getSupportFragmentManager().findFragmentByTag("habit_editor") == null) {
            String startDate = selectedDate();
            if (startDate.compareTo(LocalStore.today()) > 0) startDate = LocalStore.today();
            HabitEditorDialog.create(habit, startDate).show(activity.getSupportFragmentManager(), "habit_editor");
        }
    }

    private void showRemoteHabitActions(LocalStore.Entry habit, String date) {
        new AlertDialog.Builder(activity).setTitle(habit.title)
                .setItems(new String[]{"Xem chi tiết và lịch sử", "Cập nhật tiến độ hôm nay",
                        "Sửa thói quen", "Xóa thói quen"}, (dialog, which) -> {
                    int id = Integer.parseInt(habit.sourceId);
                    if (which == 0) showRemoteHabitDetails(habit.sourceId);
                    else if (which == 1) editRemoteProgress(habit, date);
                    else if (which == 2) {
                        if (habitCloud == null) return;
                        habitCloud.request(habitCloud.habits.habit(id),
                                new vn.edu.taskmanager.cloud.CloudRepository.Result<vn.edu.taskmanager.cloud.CloudModels.Habit>() {
                            @Override public void success(vn.edu.taskmanager.cloud.CloudModels.Habit remote) {
                                showHabitEditor(remote);
                            }
                            @Override public void error(String message) {
                                Toast.makeText(activity, "Không tải được thói quen: " + message, Toast.LENGTH_LONG).show();
                            }
                        });
                    } else confirmRemoteHabitDelete(habit);
                }).show();
    }

    private void editRemoteProgress(LocalStore.Entry habit, String date) {
        if (date.compareTo(LocalStore.today()) > 0 || !isScheduledDay(habit, date)) {
            Toast.makeText(activity, "Ngày này không thuộc lịch thực hiện hoặc ở tương lai", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText count = new EditText(activity);
        count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        count.setHint("Giá trị từ 0 đến " + habit.targetCount);
        count.setText(String.valueOf(progressCount(habit, date)));
        new AlertDialog.Builder(activity).setTitle("Cập nhật tiến độ")
                .setMessage(habit.title + " · mục tiêu " + habit.targetCount + " " + habit.targetUnit)
                .setView(count).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    int value;
                    try { value = Integer.parseInt(count.getText().toString().trim()); }
                    catch (NumberFormatException error) {
                        Toast.makeText(activity, "Nhập số từ 0 đến " + habit.targetCount, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (value < 0 || value > habit.targetCount) {
                        Toast.makeText(activity, "Giá trị phải từ 0 đến " + habit.targetCount, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateRemoteCheck(habit, date, value);
                }).show();
    }

    private void updateRemoteCheck(LocalStore.Entry habit, String date, int actualCount) {
        if (habitCloud == null) {
            Toast.makeText(activity, "Chưa cấu hình kết nối Productivity Service", Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("completed", actualCount >= habit.targetCount);
        body.put("actualCount", actualCount);
        int id = Integer.parseInt(habit.sourceId);
        habitCloud.request(habitCloud.habits.check(id, date, body),
                new vn.edu.taskmanager.cloud.CloudRepository.Result<vn.edu.taskmanager.cloud.CloudModels.Habit>() {
            @Override public void success(vn.edu.taskmanager.cloud.CloudModels.Habit remote) {
                habitMutationVersion++;
                cacheRemoteHabit(remote);
                LocalStore.Entry cached = store.find(habit.id);
                if (cached != null) {
                    int progress = Math.min(100, actualCount * 100 / Math.max(1, cached.targetCount));
                    cached.dailyProgress.put(date, progress);
                    cached.days.remove(date);
                    if (actualCount >= cached.targetCount) cached.days.add(date);
                    store.save(cached);
                }
                history.sync(tasks, store.entries("habit"), LocalStore.today());
                if (cached != null) history.setHabitResult(cached, date);
                refresh();
                Toast.makeText(activity, "Đã cập nhật tiến độ thói quen", Toast.LENGTH_SHORT).show();
            }
            @Override public void error(String message) {
                refresh();
                Toast.makeText(activity, "Chưa cập nhật được tiến độ: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmRemoteHabitDelete(LocalStore.Entry habit) {
        new AlertDialog.Builder(activity).setTitle("Xóa thói quen?")
                .setMessage("Lịch sử đã ghi sẽ được giữ lại nhưng thói quen sẽ bị ẩn.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (habitCloud == null) return;
                    int id = Integer.parseInt(habit.sourceId);
                    habitCloud.request(habitCloud.habits.delete(id),
                            new vn.edu.taskmanager.cloud.CloudRepository.Result<Void>() {
                        @Override public void success(Void ignored) {
                            habitMutationVersion++;
                            cancelHabitReminder(habit.sourceId);
                            store.delete(habit.id);
                            refresh();
                            Toast.makeText(activity, "Đã xóa thói quen", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void error(String message) {
                            Toast.makeText(activity, "Chưa xóa được thói quen: " + message, Toast.LENGTH_LONG).show();
                        }
                    });
                }).show();
    }

    private void showRemoteHabitDetails(String habitId) {
        if (habitCloud == null) return;
        habitCloud.request(habitCloud.habits.habit(Integer.parseInt(habitId)),
                new vn.edu.taskmanager.cloud.CloudRepository.Result<vn.edu.taskmanager.cloud.CloudModels.Habit>() {
            @Override public void success(vn.edu.taskmanager.cloud.CloudModels.Habit habit) {
                showRemoteHabitCalendar(habit);
            }
            @Override public void error(String message) {
                Toast.makeText(activity, "Không tải được chi tiết thói quen: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showRemoteHabitCalendar(vn.edu.taskmanager.cloud.CloudModels.Habit habit) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView description = text(content, habit.icon + "  " + habit.name + "\nMục tiêu: "
                + habit.targetCount + " " + habit.targetUnit + " mỗi ngày\n"
                + "Chuỗi hiện tại: " + habit.currentStreak + " · Kỷ lục: " + habit.longestStreak
                + "\nHoàn thành: " + habit.completedDays + "/" + habit.scheduledDays
                + " ngày · " + habit.completionRate + "%", 15);
        TextView periodStats = text(content, "", 13);
        LinearLayout monthNavigation = new LinearLayout(activity);
        monthNavigation.setGravity(Gravity.CENTER);
        Button previous = new Button(activity);
        previous.setText("‹");
        Button next = new Button(activity);
        next.setText("›");
        TextView monthLabel = new TextView(activity);
        monthLabel.setTextSize(16);
        monthLabel.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
        monthLabel.setGravity(Gravity.CENTER);
        monthNavigation.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(44)));
        monthNavigation.addView(monthLabel, new LinearLayout.LayoutParams(0, dp(44), 1));
        monthNavigation.addView(next, new LinearLayout.LayoutParams(dp(52), dp(44)));
        content.addView(monthNavigation);
        LinearLayout dayNames = new LinearLayout(activity);
        String[] names = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        for (String name : names) {
            TextView dayName = text(dayNames, name, 12);
            dayName.setGravity(Gravity.CENTER);
            dayName.setLayoutParams(new LinearLayout.LayoutParams(0, dp(30), 1));
        }
        content.addView(dayNames);
        GridLayout calendar = new GridLayout(activity);
        calendar.setColumnCount(7);
        content.addView(calendar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView selectedInfo = text(content, "", 14);
        periodStats.setPadding(0, dp(8), 0, dp(8));
        selectedInfo.setPadding(0, dp(8), 0, dp(8));
        Calendar month = Calendar.getInstance();
        String[] selectedDate = {LocalStore.today()};
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(content);
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Chi tiết thói quen")
                .setView(scroll)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton("Xóa", null)
                .setPositiveButton("Sửa", null).create();
        Runnable[] loadMonth = new Runnable[1];
        loadMonth[0] = () -> {
            Calendar start = (Calendar) month.clone();
            start.set(Calendar.DAY_OF_MONTH, 1);
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
            end.add(Calendar.DAY_OF_MONTH, -1);
            String from = LocalStore.day(start.getTime());
            String to = LocalStore.day(end.getTime());
            monthLabel.setText(new SimpleDateFormat("MMMM yyyy", vietnamese).format(start.getTime()));
            if (habitCloud == null) return;
            habitCloud.request(habitCloud.habits.history(habit.id, from, to),
                    new vn.edu.taskmanager.cloud.CloudRepository.Result<List<vn.edu.taskmanager.cloud.CloudModels.Day>>() {
                @Override public void success(List<vn.edu.taskmanager.cloud.CloudModels.Day> days) {
                    calendar.removeAllViews();
                    Map<String, vn.edu.taskmanager.cloud.CloudModels.Day> byDate = new HashMap<>();
                    int monthScheduled = 0, monthDone = 0, weekScheduled = 0, weekDone = 0;
                    Calendar today = Calendar.getInstance();
                    Calendar monday = Calendar.getInstance();
                    monday.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                    for (vn.edu.taskmanager.cloud.CloudModels.Day day : days) {
                        byDate.put(day.date, day);
                        if (day.date.compareTo(LocalStore.today()) > 0) continue;
                        if (day.scheduled) {
                            monthScheduled++;
                            if (day.completed) monthDone++;
                            Calendar dayCalendar = Calendar.getInstance();
                            String[] p = day.date.split("-");
                            dayCalendar.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                            if (!dayCalendar.before(monday) && !dayCalendar.after(today)) {
                                weekScheduled++;
                                if (day.completed) weekDone++;
                            }
                        }
                    }
                    periodStats.setText("Tuần này: " + weekDone + "/" + weekScheduled
                            + " · Tháng này: " + monthDone + "/" + monthScheduled);
                    Calendar first = (Calendar) month.clone();
                    first.set(Calendar.DAY_OF_MONTH, 1);
                    int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                    for (int empty = 0; empty < offset; empty++) addCalendarCell(calendar, "", false, false, null);
                    int count = first.getActualMaximum(Calendar.DAY_OF_MONTH);
                    for (int d = 1; d <= count; d++) {
                        Calendar date = (Calendar) first.clone();
                        date.set(Calendar.DAY_OF_MONTH, d);
                        String key = LocalStore.day(date.getTime());
                        vn.edu.taskmanager.cloud.CloudModels.Day result = byDate.get(key);
                        String label = String.valueOf(d);
                        if (result != null && result.scheduled) label += result.completed ? " ✓" : " •";
                        addCalendarCell(calendar, label, result != null && result.scheduled,
                                key.equals(selectedDate[0]), () -> {
                                    selectedDate[0] = key;
                                    renderSelectedHabitDay(selectedInfo, result, key, habit);
                                    refreshCalendarSelection(calendar, selectedDate[0], first, byDate);
                                });
                    }
                    renderSelectedHabitDay(selectedInfo, byDate.get(selectedDate[0]), selectedDate[0], habit);
                    cacheHabitHistory(habit, days);
                }
                @Override public void error(String message) {
                    periodStats.setText("Không tải được lịch sử: " + message);
                }
            });
        };
        previous.setOnClickListener(v -> { month.add(Calendar.MONTH, -1); loadMonth[0].run(); });
        next.setOnClickListener(v -> {
            Calendar current = Calendar.getInstance();
            if (month.get(Calendar.YEAR) < current.get(Calendar.YEAR)
                    || month.get(Calendar.MONTH) < current.get(Calendar.MONTH)) {
                month.add(Calendar.MONTH, 1);
                loadMonth[0].run();
            }
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                showHabitEditor(habit);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                LocalStore.Entry entry = store.find("api-habit-" + habit.id);
                if (entry != null) confirmRemoteHabitDelete(entry);
            });
            loadMonth[0].run();
        });
        dialog.show();
    }

    private void addCalendarCell(GridLayout grid, String label, boolean scheduled, boolean selected,
            Runnable onClick) {
        TextView cell = new TextView(activity);
        cell.setText(label);
        cell.setTextSize(12);
        cell.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));
        cell.setGravity(Gravity.CENTER);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(42);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        grid.addView(cell, params);
        cell.setBackgroundResource(selected ? R.drawable.day_selected
                : scheduled ? R.drawable.control_soft : android.R.color.transparent);
        cell.setOnClickListener(onClick == null ? null : view -> onClick.run());
    }

    private void refreshCalendarSelection(GridLayout grid, String selectedDate, Calendar month,
            Map<String, vn.edu.taskmanager.cloud.CloudModels.Day> days) {
        Calendar first = (Calendar) month.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int cellIndex = 0;
        for (int i = 0; i < offset; i++) cellIndex++;
        for (int d = 1; d <= first.getActualMaximum(Calendar.DAY_OF_MONTH); d++) {
            Calendar date = (Calendar) first.clone();
            date.set(Calendar.DAY_OF_MONTH, d);
            String key = LocalStore.day(date.getTime());
            vn.edu.taskmanager.cloud.CloudModels.Day result = days.get(key);
            TextView cell = (TextView) grid.getChildAt(cellIndex++);
            if (cell != null) cell.setBackgroundResource(key.equals(selectedDate) ? R.drawable.day_selected
                    : result != null && result.scheduled ? R.drawable.control_soft : android.R.color.transparent);
        }
    }

    private void renderSelectedHabitDay(TextView view, vn.edu.taskmanager.cloud.CloudModels.Day day,
            String date, vn.edu.taskmanager.cloud.CloudModels.Habit habit) {
        String result;
        if (day == null || !day.scheduled) result = "Không nằm trong lịch thực hiện";
        else result = day.completed ? "Đã hoàn thành · " : "Chưa hoàn thành · "
                + day.actualCount + "/" + habit.targetCount + " " + habit.targetUnit;
        view.setText(MainActivity.displayDate(date) + " · " + result);
    }

    private void cacheHabitHistory(vn.edu.taskmanager.cloud.CloudModels.Habit habit,
            List<vn.edu.taskmanager.cloud.CloudModels.Day> days) {
        LocalStore.Entry entry = store.find("api-habit-" + habit.id);
        if (entry == null) return;
        for (vn.edu.taskmanager.cloud.CloudModels.Day day : days) {
            int progress = Math.min(100, day.actualCount * 100 / Math.max(1, habit.targetCount));
            entry.dailyProgress.put(day.date, progress);
            entry.days.remove(day.date);
            if (day.completed) entry.days.add(day.date);
        }
        store.save(entry);
    }

    // =========================================================================
    // UC09: QUẢN LÝ NHẬT KÝ HOẠT ĐỘNG
    // =========================================================================
    private void showJournalFilterDialog() {
        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_event_editor, null); // tái dùng form hoặc dialog tùy biến
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Bộ lọc nhật ký");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
        container.addView(tvTitle);

        // 1. Khoảng ngày
        TextView lblDate = new TextView(activity);
        lblDate.setText("Khoảng ngày (Từ ngày – Đến ngày)");
        lblDate.setTextSize(13);
        lblDate.setTextColor(ContextCompat.getColor(activity, R.color.workspace_muted));
        lblDate.setPadding(0, dp(12), 0, dp(4));
        container.addView(lblDate);

        LinearLayout dateRow = new LinearLayout(activity);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnFrom = new Button(activity);
        btnFrom.setText(journalDateFrom.isEmpty() ? "Từ ngày" : MainActivity.displayDate(journalDateFrom));
        btnFrom.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1));
        dateRow.addView(btnFrom);

        Button btnTo = new Button(activity);
        btnTo.setText(journalDateTo.isEmpty() ? "Đến ngày" : MainActivity.displayDate(journalDateTo));
        LinearLayout.LayoutParams toLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        toLp.leftMargin = dp(8);
        btnTo.setLayoutParams(toLp);
        dateRow.addView(btnTo);
        container.addView(dateRow);

        final String[] tempFrom = {journalDateFrom};
        final String[] tempTo = {journalDateTo};

        btnFrom.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(activity, (picker, y, m, d) -> {
                tempFrom[0] = String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d);
                btnFrom.setText(MainActivity.displayDate(tempFrom[0]));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnTo.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(activity, (picker, y, m, d) -> {
                tempTo[0] = String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d);
                btnTo.setText(MainActivity.displayDate(tempTo[0]));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        // 2. Thói quen liên quan
        TextView lblHabit = new TextView(activity);
        lblHabit.setText("Thói quen liên quan");
        lblHabit.setTextSize(13);
        lblHabit.setTextColor(ContextCompat.getColor(activity, R.color.workspace_muted));
        lblHabit.setPadding(0, dp(12), 0, dp(4));
        container.addView(lblHabit);

        Spinner spinnerHabit = new Spinner(activity);
        List<LocalStore.Entry> habits = store.entries("habit");
        List<String> habitNames = new ArrayList<>();
        List<String> habitIds = new ArrayList<>();
        habitNames.add("Tất cả thói quen");
        habitIds.add("");
        for (LocalStore.Entry h : habits) {
            habitNames.add(h.title);
            habitIds.add(h.id);
        }
        spinnerHabit.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, habitNames));
        int curHabitPos = habitIds.indexOf(journalHabitId);
        if (curHabitPos >= 0) spinnerHabit.setSelection(curHabitPos);
        container.addView(spinnerHabit);

        // 3. Đánh dấu
        CheckBox cbBookmark = new CheckBox(activity);
        cbBookmark.setText("Chỉ hiển thị ghi chú đã đánh dấu");
        cbBookmark.setChecked(bookmarkedOnly);
        cbBookmark.setPadding(dp(4), dp(12), 0, dp(12));
        container.addView(cbBookmark);

        new AlertDialog.Builder(activity)
                .setView(container)
                .setNegativeButton("Hủy", null)
                .setNeutralButton("Xóa bộ lọc", (d, w) -> {
                    journalDateFrom = "";
                    journalDateTo = "";
                    journalHabitId = "";
                    bookmarkedOnly = false;
                    journalLimit = 30;
                    refreshJournal();
                })
                .setPositiveButton("Áp dụng", (d, w) -> {
                    journalDateFrom = tempFrom[0];
                    journalDateTo = tempTo[0];
                    journalHabitId = habitIds.get(spinnerHabit.getSelectedItemPosition());
                    bookmarkedOnly = cbBookmark.isChecked();
                    journalLimit = 30;
                    refreshJournal();
                })
                .show();
    }

    private int countActiveJournalFilters() {
        int count = 0;
        if (!journalDateFrom.isEmpty() || !journalDateTo.isEmpty()) count++;
        if (!journalHabitId.isEmpty()) count++;
        if (bookmarkedOnly) count++;
        return count;
    }

    private void refreshJournal() {
        LinearLayout rows = activity.findViewById(R.id.journalEntries);
        if (rows == null) return;
        rows.removeAllViews();

        int filterCount = countActiveJournalFilters();
        Button filterBtn = activity.findViewById(R.id.journalFilterBtn);
        if (filterBtn != null) {
            filterBtn.setText(filterCount > 0 ? "Bộ lọc (" + filterCount + ")  ﹀" : "Bộ lọc  ﹀");
        }

        View clearFilters = activity.findViewById(R.id.journalClearFilters);
        if (clearFilters != null) {
            clearFilters.setVisibility((filterCount > 0 || !noteQuery.isEmpty()) ? View.VISIBLE : View.GONE);
        }

        ImageButton bmBtn = activity.findViewById(R.id.journalBookmark);
        if (bmBtn != null) {
            bmBtn.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(activity, bookmarkedOnly ? R.color.workspace_accent : R.color.workspace_text)));
        }

        String today = LocalStore.today();
        List<vn.edu.taskmanager.data.DailyHistory.Row> allHistory = history.rows();
        List<LocalStore.Entry> userNotes = store.entries("note");

        // Tổng hợp các dòng hiển thị: phân biệt "Ghi chú" (sửa/xóa) và "Hoạt động" (chỉ đọc)
        List<JournalItem> allItems = new ArrayList<>();

        for (LocalStore.Entry note : userNotes) {
            JournalItem item = new JournalItem();
            item.isUserNote = true;
            item.id = note.id;
            item.title = note.title;
            item.body = note.body;
            item.date = note.date;
            item.time = note.time;
            item.label = note.label.isEmpty() ? "Ghi chú" : note.label;
            item.bookmarked = note.bookmarked;
            item.sourceId = note.sourceId;
            item.sourceType = note.sourceType;
            item.progress = note.progress;
            allItems.add(item);
        }

        for (vn.edu.taskmanager.data.DailyHistory.Row row : allHistory) {
            if (row.date.compareTo(today) >= 0) continue; // Ngày đã qua
            JournalItem item = new JournalItem();
            item.isUserNote = false;
            item.id = row.key;
            item.title = row.title;
            item.body = row.note;
            item.date = row.date;
            item.time = "Hoạt động";
            item.label = "habit".equals(row.kind) ? "Thói quen" : "Công việc";
            item.bookmarked = row.bookmarked;
            item.sourceId = row.sourceId;
            item.sourceType = row.kind;
            item.progress = row.progress;
            allItems.add(item);
        }

        // Sắp xếp ngày mới nhất lên trước
        Collections.sort(allItems, (a, b) -> {
            int d = b.date.compareTo(a.date);
            return d != 0 ? d : b.time.compareTo(a.time);
        });

        // Áp dụng bộ lọc đồng thời (AND)
        List<JournalItem> visible = new ArrayList<>();
        String normalizedQuery = noteQuery.trim();

        for (JournalItem item : allItems) {
            // Bộ lọc 1: Khoảng ngày
            if (!journalDateFrom.isEmpty() && item.date.compareTo(journalDateFrom) < 0) continue;
            if (!journalDateTo.isEmpty() && item.date.compareTo(journalDateTo) > 0) continue;

            // Bộ lọc 2: Thói quen liên quan
            if (!journalHabitId.isEmpty()) {
                if (!journalHabitId.equals(item.sourceId)) continue;
            }

            // Bộ lọc 3: Đánh dấu
            if (bookmarkedOnly && !item.bookmarked) continue;

            // Tìm kiếm không phân biệt dấu và hoa/thường (UC09)
            if (!normalizedQuery.isEmpty()) {
                String searchTarget = item.title + " " + item.body + " " + item.label;
                if (!VietnameseUtils.containsIgnoreAccents(searchTarget, normalizedQuery)) {
                    continue;
                }
            }

            visible.add(item);
        }

        // Tải theo mốc phân trang (UC09: 30 mục mỗi lần)
        int totalVisible = visible.size();
        int displayCount = Math.min(journalLimit, totalVisible);

        String previousDate = "";
        for (int i = 0; i < displayCount; i++) {
            JournalItem item = visible.get(i);
            if (!item.date.equals(previousDate)) {
                text(rows, MainActivity.displayDate(item.date), 12);
                previousDate = item.date;
            }

            View card = activity.getLayoutInflater().inflate(R.layout.item_journal, rows, false);
            rows.addView(card);

            String[] dParts = item.date.split("-");
            ((TextView) card.findViewById(R.id.noteMeta)).setText(Integer.parseInt(dParts[2]) + " Thg " + Integer.parseInt(dParts[1]));

            TextView tvTitle = card.findViewById(R.id.noteTitle);
            tvTitle.setText((item.progress == 100 ? "✓  " : "") + item.title);
            tvTitle.setBackgroundResource(item.isUserNote ? R.drawable.control_soft : R.drawable.history_habit);
            tvTitle.setTextColor(item.isUserNote ? ContextCompat.getColor(activity, R.color.workspace_text) : Color.WHITE);

            TextView tvProgress = card.findViewById(R.id.noteProgress);
            tvProgress.setText(item.progress >= 0 ? item.progress + "%" : "");

            TextView tvPreview = card.findViewById(R.id.notePreview);
            String previewText = (item.isUserNote ? "Ghi chú" : "Hoạt động") + " · " + (item.body.isEmpty() ? "Không có nội dung" : item.body);
            tvPreview.setText(previewText);

            // Nút đánh dấu 1 chạm (UC09)
            ImageButton btnBookmark = card.findViewById(R.id.noteBookmark);
            if (btnBookmark != null) {
                btnBookmark.setImageTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(activity, item.bookmarked ? R.color.workspace_accent : R.color.workspace_muted)));
                btnBookmark.setOnClickListener(v -> {
                    item.bookmarked = !item.bookmarked;
                    if (item.isUserNote) {
                        LocalStore.Entry n = store.find(item.id);
                        if (n != null) {
                            n.bookmarked = item.bookmarked;
                            store.save(n);
                        }
                    } else {
                        for (vn.edu.taskmanager.data.DailyHistory.Row r : history.rows()) {
                            if (r.key.equals(item.id)) {
                                history.bookmark(r);
                                break;
                            }
                        }
                    }
                    refreshJournal();
                });
            }

            // Click vào thẻ để đọc chi tiết
            card.setOnClickListener(v -> {
                if (item.isUserNote) {
                    JournalReaderDialog.create(item.id).show(activity.getSupportFragmentManager(), "journal_reader");
                } else {
                    new AlertDialog.Builder(activity)
                            .setTitle(item.title)
                            .setMessage("Ngày " + MainActivity.displayDate(item.date) + "\n" + item.label +
                                    "\nMức hoàn thành: " + item.progress + "%\n" + (item.body.isEmpty() ? "" : "\n" + item.body))
                            .setPositiveButton(R.string.close, null)
                            .show();
                }
            });

            // Tùy chọn 3 chấm
            card.findViewById(R.id.noteMore).setOnClickListener(v -> {
                if (item.isUserNote) {
                    new AlertDialog.Builder(activity).setTitle(item.title)
                            .setItems(new String[]{"Xem chi tiết", "Sửa ghi chú", "Xóa ghi chú"}, (dialog, which) -> {
                                if (which == 0) JournalReaderDialog.create(item.id).show(activity.getSupportFragmentManager(), "journal_reader");
                                else if (which == 1) EntryEditorDialog.create("note", item.id, item.date).show(activity.getSupportFragmentManager(), "entry_editor");
                                else confirmDeleteNote(item.id, item.title);
                            }).show();
                } else {
                    new AlertDialog.Builder(activity).setTitle(item.title)
                            .setItems(new String[]{"Xem chi tiết", item.bookmarked ? "Bỏ đánh dấu" : "Đánh dấu"}, (dialog, which) -> {
                                if (which == 0) card.performClick();
                                else if (btnBookmark != null) btnBookmark.performClick();
                            }).show();
                }
            });
        }

        // Nút tải thêm 30 mục
        Button btnLoadMore = activity.findViewById(R.id.journalLoadMore);
        if (btnLoadMore != null) {
            btnLoadMore.setVisibility(totalVisible > displayCount ? View.VISIBLE : View.GONE);
        }

        // Trạng thái trống chuẩn theo kế hoạch UC09
        TextView summary = activity.findViewById(R.id.journalSummary);
        if (summary != null) {
            summary.setText(totalVisible > 0 ? "Hiển thị " + displayCount + "/" + totalVisible + " mục" : "");
        }

        if (totalVisible == 0) {
            if (allItems.isEmpty()) {
                text(rows, "Chưa có ghi chú. Viết ghi chú đầu tiên", 16);
            } else {
                text(rows, "Không tìm thấy nội dung phù hợp", 16);
            }
        }
    }

    private void confirmDeleteNote(String id, String title) {
        new AlertDialog.Builder(activity)
                .setTitle("Xóa ghi chú?")
                .setMessage("Bạn có muốn xóa ghi chú này không?")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.delete(id);
                    refreshJournal();
                })
                .show();
    }

    private static class JournalItem {
        boolean isUserNote;
        String id;
        String title;
        String body;
        String date;
        String time;
        String label;
        boolean bookmarked;
        String sourceId;
        String sourceType;
        int progress;
    }

    // =========================================================================
    // UC10: QUẢN LÝ LỊCH & UC11: QUẢN LÝ NHẮC VIỆC
    // =========================================================================
    private void refreshCalendar() {
        View calContainer = activity.findViewById(R.id.calendarScheduleContainer);
        View remContainer = activity.findViewById(R.id.calendarRemindersContainer);
        if (calContainer == null || remContainer == null) return;

        calContainer.setVisibility(calendarSubTab == 0 ? View.VISIBLE : View.GONE);
        remContainer.setVisibility(calendarSubTab == 1 ? View.VISIBLE : View.GONE);

        if (calendarSubTab == 0) {
            renderMonthCalendarView();
            renderSelectedDayEventsView();
        } else {
            renderRemindersListView();
        }
    }

    private void renderMonthCalendarView() {
        TextView tvMonth = activity.findViewById(R.id.calendarMonthTitle);
        if (tvMonth != null) {
            SimpleDateFormat monthFormat = new SimpleDateFormat("'Tháng' MM/yyyy", vietnamese);
            tvMonth.setText(monthFormat.format(calendarMonth.getTime()));
        }

        TableLayout table = activity.findViewById(R.id.calendarMonthTable);
        if (table == null) return;
        table.removeAllViews();

        List<LocalStore.Entry> allEvents = store.entries("event");
        Set<String> datesWithEvents = new HashSet<>();
        for (LocalStore.Entry e : allEvents) {
            datesWithEvents.add(e.date);
        }

        Calendar firstDay = (Calendar) calendarMonth.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);

        // Thứ 2 là ngày đầu tuần
        int dayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK);
        int leadingDays = (dayOfWeek + 5) % 7; // MONDAY=2 -> 0, SUNDAY=1 -> 6

        Calendar gridCal = (Calendar) firstDay.clone();
        gridCal.add(Calendar.DAY_OF_MONTH, -leadingDays);

        String selDayStr = day(calendarDay);
        String todayStr = LocalStore.today();
        int monthInt = calendarMonth.get(Calendar.MONTH);

        for (int row = 0; row < 6; row++) {
            TableRow tableRow = new TableRow(activity);
            tableRow.setLayoutParams(new TableLayout.LayoutParams(-1, -2));

            for (int col = 0; col < 7; col++) {
                final String thisDayStr = day(gridCal);
                final Calendar clickedCal = (Calendar) gridCal.clone();
                boolean isCurrentMonth = gridCal.get(Calendar.MONTH) == monthInt;
                boolean isSelected = thisDayStr.equals(selDayStr);
                boolean isToday = thisDayStr.equals(todayStr);
                boolean hasEvent = datesWithEvents.contains(thisDayStr);

                LinearLayout cell = new LinearLayout(activity);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(0, dp(4), 0, dp(4));

                TableRow.LayoutParams cellLp = new TableRow.LayoutParams(0, dp(42), 1f);
                cell.setLayoutParams(cellLp);

                if (isSelected) {
                    cell.setBackgroundResource(R.drawable.control_black);
                } else if (isToday) {
                    cell.setBackgroundResource(R.drawable.control_soft);
                }

                TextView tvNum = new TextView(activity);
                tvNum.setText(String.valueOf(gridCal.get(Calendar.DAY_OF_MONTH)));
                tvNum.setTextSize(14);
                tvNum.setGravity(Gravity.CENTER);

                if (isSelected) {
                    tvNum.setTextColor(Color.WHITE);
                    tvNum.setTypeface(Typeface.DEFAULT_BOLD);
                } else if (isCurrentMonth) {
                    tvNum.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
                } else {
                    tvNum.setTextColor(ContextCompat.getColor(activity, R.color.workspace_line));
                }
                cell.addView(tvNum);

                // Dấu chấm báo ngày có sự kiện (UC10)
                View dot = new View(activity);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(4), dp(4));
                dotLp.topMargin = dp(2);
                dot.setLayoutParams(dotLp);
                if (hasEvent) {
                    dot.setBackgroundResource(isSelected ? R.drawable.control_soft : R.drawable.badge_habits);
                } else {
                    dot.setVisibility(View.INVISIBLE);
                }
                cell.addView(dot);

                cell.setOnClickListener(v -> {
                    calendarDay.setTime(clickedCal.getTime());
                    renderMonthCalendarView();
                    renderSelectedDayEventsView();
                });

                tableRow.addView(cell);
                gridCal.add(Calendar.DAY_OF_MONTH, 1);
            }
            table.addView(tableRow);
        }
    }

    private void renderSelectedDayEventsView() {
        TextView tvHeader = activity.findViewById(R.id.calendarSelectedDayHeader);
        if (tvHeader != null) {
            tvHeader.setText("Sự kiện ngày " + MainActivity.displayDate(day(calendarDay)));
        }

        LinearLayout list = activity.findViewById(R.id.calendarEventsList);
        if (list == null) return;
        list.removeAllViews();

        String selectedDate = day(calendarDay);
        List<LocalStore.Entry> dayEvents = new ArrayList<>();
        for (LocalStore.Entry e : store.entries("event")) {
            if (selectedDate.equals(e.date)) {
                dayEvents.add(e);
            }
        }

        // Sắp xếp theo giờ bắt đầu
        Collections.sort(dayEvents, (a, b) -> a.time.compareTo(b.time));

        if (dayEvents.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Chưa có sự kiện trong ngày.\nChạm + để thêm sự kiện mới.");
            empty.setPadding(dp(16), dp(24), dp(16), dp(16));
            empty.setTextSize(14);
            empty.setTextColor(ContextCompat.getColor(activity, R.color.workspace_muted));
            list.addView(empty);
            return;
        }

        // Kiểm tra sự kiện trùng giờ (UC10)
        for (int i = 0; i < dayEvents.size(); i++) {
            LocalStore.Entry event = dayEvents.get(i);
            boolean isConflict = false;
            for (int j = 0; j < dayEvents.size(); j++) {
                if (i != j) {
                    LocalStore.Entry other = dayEvents.get(j);
                    if (event.time.equals(other.time) && !event.time.equals("00:00")) {
                        isConflict = true;
                        break;
                    }
                }
            }

            LinearLayout card = card(list);
            card.setOnClickListener(v -> EventEditorDialog.create(event.id, event.date).show(activity.getSupportFragmentManager(), "event_editor"));

            // Thời gian
            boolean isAllDay = "all_day".equals(event.label) || event.progress == 1;
            String timeText;
            if (isAllDay) {
                timeText = "Cả ngày";
            } else {
                String end = event.label != null && event.label.startsWith("end:") ? event.label.substring(4) : "";
                timeText = event.time + (!end.isEmpty() ? " – " + end : "");
            }

            LinearLayout topRow = new LinearLayout(activity);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvTime = new TextView(activity);
            tvTime.setText(timeText);
            tvTime.setTextSize(13);
            tvTime.setTypeface(Typeface.DEFAULT_BOLD);
            tvTime.setTextColor(ContextCompat.getColor(activity, R.color.navigation_blue));
            topRow.addView(tvTime);

            if (isConflict) {
                TextView tvConflict = new TextView(activity);
                tvConflict.setText("  ·  Trùng giờ");
                tvConflict.setTextSize(12);
                tvConflict.setTextColor(Color.rgb(220, 50, 50));
                topRow.addView(tvConflict);
            }

            card.addView(topRow);

            // Tiêu đề sự kiện
            TextView tvTitle = new TextView(activity);
            tvTitle.setText(event.title);
            tvTitle.setTextSize(16);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvTitle.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
            tvTitle.setPadding(0, dp(4), 0, dp(2));
            card.addView(tvTitle);

            // Kiểm tra có lời nhắc liên kết không
            Reminder linkedRem = store.findReminderByEvent(event.id);
            if (linkedRem != null && linkedRem.isEnabled) {
                TextView tvRem = new TextView(activity);
                String remDesc = linkedRem.eventReminderMinutes == 15 ? "Nhắc trước 15 phút" : "Nhắc đúng lúc";
                tvRem.setText("🔔 " + remDesc);
                tvRem.setTextSize(12);
                tvRem.setTextColor(ContextCompat.getColor(activity, R.color.workspace_muted));
                card.addView(tvRem);
            }

            // Mô tả
            if (event.body != null && !event.body.isEmpty()) {
                TextView tvBody = new TextView(activity);
                tvBody.setText(event.body);
                tvBody.setTextSize(12);
                tvBody.setTextColor(ContextCompat.getColor(activity, R.color.workspace_muted));
                card.addView(tvBody);
            }
        }
    }

    private void renderRemindersListView() {
        // Kiểm tra quyền (UC11)
        View permBanner = activity.findViewById(R.id.reminderPermBanner);
        if (permBanner != null) {
            boolean hasPerm = ReminderScheduler.hasNotificationPermission(activity);
            permBanner.setVisibility(hasPerm ? View.GONE : View.VISIBLE);
        }

        View exactBanner = activity.findViewById(R.id.reminderExactBanner);
        if (exactBanner != null) {
            boolean hasExact = ReminderScheduler.hasExactAlarmPermission(activity);
            exactBanner.setVisibility(hasExact ? View.GONE : View.VISIBLE);
        }

        LinearLayout list = activity.findViewById(R.id.reminderList);
        if (list == null) return;
        list.removeAllViews();

        List<Reminder> reminders = store.reminders();

        // Sắp xếp: Đang bật và còn lượt nhắc lên trước, đã tắt/kết thúc ở cuối (UC11)
        Collections.sort(reminders, (a, b) -> {
            if (a.isEnabled != b.isEnabled) {
                return a.isEnabled ? -1 : 1;
            }
            if (a.nextTriggerMillis <= 0 && b.nextTriggerMillis > 0) return 1;
            if (a.nextTriggerMillis > 0 && b.nextTriggerMillis <= 0) return -1;
            return Long.compare(a.nextTriggerMillis, b.nextTriggerMillis);
        });

        if (reminders.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Chưa có lịch nhắc việc nào.\nChạm + để thêm lời nhắc mới.");
            empty.setPadding(dp(16), dp(24), dp(16), dp(16));
            empty.setTextSize(14);
            empty.setTextColor(ContextCompat.getColor(activity, R.color.workspace_muted));
            list.addView(empty);
            return;
        }

        for (Reminder reminder : reminders) {
            LinearLayout card = card(list);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));

            LinearLayout info = new LinearLayout(activity);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

            TextView title = new TextView(activity);
            title.setText(reminder.title);
            title.setTextSize(16);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
            info.addView(title);

            TextView sub = new TextView(activity);
            String desc = reminder.time + " · " + reminder.getRepeatDescription();
            sub.setText(desc);
            sub.setTextSize(13);
            sub.setTextColor(ContextCompat.getColor(activity, R.color.workspace_muted));
            info.addView(sub);

            TextView next = new TextView(activity);
            next.setText("Lần tới: " + reminder.getNextTriggerDisplay());
            next.setTextSize(12);
            next.setTextColor(ContextCompat.getColor(activity, reminder.isEnabled && reminder.nextTriggerMillis > 0
                    ? R.color.navigation_blue : R.color.workspace_muted));
            info.addView(next);

            card.addView(info);

            // Công tắc Bật/Tắt trực tiếp trên từng dòng (UC11)
            SwitchCompat sw = new SwitchCompat(activity);
            sw.setChecked(reminder.isEnabled);
            sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                reminder.isEnabled = isChecked;
                ReminderScheduler.schedule(activity, reminder);
                refreshCalendar();
            });
            card.addView(sw);

            card.setOnClickListener(v -> {
                if (reminder.isFromEvent) {
                    new AlertDialog.Builder(activity)
                            .setTitle(reminder.title)
                            .setMessage("Lời nhắc này được liên kết từ sự kiện lịch.\nBạn có muốn mở sự kiện để chỉnh sửa không?")
                            .setPositiveButton("Mở sự kiện", (d, w) -> {
                                EventEditorDialog.create(reminder.targetId, reminder.date)
                                        .show(activity.getSupportFragmentManager(), "event_editor");
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    ReminderEditorDialog.create(reminder.id).show(activity.getSupportFragmentManager(), "reminder_editor");
                }
            });
        }
    }

    // =========================================================================
    // DANH SÁCH / BỘ LỌC CÁ NHÂN & CÁC CHỨC NĂNG BỔ TRỢ
    // =========================================================================
    private void entryActions(LocalStore.Entry entry) {
        new AlertDialog.Builder(activity).setTitle(entry.title).setItems(new String[]{"Sửa", "Xóa"}, (dialog, which) -> {
            if (which == 0) editor(entry.type, entry.id, entry.date);
            else new AlertDialog.Builder(activity).setTitle("Xóa “" + entry.title + "”?")
                    .setMessage("Thao tác này xóa mục. Ghi chú hoạt động đã lưu vẫn được giữ lại.")
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (confirm, button) -> {
                        store.delete(entry.id); refresh();
                    }).show();
        }).show();
    }

    private void week(LinearLayout container, Calendar day, Runnable changed) {
        if (container == null) return;
        container.removeAllViews();
        Calendar start = monday(day);
        String[] names = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        for (int i = 0; i < 7; i++) {
            Calendar date = (Calendar) start.clone(); date.add(Calendar.DAY_OF_MONTH, i);
            LinearLayout button = new LinearLayout(activity);
            button.setOrientation(LinearLayout.VERTICAL);
            button.setGravity(Gravity.CENTER);
            int nameColor = ContextCompat.getColor(activity, R.color.workspace_accent);
            TextView weekday = new TextView(activity);
            weekday.setText(names[i]); weekday.setTextSize(12); weekday.setTextColor(nameColor);
            weekday.setGravity(Gravity.CENTER); weekday.setIncludeFontPadding(false);
            button.addView(weekday, new LinearLayout.LayoutParams(-1, -2));
            TextView number = new TextView(activity);
            number.setText(String.valueOf(date.get(Calendar.DAY_OF_MONTH))); number.setTextSize(22);
            number.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
            number.setGravity(Gravity.CENTER); number.setIncludeFontPadding(false);
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(-1, -2);
            numberParams.topMargin = dp(2); button.addView(number, numberParams);
            button.setClickable(true); button.setFocusable(true);
            button.setContentDescription(new SimpleDateFormat("EEEE dd/MM/yyyy", vietnamese).format(date.getTime()));
            LinearLayout.LayoutParams chip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            chip.setMargins(dp(2), 0, dp(2), 0);
            button.setLayoutParams(chip);
            button.setBackgroundTintList(null);
            button.setBackgroundResource(day(date).equals(day(day)) ? R.drawable.day_selected : R.drawable.control_gray);
            button.setOnClickListener(v -> { day.setTime(date.getTime()); week(container, day, changed); changed.run(); });
            container.addView(button);
        }
    }

    public void lists() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_lists, null);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        view.findViewById(R.id.listToday).setOnClickListener(v -> {
            selected.setTime(new Date()); habitsOnly = false; collapsed = false;
            activity.showSelectedDay(); dialog.dismiss(); selectTab(0);
        });
        int[] ids = {R.id.listAllTasks, R.id.listPending, R.id.listCompleted, R.id.listHabits};
        for (int i = 0; i < ids.length; i++) {
            final int which = i;
            view.findViewById(ids[i]).setOnClickListener(v -> {
                if (which == 3) showAllHabits();
                else {
                    habitsOnly = false; collapsed = false;
                    activity.setTaskStatusFilter(which == 0 ? 0 : which);
                    selectTab(0);
                }
                dialog.dismiss();
            });
        }
        Button dateFilterButton = view.findViewById(R.id.filterTaskDate);
        String[] dateFilterLabels = {"theo màn hình", "tất cả ngày", "quá hạn", "chưa lên lịch"};
        int dateFilter = Math.max(0, Math.min(activity.getDateFilter(), dateFilterLabels.length - 1));
        dateFilterButton.setText("Ngày thực hiện: " + dateFilterLabels[dateFilter]);
        dateFilterButton.setOnClickListener(v -> showTaskDateFilterDialog(dateFilterButton));
        Button priorityFilterButton = view.findViewById(R.id.filterTaskPriority);
        int currentPriorityFilter = activity.getPriorityFilter();
        priorityFilterButton.setText("Mức ưu tiên: " + (currentPriorityFilter == 0
                ? "tất cả" : TaskPriority.label(currentPriorityFilter)));
        priorityFilterButton.setOnClickListener(v -> showTaskPriorityFilterDialog(priorityFilterButton));
        LinearLayout rows = view.findViewById(R.id.customLists);
        TaskListUi.bind(activity, rows, dialog);
        for (LocalStore.Entry entry : store.entries(null)) {
            if (!"filter".equals(entry.type)) continue;
            LinearLayout card = card(rows);
            action(card, entry.title, () -> {
                habitsOnly = false;
                activity.useTaskFilter(Integer.parseInt(entry.time), entry.body, "");
                dialog.dismiss(); selectTab(0);
            });
            action(card, "Sửa / Xóa", () -> new AlertDialog.Builder(activity).setTitle(entry.title)
                    .setItems(new String[]{"Sửa", "Xóa"}, (d, which) -> {
                        dialog.dismiss();
                        if (which == 0) editFilter(entry);
                        else new AlertDialog.Builder(activity).setTitle("Xóa danh sách / bộ lọc?")
                                .setMessage("Các công việc bên trong vẫn được giữ lại.")
                                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (c, w) -> {
                                    store.delete(entry.id); activity.useTaskFilter(0, "", ""); refresh();
                                }).show();
                    }).show());
        }

        ((Button) view.findViewById(R.id.listAllTasks)).setText("Tất cả công việc                 " + tasks.size());
        ((Button) view.findViewById(R.id.listHabits)).setText("Tất cả thói quen                 " + store.entries("habit").size());
        EditText search = view.findViewById(R.id.drawerSearch);
        search.setText(((EditText) activity.findViewById(R.id.searchTasks)).getText());
        search.addTextChangedListener(watcher(value -> ((EditText) activity.findViewById(R.id.searchTasks)).setText(value)));
        view.findViewById(R.id.manageCollections).setOnClickListener(v -> { dialog.dismiss(); manageCollections(); });
        view.findViewById(R.id.habitsCompact).setOnClickListener(v -> {
            dialog.dismiss(); habitsOnly = true; collapsed = false; selectTab(0);
        });
        int[] reportButtons = {R.id.habitsWeek, R.id.habitsMonth, R.id.habitsYear};
        for (int i = 0; i < reportButtons.length; i++) {
            final int mode = i;
            view.findViewById(reportButtons[i]).setOnClickListener(v -> {
                dialog.dismiss();
                if (activity.getSupportFragmentManager().findFragmentByTag("report") == null)
                    ReportDialog.create(mode).show(activity.getSupportFragmentManager(), "report");
            });
        }
        view.findViewById(R.id.closeLists).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.START);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * .78), ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    public void showAllHabits() {
        habitsOnly = true;
        collapsed = false;
        activity.useTaskFilter(0, "", "");
        selectTab(0);
    }

    private void showTaskDateFilterDialog(Button filterButton) {
        String[] options = {"Theo ngày đang xem", "Tất cả ngày", "Quá hạn", "Chưa lên lịch"};
        new AlertDialog.Builder(activity).setTitle("Lọc theo ngày thực hiện")
                .setSingleChoiceItems(options, activity.getDateFilter(), (dialog, selectedFilter) -> {
                    activity.setDateFilter(selectedFilter);
                    filterButton.setText("Ngày thực hiện: " + new String[]{
                            "theo màn hình", "tất cả ngày", "quá hạn", "chưa lên lịch"
                    }[selectedFilter]);
                    dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void showTaskPriorityFilterDialog(Button filterButton) {
        String[] options = {"Tất cả mức ưu tiên", "Làm ngay", "Lên lịch", "Ủy quyền", "Loại bỏ"};
        new AlertDialog.Builder(activity).setTitle("Lọc theo mức ưu tiên")
                .setSingleChoiceItems(options, activity.getPriorityFilter(), (dialog, selectedFilter) -> {
                    activity.setPriorityFilter(selectedFilter);
                    filterButton.setText("Mức ưu tiên: " + (selectedFilter == 0
                            ? "tất cả" : TaskPriority.label(selectedFilter)));
                    dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void manageCollections() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_list_manager, null);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        LinearLayout rows = view.findViewById(R.id.managerRows);
        TaskListUi.bind(activity, rows, dialog);
        for (String title : new String[]{"Tất cả công việc", "Tất cả thói quen"}) {
            LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(58));
            rowParams.bottomMargin = dp(6); rows.addView(row, rowParams);
            ImageView icon = new ImageView(activity);
            icon.setImageResource(title.equals("Tất cả thói quen") ? R.drawable.badge_habits : R.drawable.badge_tasks);
            row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
            Button button = new Button(activity); button.setText(title);
            button.setTextSize(14);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, dp(52), 1);
            nameParams.leftMargin = dp(8); nameParams.rightMargin = dp(6); row.addView(button, nameParams);
            ImageButton info = new ImageButton(activity); info.setImageResource(R.drawable.ic_info);
            info.setBackgroundResource(R.drawable.control_gray); info.setPadding(dp(10), dp(10), dp(10), dp(10));
            info.setContentDescription("Thông tin " + title);
            row.addView(info, new LinearLayout.LayoutParams(dp(44), dp(44)));
            info.setOnClickListener(v -> new AlertDialog.Builder(activity).setTitle(title)
                    .setMessage("Danh sách mặc định. Bạn có thể tạo danh sách riêng hoặc bộ lọc bằng các nút bên dưới.")
                    .setPositiveButton(R.string.close, null).show());
            button.setOnClickListener(v -> {
                dialog.dismiss(); habitsOnly = title.equals("Tất cả thói quen"); collapsed = false;
                activity.useTaskFilter(0, "", ""); selectTab(0);
            });
        }
        for (LocalStore.Entry entry : store.entries(null)) {
            if (!"filter".equals(entry.type)) continue;
            LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
            rows.addView(row);
            Button name = new Button(activity); name.setText(entry.title);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(52), 1));
            ImageButton edit = new ImageButton(activity); edit.setImageResource(R.drawable.ic_edit);
            edit.setBackgroundResource(R.drawable.control_gray); edit.setPadding(dp(12), dp(12), dp(12), dp(12));
            edit.setContentDescription("Sửa " + entry.title);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44)); params.leftMargin = dp(6);
            row.addView(edit, params);
            edit.setOnClickListener(v -> { dialog.dismiss(); editFilter(entry); });
            name.setOnClickListener(v -> new AlertDialog.Builder(activity).setTitle(entry.title)
                    .setMessage("list".equals(entry.type) ? "Danh sách chứa các công việc bạn đã chọn." : "Bộ lọc tìm công việc theo tên và trạng thái.")
                    .setNegativeButton(R.string.close, null).setPositiveButton(R.string.delete, (d, which) ->
                            new AlertDialog.Builder(activity).setTitle("Xóa danh sách / bộ lọc?")
                                    .setMessage("Các công việc vẫn được giữ lại.").setNegativeButton(R.string.cancel, null)
                                    .setPositiveButton(R.string.delete, (confirm, selected) -> {
                                        store.delete(entry.id); dialog.dismiss(); activity.useTaskFilter(0, "", ""); manageCollections();
                                    }).show()).show());
        }
        view.findViewById(R.id.closeManager).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.managerHelp).setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("Danh sách / Bộ lọc").setMessage("Danh sách chứa công việc được chọn. Bộ lọc tự tìm công việc theo tên và trạng thái.")
                .setPositiveButton(R.string.close, null).show());
        view.findViewById(R.id.createList).setOnClickListener(v -> { dialog.dismiss(); activity.editTaskList(""); });
        view.findViewById(R.id.createFilter).setOnClickListener(v -> { dialog.dismiss(); editFilter(null); });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * .93), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void createCollection(String type, LocalStore.Entry old) {
        LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText name = new EditText(activity); name.setHint("Tên danh sách / bộ lọc"); name.setSingleLine(true);
        name.setText(old == null ? "" : old.title);
        layout.addView(name);
        EditText keyword = new EditText(activity);
        keyword.setHint("Tên công việc chứa…");
        keyword.setSingleLine(true);
        keyword.setText(old == null ? "" : old.body);
        layout.addView(keyword);
        new AlertDialog.Builder(activity).setTitle(old == null ? "Tạo danh sách / bộ lọc" : "Sửa danh sách / bộ lọc")
                .setView(layout).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String title = name.getText().toString().trim();
                    if (title.isEmpty()) {
                        name.setError("Nhập tên");
                        return;
                    }
                    LocalStore.Entry entry = old == null ? new LocalStore.Entry() : old;
                    entry.type = type;
                    entry.title = title;
                    entry.body = keyword.getText().toString().trim();
                    entry.date = LocalStore.today();
                    store.save(entry);
                    refresh();
                }).show();
    }

    private void editFilter(LocalStore.Entry old) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText name = new EditText(activity); name.setHint("Tên bộ lọc"); name.setSingleLine(true);
        name.setText(old == null ? "" : old.title); layout.addView(name);
        EditText keyword = new EditText(activity); keyword.setHint("Tên công việc chứa…"); keyword.setSingleLine(true);
        keyword.setText(old == null ? "" : old.body); layout.addView(keyword);
        Spinner status = new Spinner(activity);
        status.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Tất cả trạng thái", "Chưa hoàn thành", "Đã hoàn thành"}));
        layout.addView(status);
        if (old != null) status.setSelection(Integer.parseInt(old.time));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Bộ lọc")
                .setView(layout).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Vui lòng nhập tên"); return; }
            LocalStore.Entry entry = old == null ? new LocalStore.Entry() : old;
            entry.title = name.getText().toString().trim(); entry.type = "filter"; entry.date = LocalStore.today();
            entry.body = keyword.getText().toString().trim(); entry.time = String.valueOf(status.getSelectedItemPosition());
            store.save(entry); dialog.dismiss(); lists();
        }));
        dialog.show();
    }

    private void globalSearch() {
        LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(16), 0, dp(16), 0);
        EditText search = new EditText(activity); search.setHint("Nhập tên hoặc nội dung"); search.setSingleLine(true); layout.addView(search);
        ScrollView scroll = new ScrollView(activity); layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        LinearLayout rows = new LinearLayout(activity); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.search_everything).setView(layout).setNegativeButton(R.string.close, null).create();
        search.addTextChangedListener(watcher(value -> {
            rows.removeAllViews();
            if (value.trim().isEmpty()) return;
            for (Task task : tasks) if (vn.edu.taskmanager.TaskFilters.matchesSearch(task, value))
                action(rows, "Công việc · " + task.title, () -> { dialog.dismiss(); activity.onEdit(task); });
            for (LocalStore.Entry entry : store.entries(null)) {
                if (!Arrays.asList("habit", "note", "event").contains(entry.type)) continue;
                if (vn.edu.taskmanager.util.VietnameseUtils.containsIgnoreAccents(
                        entry.title + " " + entry.body, value))
                    action(rows, ("habit".equals(entry.type) ? "Thói quen" : "note".equals(entry.type) ? "Ghi chú" : "Sự kiện")
                            + " · " + entry.title, () -> { dialog.dismiss(); entryActions(entry); });
            }
            if (rows.getChildCount() == 0) text(rows, "Không có kết quả", 16);
        }));
        dialog.show();
    }

    private void timer() {
        if (activity.getSupportFragmentManager().findFragmentByTag("timer") == null)
            new TimerDialog().show(activity.getSupportFragmentManager(), "timer");
    }

    private String[] taskLabels(List<Task> entries) {
        String[] labels = new String[entries.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).title;
        return labels;
    }

    private String[] eventLabels(List<LocalStore.Entry> entries) {
        String[] labels = new String[entries.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).time + " " + entries.get(i).title;
        return labels;
    }
    private void report() {
        if (activity.getSupportFragmentManager().findFragmentByTag("report") == null)
            ReportDialog.create(0).show(activity.getSupportFragmentManager(), "report");
    }

    private void click(int id, Runnable action) {
        View v = activity.findViewById(id);
        if (v != null) v.setOnClickListener(view -> action.run());
    }

    private static String day(Calendar value) { return LocalStore.day(value.getTime()); }

    static Calendar monday(Calendar value) {
        Calendar result = (Calendar) value.clone();
        int offset = (result.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        result.add(Calendar.DAY_OF_MONTH, -offset); return result;
    }

    private int dp(int value) { return (int) (value * activity.getResources().getDisplayMetrics().density + .5f); }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12)); card.setBackgroundResource(R.drawable.task_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(10);
        parent.addView(card, params); return card;
    }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView text = new TextView(activity); text.setText(value); text.setTextSize(size);
        text.setTextColor(ContextCompat.getColor(activity, R.color.workspace_text));
        text.setPadding(dp(4), dp(8), dp(4), dp(8)); parent.addView(text); return text;
    }

    private void action(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(activity); button.setText(label); button.setAllCaps(false);
        button.setMinHeight(dp(48)); button.setOnClickListener(v -> action.run()); parent.addView(button);
    }

    private interface TextChanged { void changed(String value); }
    private static TextWatcher watcher(TextChanged callback) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { callback.changed(s.toString()); }
            public void afterTextChanged(Editable value) { }
        };
    }
}
