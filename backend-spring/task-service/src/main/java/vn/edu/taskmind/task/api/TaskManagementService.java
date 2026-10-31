package vn.edu.taskmind.task.api;

import vn.edu.taskmind.task.domain.*;
import vn.edu.taskmind.task.repository.*;
import vn.edu.taskmind.task.service.TaskListAssignmentService;
import static vn.edu.taskmind.task.api.TaskDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
@Service @Profile("dev") @Transactional
public class TaskManagementService {
    private final TaskRepository tasks; private final TaskListRepository lists; private final TaskListItemRepository items;
    private final TaskListAssignmentService assignments; private final CurrentUser user;
    public TaskManagementService(TaskRepository t, TaskListRepository l, TaskListItemRepository i, TaskListAssignmentService a, CurrentUser u) { tasks=t;lists=l;items=i;assignments=a;user=u; }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy dữ liệu"); }
    private Task task(int id) { return tasks.findOwnedForUpdate(id,user.id()).orElseThrow(this::missing); }
    private TaskList list(int id) { return lists.findByListIdAndUserId(id,user.id()).orElseThrow(this::missing); }
    private ListView listView(TaskList l) {
        var members=items.findByIdListIdOrderBySortOrderAsc(l.getListId());
        long done=members.stream().filter(m -> tasks.findById(m.getId().getTaskId()).map(t -> t.getProgress()==100).orElse(false)).count();
        return new ListView(l.getListId(),l.getListName(),l.getColor(),members.size(),done);
    }
    public List<ListView> lists() { return lists.findByUserId(user.id()).stream().map(this::listView).toList(); }
    public ListView saveList(Integer id,ListInput input) {
        TaskList l=id==null?new TaskList():list(id); l.setUserId(user.id());l.setListName(input.name().trim());l.setColor(input.color());
        return listView(lists.saveAndFlush(l));
    }
    public void deleteList(int id) {
        TaskList ownedList = list(id);
        items.deleteAll(items.findByIdListIdOrderBySortOrderAsc(id));
        items.flush();
        lists.delete(ownedList);
        lists.flush();
    }
    private TaskView view(Task t) {
        Integer listId=items.findByIdTaskId(t.getTaskId()).map(m->m.getId().getListId()).orElse(null);
        return new TaskView(t.getTaskId(),t.getTitle(),t.getDescription(),null,t.getStartDate(),t.getDueDate(),t.getPriority(),t.getStatus(),t.getProgress(),listId);
    }
    public TaskView get(int id) { return view(task(id)); }
    public List<TaskView> search(Integer listId,String status,String sort) {
        if(listId!=null) list(listId);
        if(!Set.of("ALL","PENDING","TODO","IN_PROGRESS","COMPLETED","CANCELLED").contains(status) || !Set.of("due","priority").contains(sort)) throw new IllegalArgumentException();
        Comparator<TaskView> order=sort.equals("priority")?Comparator.comparingInt(t->switch(t.priority()){case "URGENT"->0;case "HIGH"->1;case "MEDIUM"->2;default->3;}):Comparator.comparing(TaskView::dueDate,Comparator.nullsLast(Comparator.naturalOrder()));
        return tasks.findByUserId(user.id()).stream().map(this::view).filter(t->listId==null||listId.equals(t.listId()))
            .filter(t -> {
                if (status.equals("ALL")) return true;
                if (status.equals("PENDING") || status.equals("TODO")) return t.progress()<100 && !"CANCELLED".equals(t.status());
                return status.equals(t.status());
            })
            .sorted(order.thenComparingInt(TaskView::id)).toList();
    }
    public TaskView save(Integer id,TaskInput in) {
        if(in.startDate()!=null&&in.dueDate()!=null&&in.dueDate().isBefore(in.startDate())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hạn hoàn thành phải sau thời gian bắt đầu");
        if(in.listId()!=null) list(in.listId());
        if (in.notes() != null && !in.notes().isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Bảng tasks hiện tại không có cột ghi chú riêng.");
        Task t=id==null?new Task():task(id);
        if(id==null){t.setUserId(user.id());t.setStatus("TODO");t.setProgress((byte)0);}
        t.setTitle(in.title().trim());t.setDescription(in.description());t.setStartDate(in.startDate());t.setDueDate(in.dueDate());t.setPriority(in.priority());
        tasks.saveAndFlush(t);
        if(in.listId()!=null) assignments.move(user.id(),t.getTaskId(),in.listId(),0);
        else items.findByIdTaskId(t.getTaskId()).ifPresent(items::delete);
        items.flush();return view(t);
    }
    public TaskView complete(int id,boolean completed) { Task t=task(id); t.setProgress((byte)(completed?100:0));t.setStatus(completed?"COMPLETED":"TODO");t.setCompletedAt(completed?LocalDateTime.now(ZoneOffset.UTC):null); return view(t); }
    public TaskView progress(int id,int progress) {
        Task t=task(id);t.setProgress((byte)progress);
        t.setStatus(progress==100?"COMPLETED":progress==0?"TODO":"IN_PROGRESS");
        t.setCompletedAt(progress==100?LocalDateTime.now(ZoneOffset.UTC):null);
        return view(t);
    }
    public TaskView move(int id,int listId) { task(id);list(listId);assignments.move(user.id(),id,listId,0);return view(task(id)); }
    public void delete(int id) { tasks.delete(task(id)); tasks.flush(); }
}
