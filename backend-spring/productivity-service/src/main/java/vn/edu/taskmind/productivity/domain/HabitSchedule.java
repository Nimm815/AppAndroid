package vn.edu.taskmind.productivity.domain;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="habit_schedule_versions")
public class HabitSchedule {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="ScheduleID") public Long id;
 @Column(name="HabitID",nullable=false) public Integer habitId;
 @Column(name="EffectiveDate",nullable=false) public LocalDate effectiveDate;
 @Column(name="Weekdays",nullable=false) public Integer weekdays;
}
