package vn.edu.taskmind.productivity.api;

import vn.edu.taskmind.productivity.domain.*;
import vn.edu.taskmind.productivity.repository.*;
import static vn.edu.taskmind.productivity.api.HabitDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service @Profile("dev") @Transactional
public class HabitManagementService {
 private final HabitRepository habits;private final HabitLogRepository logs;private final HabitScheduleRepository schedules;private final CurrentUser user;
 public HabitManagementService(HabitRepository h,HabitLogRepository l,HabitScheduleRepository s,CurrentUser u){habits=h;logs=l;schedules=s;user=u;}
 private LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));}
 private Habit owned(int id){return habits.findOwned(id,user.id()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thói quen"));}
 private boolean configured(Habit h){return h.getWeekdays()!=null || "DAILY".equalsIgnoreCase(h.getFrequency());}
 private int mask(Habit h){return h.getWeekdays()==null?127:h.getWeekdays();}
 private LocalDate start(Habit h){
  if(!configured(h))return today();
  if(h.getStartDate()!=null)return h.getStartDate();
  var old=logs.findByHabitIdOrderByLogDateAsc(h.getHabitId());
  return old.isEmpty()?today():old.get(0).getLogDate();
 }
 private boolean scheduled(LocalDate date,LocalDate start,int fallback,List<HabitSchedule> versions){
  if(date.isBefore(start))return false;int days=fallback;
  for(var version:versions){if(version.effectiveDate.isAfter(date))break;days=version.weekdays;}
  return (days & (1 << (date.getDayOfWeek().getValue()-1)))!=0;
 }
 private View view(Habit h){
  var entries=logs.findByHabitIdOrderByLogDateAsc(h.getHabitId());var versions=schedules.findByHabitIdOrderByEffectiveDateAsc(h.getHabitId());
  Set<LocalDate> done=new HashSet<>();for(var l:entries)if("COMPLETED".equals(l.getStatus()))done.add(l.getLogDate());
  LocalDate first=start(h);var result=HabitStatistics.calculate(first,today(),d->configured(h)&&scheduled(d,first,mask(h),versions),done);
  int actualToday=logs.findByHabitIdAndLogDate(h.getHabitId(),today()).map(HabitLog::getActualCount).orElse(0);
  return new View(h.getHabitId(),h.getHabitName(),h.getDescription(),h.getIcon(),h.getColor(),mask(h),first,
   configured(h),done.contains(today()),actualToday,result.current(),result.longest(),result.completed(),result.scheduled(),result.rate(),
   h.getTargetCount(),h.getTargetUnit(),h.getReminderTime(),Boolean.TRUE.equals(h.getReminderEnabled()));
 }
 public List<View> all(){return habits.findByUserId(user.id()).stream().filter(h->Boolean.TRUE.equals(h.getIsActive())).map(this::view).toList();}
 public View get(int id){return view(owned(id));}
 private void version(Habit h,LocalDate date,int days){
  var v=schedules.findByHabitIdOrderByEffectiveDateAsc(h.getHabitId()).stream().filter(s->s.effectiveDate.equals(date)).findFirst().orElseGet(HabitSchedule::new);
  v.habitId=h.getHabitId();v.effectiveDate=date;v.weekdays=days;schedules.saveAndFlush(v);
 }
 public View save(Integer id,Input in){
  if(in.startDate().isBefore(LocalDate.of(2000,1,1))||in.startDate().isAfter(today()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngày bắt đầu phải từ năm 2000 đến hôm nay");
  Habit h=id==null?new Habit():owned(id);
  int targetCount=in.targetCount()==null?1:in.targetCount();
  if(id==null){h.setUserId(user.id());h.setStartDate(in.startDate());}
  else if(!configured(h)){h.setStartDate(today());version(h,today(),in.weekdays());}
  else {
   LocalDate first=start(h);
   if(!first.equals(in.startDate()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Không đổi ngày bắt đầu để giữ lịch sử");
   h.setStartDate(first);
   if(schedules.findByHabitIdOrderByEffectiveDateAsc(id).isEmpty())version(h,first,mask(h));
   if(mask(h)!=in.weekdays()){
    if(logs.findByHabitIdAndLogDate(id,today()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Hôm nay đã có lịch sử. Bỏ check-in trước khi đổi lịch lặp.");
    version(h,today(),in.weekdays());
   }
  }
  if(id!=null && logs.findByHabitIdOrderByLogDateAsc(id).stream()
      .anyMatch(log -> log.getActualCount()!=null && log.getActualCount()>targetCount))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Mục tiêu mới thấp hơn dữ liệu đã ghi nhận; hãy giữ mục tiêu hoặc tạo thói quen mới");
  h.setHabitName(in.name().trim());h.setDescription(in.description());h.setIcon(in.icon());h.setColor(in.color());h.setWeekdays(in.weekdays());h.setFrequency(in.weekdays()==127?"DAILY":"WEEKLY");
  h.setTargetCount(targetCount);
  h.setTargetUnit(in.targetUnit()==null||in.targetUnit().isBlank()?"lần":in.targetUnit().trim());
  h.setReminderTime(in.reminderTime());
  h.setReminderEnabled(Boolean.TRUE.equals(in.reminderEnabled()) && in.reminderTime()!=null);
  habits.saveAndFlush(h);if(id==null)version(h,in.startDate(),in.weekdays());return view(h);
 }
 public void delete(int id){owned(id).setIsActive(false);}
 public View check(int id,LocalDate date,Boolean complete,Integer requestedCount){
  Habit h=owned(id);if(!configured(h))throw new ResponseStatusException(HttpStatus.CONFLICT,"Hãy chỉnh sửa và thiết lập lịch lặp trước khi check-in");var versions=schedules.findByHabitIdOrderByEffectiveDateAsc(id);
  if(date.isAfter(today())||!scheduled(date,start(h),mask(h),versions))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngày không thuộc lịch thực hiện hoặc ở tương lai");
  int count=requestedCount==null?(Boolean.TRUE.equals(complete)?h.getTargetCount():0):requestedCount;
  if(count<0||count>h.getTargetCount())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Giá trị thực hiện phải từ 0 đến mục tiêu");
  if(count>0){var entry=logs.findByHabitIdAndLogDate(id,date).orElseGet(HabitLog::new);entry.setHabitId(id);entry.setLogDate(date);entry.setStatus(count>=h.getTargetCount()?"COMPLETED":"IN_PROGRESS");entry.setActualCount(count);logs.saveAndFlush(entry);}
  else {logs.findByHabitIdAndLogDate(id,date).ifPresent(logs::delete);logs.flush();}
  return view(h);
 }
 public List<Day> history(int id,LocalDate from,LocalDate to){
  Habit h=owned(id);if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Khoảng xem lịch tối đa 366 ngày");
  var versions=schedules.findByHabitIdOrderByEffectiveDateAsc(id);Map<LocalDate,HabitLog> entries=new HashMap<>();for(var l:logs.findByHabitIdOrderByLogDateAsc(id))entries.put(l.getLogDate(),l);
  LocalDate first=start(h);List<Day> days=new ArrayList<>();for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)){var log=entries.get(d);days.add(new Day(d,configured(h)&&scheduled(d,first,mask(h),versions),log!=null&&"COMPLETED".equals(log.getStatus()),log==null?0:log.getActualCount()));}return days;
 }
}
