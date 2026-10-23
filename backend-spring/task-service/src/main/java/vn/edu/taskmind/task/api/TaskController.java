package vn.edu.taskmind.task.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import java.util.List;
import static vn.edu.taskmind.task.api.TaskDtos.*;
@RestController @Profile("dev") @RequestMapping("/api")
public class TaskController {
    private final TaskManagementService service;
    public TaskController(TaskManagementService s){service=s;}
    @GetMapping("/lists") public List<ListView> lists(){return service.lists();}
    @PostMapping("/lists") @ResponseStatus(HttpStatus.CREATED) public ListView createList(@Valid @RequestBody ListInput body){return service.saveList(null,body);}
    @PutMapping("/lists/{id}") public ListView updateList(@PathVariable int id,@Valid @RequestBody ListInput body){return service.saveList(id,body);}
    @DeleteMapping("/lists/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteList(@PathVariable int id){service.deleteList(id);}
    @GetMapping("/tasks") public List<TaskView> tasks(@RequestParam(required=false) Integer listId,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="due") String sort){return service.search(listId,status,sort);}
    @GetMapping("/tasks/{id}") public TaskView task(@PathVariable int id){return service.get(id);}
    @PostMapping("/tasks") @ResponseStatus(HttpStatus.CREATED) public TaskView create(@Valid @RequestBody TaskInput body){return service.save(null,body);}
    @PutMapping("/tasks/{id}") public TaskView update(@PathVariable int id,@Valid @RequestBody TaskInput body){return service.save(id,body);}
    @PatchMapping("/tasks/{id}/completion") public TaskView complete(@PathVariable int id,@Valid @RequestBody Completion body){return service.complete(id,body.completed());}
    @PutMapping("/tasks/{id}/list") public TaskView move(@PathVariable int id,@Valid @RequestBody Move body){return service.move(id,body.listId());}
    @DeleteMapping("/tasks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable int id){service.delete(id);}
}
