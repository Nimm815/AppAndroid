package vn.edu.taskmind.productivity.repository;

import vn.edu.taskmind.productivity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {
    List<HabitLog> findByHabitIdOrderByLogDateAsc(Integer habitId);
    Optional<HabitLog> findByHabitIdAndLogDate(Integer habitId, java.time.LocalDate logDate);
}
