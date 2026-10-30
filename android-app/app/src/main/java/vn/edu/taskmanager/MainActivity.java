package vn.edu.taskmanager;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;
import vn.edu.taskmanager.data.LocalStore;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import vn.edu.taskmanager.data.Task;
import vn.edu.taskmanager.data.TaskListWithTasks;

public class MainActivity extends AppCompatActivity implements TaskAdapter.Listener {
    private TaskViewModel model;
    private TaskAdapter adapter;
    private TaskAdapter overdueAdapter, unscheduledAdapter;
    private final TaskHeaderAdapter overdueHeader = new TaskHeaderAdapter();
    private final TaskHeaderAdapter unscheduledHeader = new TaskHeaderAdapter();
    private boolean allDates;
    private List<Task> allTasks = new ArrayList<>();
    private int filter = 0;
    private String query = "";
    private String listId = "";
    private int priorityFilter;
    private int dateFilter = TaskFilters.DATE_CURRENT_VIEW;
    private WorkspaceController workspace;
    private String accountStorageNamespace;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener syncListener = (preferences, key) -> {
        if ("status".equals(key)) runOnUiThread(() -> {
            String status = preferences.getString("status", "");
            if (workspace != null) workspace.refreshSyncStatus();
            if (status.startsWith("Chưa lưu") || status.startsWith("Chưa đồng bộ"))
                Toast.makeText(this, status, Toast.LENGTH_LONG).show();
        });
    };
    private final android.os.Handler dayHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable dayCheck = new Runnable() {
        @Override public void run() { if (workspace != null) workspace.checkDayChange(); dayHandler.postDelayed(this, 30000); }
    };
    @Override protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        String currentStorage = vn.edu.taskmanager.auth.Session.storage(this, "personal_tasks.db");
        if (accountStorageNamespace != null && !accountStorageNamespace.equals(currentStorage)) {
            recreate();
            return;
        }
        vn.edu.taskmanager.sync.WorkspaceSync.state(this).registerOnSharedPreferenceChangeListener(syncListener);
        if (vn.edu.taskmanager.sync.WorkspaceSync.configured(this)) vn.edu.taskmanager.sync.WorkspaceSync.enqueue(this);
        if (workspace != null) {
            workspace.refreshSyncStatus();
            workspace.checkDayChange();
            workspace.refresh();
            workspace.refreshHabitsFromServer();
        }
        dayHandler.removeCallbacks(dayCheck); dayHandler.postDelayed(dayCheck, 30000);
    }
    @Override protected void onPause() { vn.edu.taskmanager.sync.WorkspaceSync.state(this).unregisterOnSharedPreferenceChangeListener(syncListener); dayHandler.removeCallbacks(dayCheck); super.onPause(); }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("open_tab") && workspace != null) {
            workspace.selectTab(intent.getIntExtra("open_tab", 0));
        }
    }


    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        accountStorageNamespace = vn.edu.taskmanager.auth.Session.storage(this, "personal_tasks.db");
        WorkspaceController.applyTheme(this);
        setContentView(R.layout.activity_main);
        model = new ViewModelProvider(this).get(TaskViewModel.class);
        RecyclerView list = findViewById(R.id.taskList);
        adapter = new TaskAdapter(this);
        overdueAdapter = new TaskAdapter(this); unscheduledAdapter = new TaskAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(adapter, overdueHeader, overdueAdapter,
                unscheduledHeader, unscheduledAdapter));
        if (state != null) {
            filter = state.getInt("filter", 0);
            query = state.getString("query", "");
            listId = state.getString("listId", "");
            allDates = state.getBoolean("allDates", false);
            priorityFilter = state.getInt("priorityFilter", 0);
            dateFilter = state.getInt("dateFilter", TaskFilters.DATE_CURRENT_VIEW);
        }
        workspace = new WorkspaceController(this, state);
        if (getIntent() != null && getIntent().hasExtra("open_tab")) {
            workspace.selectTab(getIntent().getIntExtra("open_tab", 0));
        }
        model.taskLists.observe(this, lists -> renderTasks());
        model.getHistoryChanged().observe(this, ignored -> workspace.refresh());
        model.tasks.observe(this, tasks -> {
            allTasks = tasks;
            workspace.updateTasks(tasks);
            renderTasks();
        });
        EditText search = findViewById(R.id.searchTasks);
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                renderTasks();
                workspace.searchChanged();
            }
            public void afterTextChanged(Editable value) { }
        });
        findViewById(R.id.filterTasks).setOnClickListener(v -> {
            workspace.lists();
        });
        findViewById(R.id.showStats).setOnClickListener(v -> {
            int done = 0;
            for (Task task : allTasks) if (task.completed) done++;
            new AlertDialog.Builder(this).setTitle("Thống kê công việc")
                    .setMessage("Tổng cộng: " + allTasks.size() + "\nĐã hoàn thành: " + done
                            + "\nChưa hoàn thành: " + (allTasks.size() - done))
                    .setPositiveButton("Đóng", null).show();
        });
        model.getWriteError().observe(this, failed -> {
            if (Boolean.TRUE.equals(failed)) {
                Toast.makeText(this, R.string.save_error, Toast.LENGTH_LONG).show();
                model.clearError();
            }
        });
        findViewById(R.id.addTask).setOnClickListener(v -> workspace.add());
        findViewById(R.id.editCurrentList).setOnClickListener(v -> editTaskList(listId));

    }

    private void renderTasks() {
        if (workspace == null) return;
        List<Task> visible = new ArrayList<>();
        List<Task> overdue = new ArrayList<>(), unscheduled = new ArrayList<>();
        String date = workspace.selectedDate(), today = LocalStore.today();
        TaskListWithTasks collection = findTaskList(listId);
        int done = 0;
        List<Task> source = listId.isEmpty() ? allTasks : collection == null ? new ArrayList<>() : new ArrayList<>(collection.tasks);
        source = new ArrayList<>(source);
        java.util.Collections.sort(source, (a, b) -> a.completed != b.completed ? (a.completed ? 1 : -1) : Long.compare(b.id, a.id));
        for (Task task : source) {
            if (!TaskFilters.matches(task, query, filter, priorityFilter)) continue;
            if (dateFilter == TaskFilters.DATE_OVERDUE) {
                if (TaskFilters.isOverdue(task, today)) visible.add(task);
                continue;
            }
            if (dateFilter == TaskFilters.DATE_UNSCHEDULED) {
                if (TaskFilters.isUnscheduled(task)) visible.add(task);
                continue;
            }
            if (dateFilter != TaskFilters.DATE_ALL && !allDates && !TaskSchedule.onDate(task, date)) {
                if (date.equals(today)) {
                    if (TaskSchedule.overdue(task, today)) overdue.add(task);
                    else if (task.scheduledDate.isEmpty()) unscheduled.add(task);
                }
                continue;
            }
            visible.add(task);
            if (task.completed) done++;
        }
        adapter.submitList(visible);
        overdueAdapter.submitList(overdue); unscheduledAdapter.submitList(unscheduled);
        overdueHeader.show(overdue.isEmpty() ? "" : "Quá hạn · " + overdue.size());
        unscheduledHeader.show(unscheduled.isEmpty() ? "" : "Chưa lên lịch · " + unscheduled.size());
        TextView empty = findViewById(R.id.emptyMessage);
        empty.setText(allDates ? "Không có công việc phù hợp.\nChạm + để thêm."
                : "Không có công việc cho ngày " + displayDate(date) + ".\nChạm + để lên lịch.");
        empty.setVisibility(visible.isEmpty() && overdue.isEmpty() && unscheduled.isEmpty() ? View.VISIBLE : View.GONE);
        TextView summary = findViewById(R.id.taskSummary);
        summary.setVisibility(workspace.isHabitsOnly() ? View.GONE : View.VISIBLE);
        int total = visible.size() + overdue.size() + unscheduled.size();
        for (Task task : unscheduled) if (task.completed) done++;
        summary.setText(collection == null ? getString(R.string.task_summary, total, done)
                : getString(R.string.list_completion, collection.completedCount(), collection.tasks.size()));
        findViewById(R.id.editCurrentList).setVisibility(collection != null && !workspace.isHabitsOnly() ? View.VISIBLE : View.GONE);
        String label = filter == 1 ? "Chưa hoàn thành" : filter == 2 ? "Đã hoàn thành" : "Tất cả";
        if (dateFilter == TaskFilters.DATE_OVERDUE) label = "Công việc quá hạn";
        else if (dateFilter == TaskFilters.DATE_UNSCHEDULED) label = "Chưa lên lịch";
        else if (priorityFilter != 0) label += " · " + TaskPriority.label(priorityFilter);
        if (collection != null) label = collection.list.title;
        if (!workspace.isHabitsOnly()) {
            ((TextView) findViewById(R.id.listHeading)).setText(!allDates ? "Công việc ngày " + displayDate(date)
                    : collection != null ? collection.list.title : !listId.isEmpty() ? getString(R.string.task_lists)
                    : dateFilter == TaskFilters.DATE_ALL && filter == 0 && priorityFilter == 0
                    ? "Tất cả công việc" : label);
            ((TextView) findViewById(R.id.taskCount)).setText(String.valueOf(visible.size() + overdue.size() + unscheduled.size()));
        }
    }

    public static String displayDate(String date) {
        if (date.isEmpty()) return "Chưa lên lịch";
        String[] parts = date.split("-");
        return parts[2] + "/" + parts[1] + "/" + parts[0];
    }
    public void renderTaskDates() { renderTasks(); }
    public void showSelectedDay() {
        workspace.showDailyOverview();
        allDates = false; filter = 0; listId = ""; query = "";
        dateFilter = TaskFilters.DATE_CURRENT_VIEW;
        ((EditText) findViewById(R.id.searchTasks)).setText(""); renderTasks();
    }

    public boolean isDailyOverview() { return !allDates; }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("filter", filter);
        state.putString("query", query);
        state.putString("listId", listId);
        state.putBoolean("allDates", allDates);
        state.putInt("priorityFilter", priorityFilter);
        state.putInt("dateFilter", dateFilter);
        workspace.saveState(state);
        super.onSaveInstanceState(state);
    }

    private void showEditor(long id, String title, String date, int priority) {
        if (getSupportFragmentManager().findFragmentByTag("task_editor") == null) {
            TaskEditorDialog dialog = TaskEditorDialog.newInstance(id, title, date, priority);
            dialog.requireArguments().putString("listId", listId);
            dialog.show(getSupportFragmentManager(), "task_editor");
        }
    }

    public void refreshWorkspace() { renderTasks(); workspace.refresh(); }
    void showAllHabits() { if (workspace != null) workspace.showAllHabits(); }
    void onHabitSaved(vn.edu.taskmanager.cloud.CloudModels.Habit habit) {
        if (workspace != null) workspace.onRemoteHabitSaved(habit);
    }
    void saveHabitToServer(Integer id, java.util.Map<String, Object> body,
            vn.edu.taskmanager.cloud.CloudRepository.Result<vn.edu.taskmanager.cloud.CloudModels.Habit> callback) {
        if (workspace != null) workspace.saveHabitToServer(id, body, callback);
        else callback.error("Màn hình quản lý thói quen chưa sẵn sàng");
    }

    @Override protected void onDestroy() {
        if (workspace != null) workspace.close();
        super.onDestroy();
    }
    public void entrySaved(LocalStore.Entry entry) { workspace.entrySaved(entry); }
    public void taskSaved(String date) {
        if (!listId.isEmpty()) { workspace.selectTab(0); return; }
        workspace.taskSaved(date);
    }
    public String currentTaskListId() { return listId; }
    public TaskListWithTasks findTaskList(String id) {
        List<TaskListWithTasks> lists = model.taskLists.getValue();
        if (lists != null) for (TaskListWithTasks item : lists) if (item.list.id.equals(id)) return item;
        return null;
    }
    public void openTaskList(String id) {
        workspace.showDailyOverview();
        useTaskFilter(0, "", id);
        workspace.selectTab(0);
    }
    public void editTaskList(String id) {
        if (getSupportFragmentManager().findFragmentByTag("list_editor") != null) return;
        TaskListWithTasks item = findTaskList(id);
        if (!id.isEmpty() && item == null) return;
        TaskListEditorDialog.create(item).show(getSupportFragmentManager(), "list_editor");
    }
    public void taskListDeleted(String id) {
        if (listId.equals(id)) { useTaskFilter(0, "", ""); workspace.refresh(); }
    }
    public void createTask() { showEditor(0, "", workspace.selectedDate(), 2); }
    public void selectWorkspaceTab(int tab) { workspace.selectTab(tab); }
    public void useTaskFilter(int status, String keyword, String collection) {
        allDates = true;
        filter = status; query = keyword; listId = collection;
        dateFilter = TaskFilters.DATE_ALL;
        ((EditText) findViewById(R.id.searchTasks)).setText(keyword);
        renderTasks();
    }
    public int getPriorityFilter() { return priorityFilter; }
    public void setPriorityFilter(int value) {
        priorityFilter = value;
        renderTasks();
    }
    public int getDateFilter() { return dateFilter; }
    public void setDateFilter(int value) {
        dateFilter = value;
        allDates = value != TaskFilters.DATE_CURRENT_VIEW;
        renderTasks();
    }
    public void setTaskStatusFilter(int value) {
        filter = value;
        listId = "";
        allDates = true;
        dateFilter = TaskFilters.DATE_ALL;
        renderTasks();
    }
    @Override public void onBackPressed() {
        if (!workspace.back()) super.onBackPressed();
    }

    @Override public void onEdit(Task task) { showEditor(task.id, task.title, task.scheduledDate, task.priority); }
    @Override public void onDelete(Task task) {
        DeleteTaskDialog.newInstance(task.id, task.title)
                .show(getSupportFragmentManager(), "delete_task");
    }
    @Override public void onCompleted(Task task, boolean completed) {
        model.setCompleted(task.id, completed);
    }
}
