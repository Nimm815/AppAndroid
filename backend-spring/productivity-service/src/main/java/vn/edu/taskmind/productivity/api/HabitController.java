package vn.edu.taskmind.productivity.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import static vn.edu.taskmind.productivity.api.HabitDtos.*;
@RestController @Profile("dev") @RequestMapping("/api/habits")
public class HabitController {
 private final HabitManagementService service;public HabitController(HabitManagementService s){service=s;}
 @GetMapping public List<View> all(){return service.all();}
 @GetMapping("/{id}") public View get(@PathVariable int id){return service.get(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public View create(@Valid @RequestBody Input body){return service.save(null,body);}
 @PutMapping("/{id}") public View update(@PathVariable int id,@Valid @RequestBody Input body){return service.save(id,body);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable int id){service.delete(id);}
 @PutMapping("/{id}/check-ins/{date}") public View check(@PathVariable int id,@PathVariable LocalDate date,@Valid @RequestBody CheckIn body){return service.check(id,date,body.completed(),body.actualCount(),body.note());}
 @GetMapping("/{id}/history") public List<Day> history(@PathVariable int id,@RequestParam LocalDate from,@RequestParam LocalDate to){return service.history(id,from,to);}
}
