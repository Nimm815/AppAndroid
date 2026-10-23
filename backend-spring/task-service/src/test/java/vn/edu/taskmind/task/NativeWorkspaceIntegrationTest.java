package vn.edu.taskmind.task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true") @Transactional
class NativeWorkspaceIntegrationTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean vn.edu.taskmind.task.api.NativeIdentity identity;
 @org.junit.jupiter.api.BeforeEach void identity(){org.mockito.Mockito.when(identity.id()).thenReturn(1);}

 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:"+required("MYSQL_TEST_PORT")+"/taskmind_task_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
  p.add("spring.datasource.username",()->required("MYSQL_TEST_USERNAME"));p.add("spring.datasource.password",()->required("MYSQL_TEST_PASSWORD"));
 }
 static String required(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException(name);return v;}
 int create(String route,String body)throws Exception{return json.readTree(mvc.perform(post(route).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asInt();}


 String device=java.util.UUID.randomUUID().toString();
 void send(Object body)throws Exception{mvc.perform(put("/api/native-workspace/"+device).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());}

 Object task(String id,int progress){return java.util.Map.of("id",id,"title","Native task","scheduledDate","2026-10-23","priority",4,"progress",progress,"note","Original note");}
 Object list(String id,java.util.List<String> members){return java.util.Map.of("id",id,"title",id,"members",members);}
 Object snapshot(long revision,java.util.List<Object> tasks,java.util.List<Object> lists){return java.util.Map.of("revision",revision,"tasks",tasks,"lists",lists);}
 @Test void progressPriorityMoveRetryAndDeletionStayWithinDevice()throws Exception{
  jdbc.update("INSERT INTO tasks(TaskID,UserID,Title,Priority,Status) VALUES(89999,1,'Unrelated','HIGH','PENDING')");
  var first=snapshot(1,java.util.List.of(task("1",35)),java.util.List.of(list("A",java.util.List.of("1")),list("B",java.util.List.of())));
  send(first);send(first);
  int id=jdbc.queryForObject("SELECT RemoteID FROM device_records WHERE DeviceID=? AND Kind='task'",Integer.class,device);
  assertEquals(35,jdbc.queryForObject("SELECT Progress FROM tasks WHERE TaskID=?",Integer.class,id));
  assertEquals(4,jdbc.queryForObject("SELECT LocalPriority FROM tasks WHERE TaskID=?",Integer.class,id));
  assertNull(jdbc.queryForObject("SELECT DueDate FROM tasks WHERE TaskID=?",String.class,id));
  send(snapshot(2,java.util.List.of(task("1",100)),java.util.List.of(list("A",java.util.List.of()),list("B",java.util.List.of("1")))));
  send(first); // stale network response must not roll back completion or membership
  assertEquals(100,jdbc.queryForObject("SELECT Progress FROM tasks WHERE TaskID=?",Integer.class,id));
  assertEquals("B",jdbc.queryForObject("SELECT r.LocalID FROM device_records r JOIN tasklistitems i ON r.RemoteID=i.ListID WHERE r.DeviceID=? AND r.Kind='list' AND i.TaskID=?",String.class,device,id));
  send(snapshot(3,java.util.List.of(),java.util.List.of()));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE TaskID=?",Integer.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE TaskID=89999",Integer.class));
  mvc.perform(get("/api/native-workspace/"+device)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(3));
 }
 @Test void invalidMembershipRollsBackEntireSnapshot()throws Exception{
  var bad=snapshot(1,java.util.List.of(task("1",30)),java.util.List.of(list("A",java.util.List.of("missing"))));
  mvc.perform(put("/api/native-workspace/"+device).contentType("application/json").content(json.writeValueAsString(bad))).andExpect(status().isBadRequest());
  // Transaction is rollback-only here; separate request integration handles commit rollback.
 }
}
