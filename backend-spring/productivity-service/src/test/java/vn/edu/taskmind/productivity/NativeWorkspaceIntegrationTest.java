package vn.edu.taskmind.productivity;
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
 @org.springframework.test.context.bean.override.mockito.MockitoBean vn.edu.taskmind.productivity.api.NativeIdentity identity;
 @org.junit.jupiter.api.BeforeEach void identity(){org.mockito.Mockito.when(identity.id()).thenReturn(1);}

 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:"+required("MYSQL_TEST_PORT")+"/taskmind_productivity_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
  p.add("spring.datasource.username",()->required("MYSQL_TEST_USERNAME"));p.add("spring.datasource.password",()->required("MYSQL_TEST_PASSWORD"));
 }
 static String required(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException(name);return v;}
 int create(String route,String body)throws Exception{return json.readTree(mvc.perform(post(route).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asInt();}


 String device=java.util.UUID.randomUUID().toString();
 void send(Object body)throws Exception{mvc.perform(put("/api/native-workspace/"+device).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());}

 Object habit(int progress){return java.util.Map.of("id","local-habit","title","Original habit","body","Notes","created","2026-10-01","progress",java.util.Map.of("2026-10-20",progress,"2026-10-21",100));}
 Object snapshot(long revision,java.util.List<Object> habits){return java.util.Map.of("revision",revision,"habits",habits);}
 @Test void partialHistoryRetryUndoAndSoftDelete()throws Exception{
  send(snapshot(1,java.util.List.of(habit(25))));send(snapshot(1,java.util.List.of(habit(25))));
  int id=jdbc.queryForObject("SELECT RemoteID FROM device_records WHERE DeviceID=? AND Kind='habit'",Integer.class,device);
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM habitlogs WHERE HabitID=?",Integer.class,id));
  assertEquals(25,jdbc.queryForObject("SELECT ActualCount FROM habitlogs WHERE HabitID=? AND LogDate='2026-10-20'",Integer.class,id));
  send(snapshot(2,java.util.List.of(habit(0))));send(snapshot(1,java.util.List.of(habit(25))));
  assertEquals(0,jdbc.queryForObject("SELECT ActualCount FROM habitlogs WHERE HabitID=? AND LogDate='2026-10-20'",Integer.class,id));
  assertEquals(100,jdbc.queryForObject("SELECT ActualCount FROM habitlogs WHERE HabitID=? AND LogDate='2026-10-21'",Integer.class,id));
  send(snapshot(3,java.util.List.of()));
  assertEquals(0,jdbc.queryForObject("SELECT IsActive FROM habits WHERE HabitID=?",Integer.class,id));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM habitlogs WHERE HabitID=?",Integer.class,id));
 }
}
