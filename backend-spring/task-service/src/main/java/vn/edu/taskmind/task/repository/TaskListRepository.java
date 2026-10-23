package vn.edu.taskmind.task.repository;

import vn.edu.taskmind.task.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskListRepository extends JpaRepository<TaskList, Integer> {
    List<TaskList> findByUserId(Integer userId);
    Optional<TaskList> findByListIdAndUserId(Integer listId, Integer userId);
}
