package vn.edu.taskmind.task.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
@RestController @Profile("dev") @RequestMapping("/api/native-workspace")
public class NativeWorkspaceController {
 public record TaskInput(@NotBlank @Size(max=100) String id,@NotBlank @Size(max=200) String title,@NotNull String scheduledDate,
  @Min(1) @Max(4) int priority,@Min(0) @Max(100) int progress,@Size(max=10000) String note,@Min(0) long createdAt){}
 public record ListInput(@NotBlank @Size(max=100) String id,@NotBlank @Size(max=100) String title,@NotNull @Size(max=10000) List<String> members,@Min(0) long createdAt){}
 public record Snapshot(@Min(0) long revision,@NotNull @Size(max=10000) List<@Valid TaskInput> tasks,@NotNull @Size(max=2000) List<@Valid ListInput> lists){}
 final DeviceWorkspaceStore store;final ObjectMapper json;
 public NativeWorkspaceController(DeviceWorkspaceStore s,ObjectMapper j){store=s;json=j;}
 @GetMapping("/{device}") public Map<String,Object> get(@PathVariable String device){return store.read(device);}
 @PutMapping("/{device}") @Transactional public Map<String,Object> save(@PathVariable String device,@Valid @RequestBody Snapshot body)throws Exception{
  long previous=store.lock(device);if(body.revision()<=previous)return Map.of("revision",previous);
  DeviceWorkspaceStore.unique(body.tasks().stream().map(TaskInput::id).toList());DeviceWorkspaceStore.unique(body.lists().stream().map(ListInput::id).toList());
  var db=store.db;int user=store.user.id();Map<String,Integer> tasks=new HashMap<>(),lists=new HashMap<>();
  for(var t:body.tasks()){
   String date=t.scheduledDate().isEmpty()?null:LocalDate.parse(t.scheduledDate()).toString();
   LocalDateTime created=t.createdAt()>0?LocalDateTime.ofInstant(Instant.ofEpochMilli(t.createdAt()),ZoneOffset.UTC):LocalDateTime.now(ZoneOffset.UTC);
   Integer id=store.id(device,"task",t.id());if(id==null)id=store.create(device,"task",t.id(),"INSERT INTO tasks(UserID,Title,Priority,Status,CreatedAt) VALUES (?,?,'MEDIUM','PENDING',?)",user,t.title(),created);
   String priority=t.priority()==1?"HIGH":t.priority()==2?"MEDIUM":"LOW", status=t.progress()==100?"COMPLETED":t.progress()==0?"PENDING":"IN_PROGRESS";
   db.update("UPDATE tasks SET Title=?,ScheduledDate=?,LocalPriority=?,Priority=?,Progress=?,Status=?,Notes=?,CreatedAt=?,CompletedAt=CASE WHEN ?=100 THEN COALESCE(CompletedAt,UTC_TIMESTAMP()) ELSE NULL END WHERE TaskID=? AND UserID=?",t.title(),date,t.priority(),priority,t.progress(),status,t.note(),created,t.progress(),id,user);tasks.put(t.id(),id);
  }
  for(var l:body.lists()){
   LocalDateTime created=l.createdAt()>0?LocalDateTime.ofInstant(Instant.ofEpochMilli(l.createdAt()),ZoneOffset.UTC):LocalDateTime.now(ZoneOffset.UTC);
   Integer id=store.id(device,"list",l.id());if(id==null)id=store.create(device,"list",l.id(),"INSERT INTO tasklists(UserID,ListName,CreatedAt) VALUES (?,?,?)",user,l.title(),created);
   db.update("UPDATE tasklists SET ListName=? WHERE ListID=? AND UserID=?",l.title(),id,user);lists.put(l.id(),id);
  }
  // Only memberships of this device's mapped tasks are replaced; other devices are untouched.
  for(int task:tasks.values())db.update("DELETE FROM tasklistitems WHERE TaskID=?",task);
  Set<String> assigned=new HashSet<>();
  for(var l:body.lists()){int order=0;for(String local:l.members()){
   if(!tasks.containsKey(local)||!assigned.add(local))throw new IllegalArgumentException("Invalid membership");
   db.update("INSERT INTO tasklistitems(ListID,TaskID,SortOrder) VALUES (?,?,?)",lists.get(l.id()),tasks.get(local),order++);
  }}
  for(var entry:store.records(device,"list").entrySet())if(!lists.containsKey(entry.getKey())){
   db.update("DELETE FROM tasklists WHERE ListID=? AND UserID=?",entry.getValue(),user);store.unmap(device,"list",entry.getKey());
  }
  for(var entry:store.records(device,"task").entrySet())if(!tasks.containsKey(entry.getKey())){
   db.update("DELETE FROM tasks WHERE TaskID=? AND UserID=?",entry.getValue(),user);store.unmap(device,"task",entry.getKey());
  }
  store.finish(device,body.revision(),json.writeValueAsString(body));return Map.of("revision",body.revision(),"tasks",tasks.size(),"lists",lists.size());
 }
}
