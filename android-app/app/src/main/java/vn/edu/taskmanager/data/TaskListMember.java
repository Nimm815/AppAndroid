package vn.edu.taskmanager.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "task_list_members", primaryKeys = {"listId", "taskId"},
        indices = {@Index(value = "taskId", unique = true)}, foreignKeys = {
        @ForeignKey(entity = TaskList.class, parentColumns = "id", childColumns = "listId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = Task.class, parentColumns = "id", childColumns = "taskId", onDelete = ForeignKey.CASCADE)})
public class TaskListMember {
    @NonNull public String listId = "";
    public long taskId;
    public TaskListMember(@NonNull String listId, long taskId) {
        this.listId = listId;
        this.taskId = taskId;
    }
}
