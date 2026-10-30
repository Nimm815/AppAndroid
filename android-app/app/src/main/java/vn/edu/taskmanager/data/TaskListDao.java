package vn.edu.taskmanager.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface TaskListDao {
    @Transaction @Query("SELECT * FROM task_lists ORDER BY createdAt DESC, id")
    LiveData<List<TaskListWithTasks>> observeLists();

    @Transaction @Query("SELECT * FROM task_lists ORDER BY createdAt DESC, id")
    List<TaskListWithTasks> allLists();

    @Query("SELECT * FROM task_lists WHERE id = :id") TaskList find(String id);
    @Insert void insert(TaskList list);
    @Insert(onConflict = OnConflictStrategy.IGNORE) void insertFromSync(TaskList list);
    @Query("UPDATE task_lists SET title = :title WHERE id = :id") void rename(String id, String title);
    @Query("DELETE FROM task_lists WHERE id = :id") void delete(String id);
    @Query("DELETE FROM task_list_members WHERE listId = :id") void clearMembers(String id);
    @Query("DELETE FROM task_list_members WHERE taskId = :taskId") void removeTaskMembership(long taskId);
    @Query("SELECT COUNT(*) FROM task_list_members WHERE taskId = :taskId") int membershipCount(long taskId);
    @Insert void insertMember(TaskListMember member);

    /** Chuyển việc trong một transaction; lỗi ở đích sẽ giữ nguyên danh sách cũ. */
    @Transaction
    default void addMember(TaskListMember member) {
        removeTaskMembership(member.taskId);
        insertMember(member);
    }
    @Query("SELECT COUNT(*) FROM task_list_import_state WHERE id = 'legacy_preferences_v1'") int imported();
    @Insert void markImported(TaskListImportState state);
}
