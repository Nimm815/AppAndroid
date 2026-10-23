package vn.edu.taskmind.task.repository;

import vn.edu.taskmind.task.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskListItemRepository extends JpaRepository<TaskListItem, TaskListItemId> {
    Optional<TaskListItem> findByIdTaskId(Integer taskId);
    List<TaskListItem> findByIdListIdOrderBySortOrderAsc(Integer listId);
}
