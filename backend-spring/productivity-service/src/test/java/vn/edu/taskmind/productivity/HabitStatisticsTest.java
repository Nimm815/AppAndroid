package vn.edu.taskmind.productivity;
import org.junit.jupiter.api.Test;
import vn.edu.taskmind.productivity.api.HabitStatistics;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HabitStatisticsTest {
 @Test void unscheduledDaysDoNotBreakOrIncreaseStreak(){
  var monday=LocalDate.of(2026,10,19);var friday=monday.plusDays(4);
  var done=Set.of(monday,monday.plusDays(2),friday);
  var s=HabitStatistics.calculate(monday,friday.plusDays(2),d->Set.of(DayOfWeek.MONDAY,DayOfWeek.WEDNESDAY,DayOfWeek.FRIDAY).contains(d.getDayOfWeek()),done);
  assertEquals(3,s.current());assertEquals(3,s.longest());assertEquals(100,s.rate());
 }
 @Test void missedScheduledDayBreaksButTodayIsStillOpen(){
  var first=LocalDate.of(2026,10,19);
  var s=HabitStatistics.calculate(first,first.plusDays(4),d->true,Set.of(first,first.plusDays(1),first.plusDays(3)));
  assertEquals(1,s.current());assertEquals(2,s.longest());assertEquals(60,s.rate());
 }

 @Test void developmentIdentityCannotStartInProduction(){
  var environment=new org.springframework.mock.env.MockEnvironment();environment.setActiveProfiles("dev","production");
  assertThrows(IllegalStateException.class,()->new vn.edu.taskmind.productivity.api.CurrentUser(environment,null));
 }
}
