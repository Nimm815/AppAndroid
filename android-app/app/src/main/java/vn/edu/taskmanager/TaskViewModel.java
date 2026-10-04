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

    public TaskViewModel(@NonNull Application application) {
        super(application);
        dao = TaskDatabase.getInstance(application).taskDao();
        tasks = dao.observeTasks();
    }

    public LiveData<Boolean> getWriteError() { return writeError; }
    public void clearError() { writeError.setValue(false); }

    public void save(long id, String title) {
        String cleaned = title.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Tên công việc không được trống");
        write(() -> {
            if (id == 0) dao.insert(new Task(cleaned));
            else dao.rename(id, cleaned);
        });
    }

    public void setCompleted(long id, boolean completed) {
        write(() -> dao.setCompleted(id, completed));
    }

    public void delete(long id) { write(() -> dao.delete(id)); }

    private void write(Runnable operation) {
        executor.execute(() -> {
            try { operation.run(); }
            catch (RuntimeException error) {
                android.util.Log.e("TaskViewModel", "Không thể lưu công việc", error);
                writeError.postValue(true);
            }
        });
    }

    @Override protected void onCleared() { executor.shutdown(); }
}
