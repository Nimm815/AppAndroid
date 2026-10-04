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
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        if (state != null) {
            filter = state.getInt("filter", 0);
            query = state.getString("query", "");
            listId = state.getString("listId", "");
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
        List<Task> visible = new ArrayList<>();
        String keyword = query.trim().toLowerCase(Locale.ROOT);
        LocalStore.Entry collection = listId.isEmpty() ? null : new LocalStore(this).find(listId);
        Set<String> members = collection == null ? null : new HashSet<>(Arrays.asList(collection.body.split(",")));
        int done = 0;
        for (Task task : allTasks) {
            if (members != null && !members.contains(String.valueOf(task.id))) continue;
            if ((filter == 1 && task.completed) || (filter == 2 && !task.completed)) continue;
            if (!task.title.toLowerCase(Locale.ROOT).contains(keyword)) continue;
            visible.add(task);
            if (task.completed) done++;
        }
        adapter.submitList(visible);
        TextView empty = findViewById(R.id.emptyMessage);
        empty.setText(allTasks.isEmpty() ? "Chưa có công việc.\nChạm nút + để thêm."
                : "Không có công việc phù hợp.");
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.taskSummary)).setText(
                getString(R.string.task_summary, visible.size(), done));
        String label = filter == 1 ? "Chưa hoàn thành" : filter == 2 ? "Đã hoàn thành" : "Tất cả";
        if (collection != null) label = collection.title;
        ((TextView) findViewById(R.id.listHeading)).setText("Danh sách việc cần làm · " + label);
        if (workspace != null && workspace.isHabitsOnly()) workspace.refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("filter", filter);
        state.putString("query", query);
        state.putString("listId", listId);
        workspace.saveState(state);
        super.onSaveInstanceState(state);
    }

    private void showEditor(long id, String title) {
        if (getSupportFragmentManager().findFragmentByTag("task_editor") == null) {
            TaskEditorDialog.newInstance(id, title)
                    .show(getSupportFragmentManager(), "task_editor");
        }
    }

    public void refreshWorkspace() { renderTasks(); workspace.refresh(); }
    public void entrySaved(LocalStore.Entry entry) { workspace.entrySaved(entry); }
    public void taskCreated() { workspace.taskCreated(); }
    public void createTask() { showEditor(0, ""); }
    public void useTaskFilter(int status, String keyword, String collection) {
        filter = status; query = keyword; listId = collection;
        ((EditText) findViewById(R.id.searchTasks)).setText(keyword);
        renderTasks();
    }
    @Override public void onBackPressed() {
        if (!workspace.back()) super.onBackPressed();
    }

    @Override public void onEdit(Task task) { showEditor(task.id, task.title); }
    @Override public void onDelete(Task task) {
        DeleteTaskDialog.newInstance(task.id, task.title)
                .show(getSupportFragmentManager(), "delete_task");
    }
    @Override public void onCompleted(Task task, boolean completed) {
        model.setCompleted(task.id, completed);
    }
}
