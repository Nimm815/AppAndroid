package vn.edu.taskmanager;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import vn.edu.taskmanager.data.Task;
import vn.edu.taskmanager.data.TaskDao;
import vn.edu.taskmanager.data.TaskDatabase;
import vn.edu.taskmanager.data.TaskList;
import vn.edu.taskmanager.data.TaskListMember;
import vn.edu.taskmanager.data.TaskListWithTasks;
import vn.edu.taskmanager.data.LegacyTaskListImporter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskViewModel extends AndroidViewModel {
    private final TaskDao dao;
    private final TaskDatabase database;
    public final LiveData<List<TaskListWithTasks>> taskLists;
    public final MutableLiveData<Boolean> listsReady = new MutableLiveData<>(false);
    public final MutableLiveData<ListResult> listResult = new MutableLiveData<>();
    private final java.util.Set<String> activeListRequests = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    public boolean isListRequestActive(String request) { return activeListRequests.contains(request); }
    public static class ListResult {
        public final String request, listId;
        public final boolean success;
        ListResult(String request, String listId, boolean success) {
            this.request = request; this.listId = listId; this.success = success;
        }
    }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> writeError = new MutableLiveData<>(false);
    public final LiveData<List<Task>> tasks;
    private final MutableLiveData<Long> historyChanged = new MutableLiveData<>();
    public LiveData<Long> getHistoryChanged() { return historyChanged; }

    public TaskViewModel(@NonNull Application application) {
        super(application);
        database = TaskDatabase.getInstance(application);
        dao = database.taskDao();
        tasks = dao.observeTasks();
        taskLists = database.taskListDao().observeLists();
        executor.execute(() -> {
            try {
                LegacyTaskListImporter.importOnce(application, database);
                listsReady.postValue(true);
            } catch (RuntimeException error) {
                android.util.Log.e("TaskViewModel", "Không nhập được danh sách cũ", error);
                writeError.postValue(true);
            }
        });
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
    public void saveInList(long id, String title, String date, int priority, int progress, String note, String listId) {
        if (progress < 0 || progress > 100) throw new IllegalArgumentException("Mức hoàn thành từ 0 đến 100");
        saveDetails(id, title, date, priority, progress, note, listId);
    }
    private void saveDetails(long id, String title, String date, int priority, Integer progress, String note) {
        saveDetails(id, title, date, priority, progress, note, "");
    }
    private void saveDetails(long id, String title, String date, int priority, Integer progress, String note, String listId) {
        String cleaned = title.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Tên công việc không được trống");
        if (priority < 1 || priority > 4) throw new IllegalArgumentException("Nhóm quan trọng không hợp lệ");
        write(() -> {
            if (id == 0) {
                Task task = new Task(cleaned); task.scheduledDate = date; task.priority = priority;
                task.progress = progress == null ? 0 : progress; task.completed = task.progress == 100;
                task.note = note == null ? "" : note;
                database.runInTransaction(() -> {
                    if (!listId.isEmpty() && database.taskListDao().find(listId) == null)
                        throw new IllegalStateException("Danh sách không còn tồn tại");
                    long newId = dao.insert(task);
                    if (!listId.isEmpty()) database.taskListDao().addMember(new TaskListMember(listId, newId));
                });
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

    public void saveList(String request, String id, String title, boolean creating, java.util.Set<Long> members) {
        String cleaned = title.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Tên danh sách không được trống");
        java.util.Set<Long> selected = new java.util.HashSet<>(members);
        mutateList(request, id, () -> {
            TaskList list = database.taskListDao().find(id);
            if (creating && list == null) {
                list = new TaskList(); list.id = id; list.title = cleaned; list.createdAt = System.currentTimeMillis();
                database.taskListDao().insert(list);
            } else {
                if (list == null) throw new IllegalStateException("Danh sách không còn tồn tại");
                database.taskListDao().rename(id, cleaned);
            }
            database.taskListDao().clearMembers(id);
            for (Long taskId : selected) if (dao.findById(taskId) != null)
                database.taskListDao().addMember(new TaskListMember(id, taskId));
        });
    }
    public void deleteList(String request, String id) {
        mutateList(request, id, () -> database.taskListDao().delete(id));
    }
    private void mutateList(String request, String id, Runnable operation) {
        activeListRequests.add(request);
        executor.execute(() -> {
            try {
                LegacyTaskListImporter.importOnce(getApplication(), database);
                database.runInTransaction(operation);
                listsReady.postValue(true);
                listResult.postValue(new ListResult(request, id, true));
            } catch (RuntimeException error) {
                android.util.Log.e("TaskViewModel", "Không lưu được danh sách", error);
                listResult.postValue(new ListResult(request, id, false));
            } finally {
                activeListRequests.remove(request);
            }
        });
    }

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
