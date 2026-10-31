package vn.edu.taskmind.productivity.repository;

import vn.edu.taskmind.productivity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HabitRepository extends JpaRepository<Habit, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select h from Habit h where h.habitId = :id and h.userId = :owner and h.isActive = true")
    Optional<Habit> findOwned(@org.springframework.data.repository.query.Param("id") Integer id, @org.springframework.data.repository.query.Param("owner") Integer owner);
    List<Habit> findByUserIdAndIsActiveTrue(Integer userId);
}
