package vn.edu.taskmind.productivity.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
@RestController @Profile("dev") @RequestMapping("/api/native-workspace")
public class NativeWorkspaceController {
 public record HabitInput(@NotBlank @Size(max=100) String id,@NotBlank @Size(max=200) String title,@Size(max=10000) String body,
  @NotNull LocalDate created,@NotNull @Size(max=40000) Map<@NotBlank String,@NotNull @Min(0) @Max(100) Integer> progress){}
 public record Snapshot(@Min(0) long revision,@NotNull @Size(max=2000) List<@Valid HabitInput> habits){}
 final DeviceWorkspaceStore store;final ObjectMapper json;
 public NativeWorkspaceController(DeviceWorkspaceStore s,ObjectMapper j){store=s;json=j;}
 @GetMapping("/{device}") public Map<String,Object> get(@PathVariable String device){return store.read(device);}
 @PutMapping("/{device}") @Transactional public Map<String,Object> save(@PathVariable String device,@Valid @RequestBody Snapshot body)throws Exception{
  long previous=store.lock(device);if(body.revision()<=previous)return Map.of("revision",previous);
  DeviceWorkspaceStore.unique(body.habits().stream().map(HabitInput::id).toList());var db=store.db;int user=store.user.id();Set<String> retained=new HashSet<>();
  for(var h:body.habits()){
   Integer id=store.id(device,"habit",h.id());if(id==null)id=store.create(device,"habit",h.id(),"INSERT INTO habits(UserID,HabitName,Frequency,TargetCount) VALUES (?,?,'DAILY',100)",user,h.title());
   db.update("UPDATE habits SET HabitName=?,Description=?,StartDate=?,Weekdays=127,Frequency='DAILY',TargetCount=100,IsActive=1 WHERE HabitID=? AND UserID=?",h.title(),h.body(),h.created(),id,user);
   db.update("INSERT INTO habit_schedule_versions(HabitID,EffectiveDate,Weekdays) VALUES (?,?,127) ON DUPLICATE KEY UPDATE Weekdays=127",id,h.created());
   // Existing UI supports partial completion. Preserve percentages, including explicit 0% days.
   for(var e:h.progress().entrySet()){
    LocalDate date=LocalDate.parse(e.getKey());int progress=e.getValue();
    db.update("INSERT INTO habitlogs(HabitID,LogDate,Status,ActualCount) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE Status=VALUES(Status),ActualCount=VALUES(ActualCount)",id,date,progress==100?"COMPLETED":progress==0?"PENDING":"IN_PROGRESS",progress);
   }
   retained.add(h.id());
  }
  for(var entry:store.records(device,"habit").entrySet())if(!retained.contains(entry.getKey()))db.update("UPDATE habits SET IsActive=0 WHERE HabitID=? AND UserID=?",entry.getValue(),user);
  store.finish(device,body.revision(),json.writeValueAsString(body));return Map.of("revision",body.revision(),"habits",retained.size());
 }
}
