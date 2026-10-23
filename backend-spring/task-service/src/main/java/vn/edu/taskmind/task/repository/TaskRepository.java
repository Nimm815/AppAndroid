package vn.edu.taskmind.task.repository;

import vn.edu.taskmind.task.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Integer> {
    List<Task> findByUserId(Integer userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Task t where t.taskId = :taskId and t.userId = :userId")
    Optional<Task> findOwnedForUpdate(@org.springframework.data.repository.query.Param("taskId") Integer taskId, @org.springframework.data.repository.query.Param("userId") Integer userId);
}
