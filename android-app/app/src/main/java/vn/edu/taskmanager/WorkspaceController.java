package vn.edu.taskmanager;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import vn.edu.taskmanager.data.LocalStore;
import vn.edu.taskmanager.data.Task;
import java.text.SimpleDateFormat;
import java.util.*;

/** Điều khiển các màn hình bổ sung; giao diện chính nằm trong res/layout. */
public class WorkspaceController {
    private final MainActivity activity;
    private final LocalStore store;
    private final SharedPreferences preferences;
    private final Locale vietnamese = new Locale("vi", "VN");
    private final Calendar selected = Calendar.getInstance();
    private final Calendar calendarDay = Calendar.getInstance();
    private int tab;
    private boolean habitsOnly, bookmarkedOnly;
    private String noteQuery = "";
    private String noteLabel = "";
    private boolean collapsed;
    private List<Task> tasks = new ArrayList<>();

    public WorkspaceController(MainActivity activity, Bundle state) {
        this.activity = activity;
        store = new LocalStore(activity);
        preferences = activity.getSharedPreferences("workspace_settings", 0);
        if (state != null) {
            tab = state.getInt("tab"); habitsOnly = state.getBoolean("habitsOnly");
            bookmarkedOnly = state.getBoolean("bookmarkedOnly");
            noteQuery = state.getString("noteQuery", "");
            noteLabel = state.getString("noteLabel", "");
            collapsed = state.getBoolean("collapsed", false);
            selected.setTimeInMillis(state.getLong("selectedDay", selected.getTimeInMillis()));
            calendarDay.setTimeInMillis(state.getLong("calendarDay", calendarDay.getTimeInMillis()));
        }
        int[] nav = {R.id.navTasks, R.id.navJournal, R.id.navCalendar, R.id.navSettings};
        for (int i = 0; i < nav.length; i++) {
            final int index = i;
            activity.findViewById(nav[i]).setOnClickListener(v -> selectTab(index));
        }
        click(R.id.addNote, () -> editor("note", null, LocalStore.today()));
        click(R.id.journalQuickWrite, () -> editor("note", null, LocalStore.today()));
        click(R.id.addEvent, () -> editor("event", null, day(calendarDay)));
        click(R.id.openTimer, this::timer);
        click(R.id.openReport, this::report);
        click(R.id.settingsTimer, this::timer);
        click(R.id.settingsReport, this::report);
        click(R.id.manageLists, this::manageCollections);
        click(R.id.settingsSearch, this::globalSearch);
        click(R.id.help, () -> new AlertDialog.Builder(activity).setTitle(R.string.help)
                .setMessage("Công việc: chạm + để thêm; chạm tên để sửa/xóa, chạm ô bên phải để hoàn thành.\n\n"
                        + "Menu ☰: chọn công việc, thói quen, danh sách hoặc bộ lọc.\n\n"
                        + "Thói quen: chọn ngày rồi đánh dấu hoàn thành. Báo cáo chỉ tính từ ngày tạo.\n\n"
                        + "Nhật ký: thêm ghi chú, tìm kiếm hoặc đánh dấu trang.\n\n"
                        + "Lịch: tạo sự kiện theo ngày và giờ; chạm sự kiện để sửa/xóa.\n\n"
                        + "Dữ liệu lưu trên thiết bị. Gỡ app hoặc xóa dữ liệu ứng dụng sẽ xóa dữ liệu.")
                .setPositiveButton(R.string.close, null).show());
        click(R.id.themeChoice, () -> new AlertDialog.Builder(activity).setTitle("Giao diện")
                .setSingleChoiceItems(new String[]{"Sáng", "Tối", "Theo hệ thống"},
                        preferences.getInt("theme", 0), (dialog, which) -> {
                            preferences.edit().putInt("theme", which).apply();
                            dialog.dismiss();
                            applyTheme(activity);
                        }).setNegativeButton(R.string.cancel, null).show());
        click(R.id.journalBookmark, () -> {
            bookmarkedOnly = !bookmarkedOnly;
            refreshJournal();
        });
        click(R.id.journalItems, () -> new AlertDialog.Builder(activity).setTitle("Hiển thị mục")
                .setItems(new String[]{"Tất cả mục", "Ghi chú đã đánh dấu"}, (dialog, which) -> {
                    bookmarkedOnly = which == 1; refreshJournal();
                }).show());
        click(R.id.journalFilter, () -> {
            Set<String> labels = new TreeSet<>();
            for (LocalStore.Entry entry : store.entries("note")) if (!entry.label.isEmpty()) labels.add(entry.label);
            List<String> choices = new ArrayList<>(); choices.add("Tất cả nhãn"); choices.addAll(labels);
            new AlertDialog.Builder(activity).setTitle("Nhãn ghi chú")
                    .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                        noteLabel = which == 0 ? "" : choices.get(which); refreshJournal();
                    }).show();
        });
        click(R.id.journalSearchToggle, () -> {
            View searchView = activity.findViewById(R.id.journalSearch);
            searchView.setVisibility(searchView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            if (searchView.getVisibility() == View.VISIBLE) searchView.requestFocus();
        });
        click(R.id.journalMore, () -> new AlertDialog.Builder(activity).setTitle("Nhật ký")
                .setItems(new String[]{"Thêm ghi chú", "Tìm kiếm", "Tất cả ghi chú", "Bộ lọc và nhãn"}, (dialog, which) -> {
                    if (which == 0) editor("note", null, LocalStore.today());
                    else if (which == 1) { activity.findViewById(R.id.journalSearch).setVisibility(View.VISIBLE); activity.findViewById(R.id.journalSearch).requestFocus(); }
                    else if (which == 2) { noteLabel = ""; bookmarkedOnly = false; ((EditText) activity.findViewById(R.id.journalSearch)).setText(""); refreshJournal(); }
                    else {
                        View tools = activity.findViewById(R.id.journalTools);
                        tools.setVisibility(tools.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                }).show());
        click(R.id.calendarMenu, this::lists);
        click(R.id.calendarMore, () -> new AlertDialog.Builder(activity).setTitle("Lịch")
                .setItems(new String[]{"Thêm sự kiện", "Chọn ngày", "Về hôm nay"}, (dialog, which) -> {
                    if (which == 0) editor("event", null, day(calendarDay));
                    else activity.findViewById(which == 1 ? R.id.calendarDate : R.id.calendarToday).performClick();
                }).show());
        click(R.id.settingsMenu, this::lists);
        click(R.id.settingsStar, () -> new AlertDialog.Builder(activity).setTitle(R.string.app_name)
                .setMessage(R.string.local_account_detail).setPositiveButton(R.string.close, null).show());
        click(R.id.collapseTasks, () -> { collapsed = !collapsed; refresh(); });
        EditText search = activity.findViewById(R.id.journalSearch);
        search.setText(noteQuery);
        search.addTextChangedListener(watcher(value -> { noteQuery = value; refreshJournal(); }));
        click(R.id.calendarPrevious, () -> { calendarDay.add(Calendar.DAY_OF_MONTH, -7); refreshCalendar(); });
        click(R.id.calendarNext, () -> { calendarDay.add(Calendar.DAY_OF_MONTH, 7); refreshCalendar(); });
        click(R.id.calendarToday, () -> { calendarDay.setTime(new Date()); refreshCalendar(); });
        click(R.id.calendarDate, () -> new DatePickerDialog(activity, (picker, year, month, date) -> {
            calendarDay.set(year, month, date); refreshCalendar();
        }, calendarDay.get(Calendar.YEAR), calendarDay.get(Calendar.MONTH), calendarDay.get(Calendar.DAY_OF_MONTH)).show());
        click(R.id.currentDate, () -> new DatePickerDialog(activity, (picker, year, month, date) -> {
            selected.set(year, month, date); activity.showSelectedDay(); refresh();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show());
        selectTab(tab);
    }

    public static void applyTheme(MainActivity activity) {
        int mode = activity.getSharedPreferences("workspace_settings", 0).getInt("theme", 0);
        int desired = mode == 0 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 1 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != desired) AppCompatDelegate.setDefaultNightMode(desired);
    }
    public void saveState(Bundle state) {
        state.putInt("tab", tab); state.putBoolean("habitsOnly", habitsOnly);
        state.putBoolean("bookmarkedOnly", bookmarkedOnly); state.putString("noteQuery", noteQuery);
        state.putString("noteLabel", noteLabel); state.putBoolean("collapsed", collapsed);
        state.putLong("selectedDay", selected.getTimeInMillis()); state.putLong("calendarDay", calendarDay.getTimeInMillis());
    }
    public boolean isHabitsOnly() { return habitsOnly; }
    public void showDailyOverview() { habitsOnly = false; collapsed = false; }
    public String selectedDate() { return day(selected); }
    public boolean back() {
        if (tab == 0) return false;
        selectTab(0); return true;
    }
    public void updateTasks(List<Task> value) { tasks = value; refresh(); }
    public void entrySaved(LocalStore.Entry entry) {
        if ("habit".equals(entry.type)) {
            activity.showSelectedDay(); selectTab(0);
        } else if ("note".equals(entry.type)) {
            bookmarkedOnly = false;
            noteLabel = "";
            ((EditText) activity.findViewById(R.id.journalSearch)).setText(""); selectTab(1);
        } else if ("event".equals(entry.type)) {
            String[] parts = entry.date.split("-");
            calendarDay.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            selectTab(2);
        }
    }
    public void taskSaved(String date) {
        habitsOnly = false; collapsed = false;
        if (date.isEmpty()) {
            selected.setTime(new Date()); activity.showSelectedDay();
        }
        else {
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
            int color = androidx.core.content.ContextCompat.getColor(activity, i == index ? R.color.navigation_blue : R.color.workspace_muted);
            ((ImageButton) button).setImageTintList(android.content.res.ColorStateList.valueOf(color));
        }
        refresh();
    }
    public void refresh() {
        week(activity.findViewById(R.id.taskWeek), selected, () -> { activity.showSelectedDay(); refresh(); });
        refreshDate(); refreshHabits(); refreshJournal();
        if (tab == 2) refreshCalendar();
        activity.findViewById(R.id.taskListContainer).setVisibility(habitsOnly || collapsed ? View.GONE : View.VISIBLE);
        boolean showHabits = (habitsOnly || activity.isDailyOverview()) && !collapsed;
        activity.findViewById(R.id.habitScroll).setVisibility(showHabits ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.habitHeading).setVisibility(showHabits && !habitsOnly ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.collapseTasks).setRotation(collapsed ? 180 : 0);
        ((Button) activity.findViewById(R.id.themeChoice)).setText(new String[]{"Sáng  ﹀", "Tối  ﹀", "Hệ thống  ﹀"}[preferences.getInt("theme", 0)]);
        activity.renderTaskDates();
    }
    private void refreshDate() {
        ((TextView) activity.findViewById(R.id.currentDate)).setText(
                day(selected).equals(LocalStore.today()) ? "Hôm nay" : new SimpleDateFormat("dd/MM", vietnamese).format(selected.getTime()));
    }
    public void add() {
        new AlertDialog.Builder(activity).setTitle("Thêm mới")
                .setItems(new String[]{"Công việc", "Thói quen hằng ngày", "Ghi chú nhật ký", "Sự kiện lịch"}, (dialog, which) -> {
                    if (which == 0) activity.createTask();
                    else editor(which == 1 ? "habit" : which == 2 ? "note" : "event", null, day(selected));
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
            // Thói quen lặp mỗi ngày từ ngày tạo, không chỉ ngày nhập trong form.
            if (date.compareTo(habit.created) < 0) continue;
            if (!habit.title.toLowerCase(Locale.ROOT).contains(keyword)) continue;
            visible++;
            if (habit.days.contains(date)) done++;
            LinearLayout card = card(rows);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            ImageView icon = new ImageView(activity);
            icon.setImageResource(R.drawable.ic_leaf);
            icon.setBackgroundResource(R.drawable.control_purple);
            icon.setPadding(dp(6), dp(6), dp(6), dp(6));
            card.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView title = new TextView(activity);
            title.setText(habit.title); title.setTextSize(16);
            title.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.workspace_text));
            title.setPadding(dp(8), 0, dp(8), 0);
            card.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
            title.setGravity(Gravity.CENTER_VERTICAL);
            card.setOnClickListener(v -> entryActions(habit));
            CheckBox check = new CheckBox(activity);
            check.setButtonDrawable(null);
            check.setBackgroundResource(R.drawable.completion_box);
            check.setContentDescription("Hoàn thành " + habit.title);
            check.setChecked(habit.days.contains(date));
            boolean available = date.compareTo(LocalStore.today()) <= 0 && date.compareTo(habit.created) >= 0;
            check.setEnabled(available);
            check.setOnCheckedChangeListener((button, checked) -> {
                store.checkHabit(habit, date, checked); refreshHabits();
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
    private void refreshJournal() {
        LinearLayout rows = activity.findViewById(R.id.journalEntries); rows.removeAllViews();
        ((TextView) activity.findViewById(R.id.journalToday)).setText("Hôm nay, "
                + new SimpleDateFormat("dd/MM", vietnamese).format(new Date()));
        ((Button) activity.findViewById(R.id.journalFilter)).setText((noteLabel.isEmpty() ? "Tất cả nhãn" : noteLabel) + "  ﹀");
        ((Button) activity.findViewById(R.id.journalItems)).setText(bookmarkedOnly ? "Đã đánh dấu  ﹀" : "Tất cả mục  ﹀");
        ((ImageButton) activity.findViewById(R.id.journalBookmark)).setImageTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(activity, bookmarkedOnly ? R.color.workspace_accent : R.color.workspace_text)));
        List<LocalStore.Entry> notes = store.entries("note");
        // Nhóm theo ngày viết, không đổi vị trí khi chỉ sửa nội dung hoặc đánh dấu.
        Collections.sort(notes, (a, b) -> (b.date + " " + b.time).compareTo(a.date + " " + a.time));
        Map<String, Integer> monthCounts = new LinkedHashMap<>();
        List<LocalStore.Entry> visibleNotes = new ArrayList<>();
        for (LocalStore.Entry note : notes) {
            if (bookmarkedOnly && !note.bookmarked) continue;
            if (!noteLabel.isEmpty() && !noteLabel.equals(note.label)) continue;
            if (!(note.title + " " + note.body).toLowerCase(Locale.ROOT).contains(noteQuery.trim().toLowerCase(Locale.ROOT))) continue;
            visibleNotes.add(note);
            String month = note.date.length() >= 7 ? note.date.substring(0, 7) : "";
            Integer monthCount = monthCounts.get(month);
            monthCounts.put(month, monthCount == null ? 1 : monthCount + 1);
        }
        int count = 0;
        String previousMonth = null;
        for (LocalStore.Entry note : visibleNotes) {
            String month = note.date.length() >= 7 ? note.date.substring(0, 7) : "";
            if (!month.equals(previousMonth)) {
                journalMonthHeading(rows, month, monthCounts.get(month));
                previousMonth = month;
            }
            count++;
            View card = activity.getLayoutInflater().inflate(R.layout.item_journal, rows, false);
            rows.addView(card);
            ((TextView) card.findViewById(R.id.noteTitle)).setText(note.title);
            ((TextView) card.findViewById(R.id.noteMeta)).setText(MainActivity.displayDate(note.date)
                    + (note.time.isEmpty() ? "" : " · " + note.time));
            ((TextView) card.findViewById(R.id.notePreview)).setText(note.body.isEmpty() ? "Chưa có nội dung. Chạm để viết thêm." : note.body);
            TextView label = card.findViewById(R.id.noteLabel);
            label.setText(note.label + (note.bookmarked ? (note.label.isEmpty() ? "" : " · ") + "Đã đánh dấu" : ""));
            label.setVisibility(label.getText().length() == 0 ? View.GONE : View.VISIBLE);
            View.OnClickListener read = v -> {
                if (activity.getSupportFragmentManager().findFragmentByTag("journal_reader") == null)
                    JournalReaderDialog.create(note.id).show(activity.getSupportFragmentManager(), "journal_reader");
            };
            card.setOnClickListener(read);
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(activity).setTitle(note.title)
                        .setItems(new String[]{"Sửa bài nhật ký", note.bookmarked ? "Bỏ đánh dấu" : "Đánh dấu", "Xóa bài nhật ký"}, (dialog, which) -> {
                            if (which == 0) editor("note", note.id, note.date);
                            else if (which == 1) { note.bookmarked = !note.bookmarked; store.save(note); refreshJournal(); }
                            else new AlertDialog.Builder(activity).setTitle("Xóa bài nhật ký?")
                                    .setMessage(note.title).setNegativeButton(R.string.cancel, null)
                                    .setPositiveButton(R.string.delete, (confirm, button) -> { store.delete(note.id); refreshJournal(); }).show();
                        }).show();
                return true;
            });
        }
        ((TextView) activity.findViewById(R.id.journalSummary)).setText(count + " bài nhật ký · Mỗi ngày, một trang của bạn");
        if (count == 0) {
            rows.setGravity(Gravity.CENTER);
            TextView empty = text(rows, notes.isEmpty() ? "Không có ghi chú hoặc hoạt động\nChạm nút + để thêm" : "Không có ghi chú phù hợp", 17);
            empty.setGravity(Gravity.CENTER); empty.setLineSpacing(dp(8), 1);
            android.text.SpannableString message = new android.text.SpannableString(empty.getText());
            int firstLine = message.toString().indexOf('\n');
            message.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0,
                    firstLine < 0 ? message.length() : firstLine, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            empty.setText(message);
        } else {
            rows.setGravity(Gravity.TOP);
        }
    }
    private void journalMonthHeading(LinearLayout rows, String month, int count) {
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, dp(16), 0, dp(12));
        TextView title = new TextView(activity);
        String[] parts = month.split("-");
        title.setText(parts.length == 2 ? "Tháng " + Integer.parseInt(parts[1]) + ", " + parts[0] : "Chưa có ngày");
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.workspace_text));
        heading.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView total = new TextView(activity);
        total.setText(count + " bài viết");
        total.setTextSize(13);
        total.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.workspace_muted));
        heading.addView(total);
        rows.addView(heading);
    }
    private void entryActions(LocalStore.Entry entry) {
        new AlertDialog.Builder(activity).setTitle(entry.title).setItems(new String[]{"Sửa", "Xóa"}, (dialog, which) -> {
            if (which == 0) editor(entry.type, entry.id, entry.date);
            else new AlertDialog.Builder(activity).setTitle("Xóa “" + entry.title + "”?")
                    .setMessage("Thao tác này xóa mục và lịch sử ghi nhận của mục đó.")
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (confirm, button) -> {
                        store.delete(entry.id); refresh();
                    }).show();
        }).show();
    }
    private void refreshCalendar() {
        ((Button) activity.findViewById(R.id.calendarDate)).setText(
                new SimpleDateFormat("'Tháng' MM yyyy", vietnamese).format(calendarDay.getTime()));
        week(activity.findViewById(R.id.calendarWeek), calendarDay, this::refreshCalendar);
        Calendar monday = monday(calendarDay);
        List<LocalStore.Entry> events = store.entries("event");
        ((TextView) activity.findViewById(R.id.calendarAgenda)).setText("Lịch sự kiện · " + day(calendarDay)
                + "\nChạm ô giờ để thêm; chạm sự kiện để sửa/xóa.");
        LinearLayout grid = activity.findViewById(R.id.calendarGrid); grid.removeAllViews();
        for (int hour = 0; hour < 24; hour++) {
            final int slot = hour;
            LinearLayout row = new LinearLayout(activity); grid.addView(row);
            TextView label = text(row, String.format(Locale.ROOT, "%02d:00", hour), 11);
            label.setGravity(Gravity.TOP); label.setPadding(dp(2), dp(4), 0, 0);
            label.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(76)));
            for (int weekday = 0; weekday < 7; weekday++) {
                Calendar date = (Calendar) monday.clone(); date.add(Calendar.DAY_OF_MONTH, weekday);
                String key = day(date);
                List<LocalStore.Entry> matches = new ArrayList<>();
                for (LocalStore.Entry event : events)
                    if (key.equals(event.date) && Integer.parseInt(event.time.split(":")[0]) == hour) matches.add(event);
                TextView cell = new TextView(activity);
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, dp(76), 1));
                cell.setBackgroundResource(R.drawable.grid_cell); cell.setPadding(dp(3), dp(4), dp(2), 0);
                cell.setTextSize(10); cell.setTextColor(Color.rgb(35, 35, 35));
                if (!matches.isEmpty()) {
                    StringBuilder title = new StringBuilder();
                    for (LocalStore.Entry event : matches) title.append(event.time).append(" ").append(event.title).append('\n');
                    cell.setText(title); cell.setBackgroundColor(Color.rgb(213, 210, 242));
                }
                cell.setContentDescription(key + " " + slot + " giờ, " + matches.size() + " sự kiện");
                cell.setOnClickListener(v -> {
                    if (matches.isEmpty()) {
                        if (activity.getSupportFragmentManager().findFragmentByTag("entry_editor") == null) {
                            EntryEditorDialog editor = EntryEditorDialog.create("event", null, key);
                            editor.requireArguments().putString("time", String.format(Locale.ROOT, "%02d:00", slot));
                            editor.show(activity.getSupportFragmentManager(), "entry_editor");
                        }
                    }
                    else if (matches.size() == 1) entryActions(matches.get(0));
                    else new AlertDialog.Builder(activity).setTitle(key).setItems(
                            eventLabels(matches),
                            (dialog, which) -> entryActions(matches.get(which))).show();
                });
                row.addView(cell);
            }
        }
    }
    private void week(LinearLayout container, Calendar day, Runnable changed) {
        container.removeAllViews();
        Calendar start = monday(day);
        String[] names = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        for (int i = 0; i < 7; i++) {
            Calendar date = (Calendar) start.clone(); date.add(Calendar.DAY_OF_MONTH, i);
            LinearLayout button = new LinearLayout(activity);
            button.setOrientation(LinearLayout.VERTICAL);
            button.setGravity(Gravity.CENTER);
            boolean calendar = container.getId() == R.id.calendarWeek;
            int nameColor = androidx.core.content.ContextCompat.getColor(activity, calendar ? R.color.workspace_text : R.color.workspace_accent);
            TextView weekday = new TextView(activity);
            weekday.setText(names[i]); weekday.setTextSize(12); weekday.setTextColor(nameColor);
            weekday.setGravity(Gravity.CENTER); weekday.setIncludeFontPadding(false);
            button.addView(weekday, new LinearLayout.LayoutParams(-1, -2));
            TextView number = new TextView(activity);
            number.setText(String.valueOf(date.get(Calendar.DAY_OF_MONTH))); number.setTextSize(22);
            number.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.workspace_text));
            number.setGravity(Gravity.CENTER); number.setIncludeFontPadding(false);
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(-1, -2);
            numberParams.topMargin = dp(2); button.addView(number, numberParams);
            button.setClickable(true); button.setFocusable(true);
            button.setContentDescription(new SimpleDateFormat("EEEE dd/MM/yyyy", vietnamese).format(date.getTime()));
            LinearLayout.LayoutParams chip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            chip.setMargins(calendar ? 0 : dp(2), 0, calendar ? 0 : dp(2), 0);
            button.setLayoutParams(chip);
            button.setBackgroundTintList(null);
            button.setBackgroundResource(day(date).equals(day(day)) ? calendar ? R.drawable.control_soft : R.drawable.day_selected
                    : calendar ? android.R.color.transparent : R.drawable.control_gray);
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
                habitsOnly = which == 3; collapsed = false;
                activity.useTaskFilter(which < 3 ? which : 0, "", "");
                dialog.dismiss(); selectTab(0);
            });
        }
        LinearLayout rows = view.findViewById(R.id.customLists);
        for (LocalStore.Entry entry : store.entries(null)) {
            if (!"list".equals(entry.type) && !"filter".equals(entry.type)) continue;
            LinearLayout card = card(rows);
            action(card, entry.title, () -> {
                habitsOnly = false;
                if ("list".equals(entry.type)) activity.useTaskFilter(0, "", entry.id);
                else activity.useTaskFilter(Integer.parseInt(entry.time), entry.body, "");
                dialog.dismiss(); selectTab(0);
            });
            action(card, "Sửa / Xóa", () -> new AlertDialog.Builder(activity).setTitle(entry.title)
                    .setItems(new String[]{"Sửa", "Xóa"}, (d, which) -> {
                        dialog.dismiss();
                        if (which == 0) createCollection(entry.type, entry);
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
    private void manageCollections() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_list_manager, null);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        LinearLayout rows = view.findViewById(R.id.managerRows);
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
            if (!"list".equals(entry.type) && !"filter".equals(entry.type)) continue;
            LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
            rows.addView(row);
            Button name = new Button(activity); name.setText(entry.title);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(52), 1));
            ImageButton edit = new ImageButton(activity); edit.setImageResource(R.drawable.ic_edit);
            edit.setBackgroundResource(R.drawable.control_gray); edit.setPadding(dp(12), dp(12), dp(12), dp(12));
            edit.setContentDescription("Sửa " + entry.title);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44)); params.leftMargin = dp(6);
            row.addView(edit, params);
            edit.setOnClickListener(v -> { dialog.dismiss(); createCollection(entry.type, entry); });
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
        view.findViewById(R.id.createList).setOnClickListener(v -> { dialog.dismiss(); createCollection("list", null); });
        view.findViewById(R.id.createFilter).setOnClickListener(v -> { dialog.dismiss(); createCollection("filter", null); });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * .93), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    private void createCollection(String type, LocalStore.Entry old) {
        LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText name = new EditText(activity); name.setHint("Tên danh sách / bộ lọc"); name.setSingleLine(true);
        name.setText(old == null ? "" : old.title); layout.addView(name);
        EditText keyword = new EditText(activity); keyword.setHint("Tên công việc chứa…"); keyword.setSingleLine(true);
        keyword.setText(old == null ? "" : old.body);
        Spinner status = new Spinner(activity);
        status.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Tất cả trạng thái", "Chưa hoàn thành", "Đã hoàn thành"}));
        if ("filter".equals(type)) {
            layout.addView(keyword); layout.addView(status);
            if (old != null) status.setSelection(Integer.parseInt(old.time));
        }
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("list".equals(type) ? "Danh sách công việc" : "Bộ lọc")
                .setView(layout).setNegativeButton(R.string.cancel, null).setPositiveButton("Tiếp tục", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Vui lòng nhập tên"); return; }
            LocalStore.Entry entry = old == null ? new LocalStore.Entry() : old;
            entry.title = name.getText().toString().trim(); entry.type = type; entry.date = LocalStore.today();
            if ("filter".equals(type)) {
                entry.body = keyword.getText().toString().trim(); entry.time = String.valueOf(status.getSelectedItemPosition());
                store.save(entry); dialog.dismiss(); lists();
            } else {
                dialog.dismiss();
                List<Task> snapshot = new ArrayList<>(tasks);
                boolean[] checks = new boolean[snapshot.size()];
                Set<String> previous = new HashSet<>(Arrays.asList((entry.body == null ? "" : entry.body).split(",")));
                for (int i = 0; i < checks.length; i++) checks[i] = previous.contains(String.valueOf(snapshot.get(i).id));
                new AlertDialog.Builder(activity).setTitle("Chọn công việc")
                        .setMultiChoiceItems(taskLabels(snapshot), checks,
                                (pick, which, checked) -> checks[which] = checked)
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, (pick, which) -> {
                            List<String> members = new ArrayList<>();
                            for (int i = 0; i < checks.length; i++) if (checks[i]) members.add(String.valueOf(snapshot.get(i).id));
                            entry.body = android.text.TextUtils.join(",", members); entry.time = "0";
                            store.save(entry); activity.refreshWorkspace(); lists();
                        }).show();
            }
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
            rows.removeAllViews(); String keyword = value.trim().toLowerCase(Locale.ROOT);
            if (keyword.isEmpty()) return;
            for (Task task : tasks) if (task.title.toLowerCase(Locale.ROOT).contains(keyword))
                action(rows, "Công việc · " + task.title, () -> { dialog.dismiss(); activity.onEdit(task); });
            for (LocalStore.Entry entry : store.entries(null)) {
                if (!Arrays.asList("habit", "note", "event").contains(entry.type)) continue;
                if ((entry.title + " " + entry.body).toLowerCase(Locale.ROOT).contains(keyword))
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
    private String[] eventLabels(List<LocalStore.Entry> entries) {
        String[] labels = new String[entries.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).time + " " + entries.get(i).title;
        return labels;
    }
    private String[] taskLabels(List<Task> entries) {
        String[] labels = new String[entries.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).title;
        return labels;
    }
    private void report() {
        if (activity.getSupportFragmentManager().findFragmentByTag("report") == null)
            new ReportDialog().show(activity.getSupportFragmentManager(), "report");
    }
    private void click(int id, Runnable action) { activity.findViewById(id).setOnClickListener(v -> action.run()); }
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
        text.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.workspace_text));
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
