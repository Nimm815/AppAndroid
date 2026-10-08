package vn.edu.taskmanager.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY completed ASC, id DESC")
    LiveData<List<Task>> observeTasks();

    @Query("SELECT * FROM tasks")
    List<Task> allTasks();

    @Query("UPDATE tasks SET title = :title, scheduledDate = :date, priority = :priority, progress = :progress, note = :note, completed = CASE WHEN :progress = 100 THEN 1 ELSE 0 END WHERE id = :id")
    void editDetails(long id, String title, String date, int priority, int progress, String note);

    @Insert
    long insert(Task task);

    @Query("UPDATE tasks SET title = :title WHERE id = :id")
    void rename(long id, String title);
    @Query("UPDATE tasks SET title = :title, scheduledDate = :date WHERE id = :id")
    void edit(long id, String title, String date);
    @Query("UPDATE tasks SET title = :title, scheduledDate = :date, priority = :priority WHERE id = :id")
    void editWithPriority(long id, String title, String date, int priority);

    @Query("UPDATE tasks SET completed = :completed, progress = CASE WHEN :completed THEN 100 ELSE 0 END WHERE id = :id")
    void setCompleted(long id, boolean completed);

    @Query("DELETE FROM tasks WHERE id = :id")
    void delete(long id);

    @Query("SELECT * FROM tasks WHERE id = :id")
    Task findById(long id);
}
