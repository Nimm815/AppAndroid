package vn.edu.taskmind.task.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.taskmind.task.domain.TaskListItem;
import vn.edu.taskmind.task.domain.TaskListItemId;
import vn.edu.taskmind.task.repository.TaskListItemRepository;
import vn.edu.taskmind.task.repository.TaskListRepository;
import vn.edu.taskmind.task.repository.TaskRepository;

@Service
public class TaskListAssignmentService {
    private final TaskRepository tasks;
    private final TaskListRepository lists;
    private final TaskListItemRepository items;

    public TaskListAssignmentService(TaskRepository tasks, TaskListRepository lists, TaskListItemRepository items) {
        this.tasks = tasks; this.lists = lists; this.items = items;
    }

    /** UserID phải lấy từ danh tính đã xác thực khi xây API, không tin UserID trong request body. */
    @Transactional
    public TaskListItem move(Integer userId, Integer taskId, Integer listId, int sortOrder) {
        if (userId == null || userId <= 0 || sortOrder < 0)
            throw new IllegalArgumentException("Invalid owner or sort order");
        tasks.findOwnedForUpdate(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found for this owner"));
        lists.findByListIdAndUserId(listId, userId)
                .orElseThrow(() -> new IllegalArgumentException("List not found for this owner"));
        TaskListItem previous = items.findByIdTaskId(taskId).orElse(null);
        if (previous != null && previous.getId().getListId().equals(listId)) {
            previous.setSortOrder(sortOrder);
            return items.saveAndFlush(previous);
        }
        if (previous != null) { items.delete(previous); items.flush(); }
        TaskListItem item = new TaskListItem();
        item.setId(new TaskListItemId(listId, taskId)); item.setSortOrder(sortOrder);
        return items.saveAndFlush(item);
    }
}
