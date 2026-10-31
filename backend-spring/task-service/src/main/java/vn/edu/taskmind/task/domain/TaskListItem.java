package vn.edu.taskmind.task.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "tasklistitems")
public class TaskListItem {
    @EmbeddedId
    private TaskListItemId id;
    @Column(name = "SortOrder", nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ListID", insertable = false, updatable = false)
    private TaskList list;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TaskID", insertable = false, updatable = false)
    private Task task;

    public TaskListItem() { }

    public TaskListItemId getId() { return id; }
    public void setId(TaskListItemId id) { this.id = id; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public TaskList getList() { return list; }
    public Task getTask() { return task; }
}
