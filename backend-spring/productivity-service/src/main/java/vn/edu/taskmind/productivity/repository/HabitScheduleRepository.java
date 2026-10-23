package vn.edu.taskmind.productivity.repository;
import vn.edu.taskmind.productivity.domain.HabitSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HabitScheduleRepository extends JpaRepository<HabitSchedule,Long> {
 List<HabitSchedule> findByHabitIdOrderByEffectiveDateAsc(Integer habitId);
}
