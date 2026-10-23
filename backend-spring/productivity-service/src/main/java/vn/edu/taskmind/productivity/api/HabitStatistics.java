package vn.edu.taskmind.productivity.api;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
/** Pure calendar calculation: unscheduled days neither break nor extend a streak. */
public final class HabitStatistics {
 public record Result(int current,int longest,int completed,int scheduled,double rate) {}
 public static Result calculate(LocalDate start,LocalDate today,Predicate<LocalDate> scheduled,Set<LocalDate> done) {
  int current=0,longest=0,completed=0,total=0;
  for(LocalDate d=start;!d.isAfter(today);d=d.plusDays(1)) {
   if(!scheduled.test(d)) continue;
   total++;
   if(done.contains(d)){completed++;current++;longest=Math.max(longest,current);}
   else if(d.isBefore(today)) current=0;
  }
  return new Result(current,longest,completed,total,total==0?0:Math.round(completed*1000.0/total)/10.0);
 }
}
