package vn.edu.taskmanager.data;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;
import java.util.List;

public class TaskListWithTasks {
    @Embedded public TaskList list;
    @Relation(parentColumn = "id", entityColumn = "id",
            associateBy = @Junction(value = TaskListMember.class, parentColumn = "listId", entityColumn = "taskId"))
    public List<Task> tasks;

    public int completedCount() {
        int count = 0;
        for (Task task : tasks) if (task.completed) count++;
        return count;
    }
}
