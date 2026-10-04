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

    @Insert
    long insert(Task task);

    @Query("UPDATE tasks SET title = :title WHERE id = :id")
    void rename(long id, String title);

    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    void setCompleted(long id, boolean completed);

    @Query("DELETE FROM tasks WHERE id = :id")
    void delete(long id);

    @Query("SELECT * FROM tasks WHERE id = :id")
    Task findById(long id);
}
