package vn.edu.taskmanager;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import vn.edu.taskmanager.data.Task;
import vn.edu.taskmanager.data.TaskDao;
import vn.edu.taskmanager.data.TaskDatabase;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskViewModel extends AndroidViewModel {
    private final TaskDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> writeError = new MutableLiveData<>(false);
    public final LiveData<List<Task>> tasks;
    private final MutableLiveData<Long> historyChanged = new MutableLiveData<>();
    public LiveData<Long> getHistoryChanged() { return historyChanged; }

    public TaskViewModel(@NonNull Application application) {
        super(application);
        dao = TaskDatabase.getInstance(application).taskDao();
        tasks = dao.observeTasks();
    }

    public LiveData<Boolean> getWriteError() { return writeError; }
    public void clearError() { writeError.setValue(false); }

    public void save(long id, String title, String date, int priority) {
        saveDetails(id, title, date, priority, null, null);
    }
    public void save(long id, String title, String date, int priority, int progress, String note) {
        if (progress < 0 || progress > 100) throw new IllegalArgumentException("Mức hoàn thành từ 0 đến 100");
        saveDetails(id, title, date, priority, progress, note);
    }
    private void saveDetails(long id, String title, String date, int priority, Integer progress, String note) {
        String cleaned = title.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Tên công việc không được trống");
        if (priority < 1 || priority > 4) throw new IllegalArgumentException("Nhóm quan trọng không hợp lệ");
        write(() -> {
            if (id == 0) {
                Task task = new Task(cleaned); task.scheduledDate = date; task.priority = priority;
                task.progress = progress == null ? 0 : progress; task.completed = task.progress == 100;
                task.note = note == null ? "" : note; dao.insert(task);
            } else {
                Task old = dao.findById(id);
                if (old != null) {
                    dao.editDetails(id, cleaned, date, priority,
                            progress == null ? old.progress : progress, note == null ? old.note : note);
                    if (progress != null && progress != old.progress) recordLateResult(id);
                }
            }
        });
    }
    public void setCompleted(long id, boolean completed) {
        write(() -> { dao.setCompleted(id, completed); recordLateResult(id); });
    }
    private void recordLateResult(long id) {
        Task task = dao.findById(id);
        String today = vn.edu.taskmanager.data.LocalStore.today();
        if (task != null && !task.scheduledDate.isEmpty() && task.scheduledDate.compareTo(today) < 0)
            new vn.edu.taskmanager.data.DailyHistory(getApplication()).recordTaskResult(task, today);
    }
    public void delete(long id) { write(() -> dao.delete(id)); }

    private void syncHistory() {
        new vn.edu.taskmanager.data.DailyHistory(getApplication()).sync(dao.allTasks(),
                new vn.edu.taskmanager.data.LocalStore(getApplication()).entries("habit"),
                vn.edu.taskmanager.data.LocalStore.today());
    }

    private void write(Runnable operation) {
        executor.execute(() -> {
            try { syncHistory(); operation.run(); syncHistory(); historyChanged.postValue(System.currentTimeMillis()); }
            catch (RuntimeException error) {
                android.util.Log.e("TaskViewModel", "Không thể lưu công việc", error);
                writeError.postValue(true);
            }
        });
    }

    @Override protected void onCleared() { executor.shutdown(); }
}
