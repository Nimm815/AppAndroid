package vn.edu.taskmind.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TaskListItemId implements Serializable {
    @Column(name = "ListID", nullable = false)
    private Integer listId;
    @Column(name = "TaskID", nullable = false)
    private Integer taskId;

    public TaskListItemId() { }
    public TaskListItemId(Integer listId, Integer taskId) { this.listId = listId; this.taskId = taskId; }
    public Integer getListId() { return listId; }
    public void setListId(Integer listId) { this.listId = listId; }
    public Integer getTaskId() { return taskId; }
    public void setTaskId(Integer taskId) { this.taskId = taskId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TaskListItemId id)) return false;
        return Objects.equals(listId, id.listId) && Objects.equals(taskId, id.taskId);
    }
    @Override public int hashCode() { return Objects.hash(listId, taskId); }
}
