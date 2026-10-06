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
import java.util.Locale;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import vn.edu.taskmanager.data.LocalStore;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import vn.edu.taskmanager.data.Task;

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
    private WorkspaceController workspace;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        }
        workspace = new WorkspaceController(this, state);
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
    }

    private void renderTasks() {
        if (workspace == null) return;
        List<Task> visible = new ArrayList<>();
        List<Task> overdue = new ArrayList<>(), unscheduled = new ArrayList<>();
        String date = workspace.selectedDate(), today = LocalStore.today();
        String keyword = query.trim().toLowerCase(Locale.ROOT);
        LocalStore.Entry collection = listId.isEmpty() ? null : new LocalStore(this).find(listId);
        Set<String> members = collection == null ? null : new HashSet<>(Arrays.asList(collection.body.split(",")));
        int done = 0;
        for (Task task : allTasks) {
            if (members != null && !members.contains(String.valueOf(task.id))) continue;
            if ((filter == 1 && task.completed) || (filter == 2 && !task.completed)) continue;
            if (!task.title.toLowerCase(Locale.ROOT).contains(keyword)) continue;
            if (!allDates && !TaskSchedule.onDate(task, date)) {
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
        ((TextView) findViewById(R.id.taskSummary)).setText(
                getString(R.string.task_summary, visible.size(), done));
        String label = filter == 1 ? "Chưa hoàn thành" : filter == 2 ? "Đã hoàn thành" : "Tất cả";
        if (collection != null) label = collection.title;
        if (!workspace.isHabitsOnly()) {
            ((TextView) findViewById(R.id.listHeading)).setText(!allDates ? "Công việc ngày " + displayDate(date)
                    : collection != null ? collection.title : filter == 0 ? "Tất cả công việc" : label);
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
        ((EditText) findViewById(R.id.searchTasks)).setText(""); renderTasks();
    }

    public boolean isDailyOverview() { return !allDates; }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("filter", filter);
        state.putString("query", query);
        state.putString("listId", listId);
        state.putBoolean("allDates", allDates);
        workspace.saveState(state);
        super.onSaveInstanceState(state);
    }

    private void showEditor(long id, String title, String date, int priority) {
        if (getSupportFragmentManager().findFragmentByTag("task_editor") == null) {
            TaskEditorDialog.newInstance(id, title, date, priority)
                    .show(getSupportFragmentManager(), "task_editor");
        }
    }

    public void refreshWorkspace() { renderTasks(); workspace.refresh(); }
    public void entrySaved(LocalStore.Entry entry) { workspace.entrySaved(entry); }
    public void taskSaved(String date) { workspace.taskSaved(date); }
    public void createTask() { showEditor(0, "", workspace.selectedDate(), 2); }
    public void selectWorkspaceTab(int tab) { workspace.selectTab(tab); }
    public void useTaskFilter(int status, String keyword, String collection) {
        allDates = true;
        filter = status; query = keyword; listId = collection;
        ((EditText) findViewById(R.id.searchTasks)).setText(keyword);
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
