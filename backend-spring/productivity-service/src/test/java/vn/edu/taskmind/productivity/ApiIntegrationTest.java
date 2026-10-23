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
class ApiIntegrationTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean vn.edu.taskmind.productivity.api.NativeIdentity identity;
 @org.junit.jupiter.api.BeforeEach void identity(){org.mockito.Mockito.when(identity.id()).thenReturn(1);}

 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:"+required("MYSQL_TEST_PORT")+"/taskmind_productivity_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
  p.add("spring.datasource.username",()->required("MYSQL_TEST_USERNAME"));p.add("spring.datasource.password",()->required("MYSQL_TEST_PASSWORD"));
 }
 static String required(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException(name);return v;}
 int create(String route,String body)throws Exception{return json.readTree(mvc.perform(post(route).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asInt();}

 @Test void checkInUndoHistoryAndSoftDelete()throws Exception{
  var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
  int id=create("/api/habits","{\"name\":\"Read\",\"description\":\"Books\",\"icon\":\"📚\",\"color\":\"#336699\",\"weekdays\":127,\"startDate\":\""+today.minusDays(2)+"\"}");
  for(int n=2;n>=0;n--)mvc.perform(put("/api/habits/"+id+"/check-ins/"+today.minusDays(n)).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isOk());
  mvc.perform(get("/api/habits/"+id)).andExpect(jsonPath("$.currentStreak").value(3)).andExpect(jsonPath("$.longestStreak").value(3)).andExpect(jsonPath("$.completionRate").value(100.0));
  mvc.perform(put("/api/habits/"+id+"/check-ins/"+today).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isOk());
  assertEquals(3,jdbc.queryForObject("select count(*) from habitlogs where HabitID=?",Integer.class,id));
  mvc.perform(put("/api/habits/"+id+"/check-ins/"+today.minusDays(1)).contentType("application/json").content("{\"completed\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.currentStreak").value(1));
  mvc.perform(get("/api/habits/"+id+"/history").param("from",today.minusDays(2).toString()).param("to",today.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
  mvc.perform(put("/api/habits/"+id+"/check-ins/"+today.plusDays(1)).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isBadRequest());
  mvc.perform(delete("/api/habits/"+id)).andExpect(status().isNoContent());
  mvc.perform(get("/api/habits/"+id)).andExpect(status().isNotFound());
  assertEquals(2,jdbc.queryForObject("select count(*) from habitlogs where HabitID=?",Integer.class,id));
 }

 @Test void quantitativeGoalUpdatesOneDailyRecordAndRejectsExcess()throws Exception{
  var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
  int id=create("/api/habits","{\"name\":\"Drink water\",\"icon\":\"💧\",\"color\":\"#336699\",\"weekdays\":127,\"startDate\":\""+today+"\",\"targetCount\":5,\"targetUnit\":\"cốc\"}");
  mvc.perform(put("/api/habits/"+id+"/check-ins/"+today).contentType("application/json").content("{\"completed\":false,\"actualCount\":2}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.completedToday").value(false)).andExpect(jsonPath("$.actualCountToday").value(2));
  mvc.perform(put("/api/habits/"+id+"/check-ins/"+today).contentType("application/json").content("{\"completed\":false,\"actualCount\":4}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.actualCountToday").value(4));
  assertEquals(1,jdbc.queryForObject("select count(*) from habitlogs where HabitID=? and LogDate=?",Integer.class,id,java.sql.Date.valueOf(today)));
  mvc.perform(put("/api/habits/"+id+"/check-ins/"+today).contentType("application/json").content("{\"completed\":true,\"actualCount\":6}"))
    .andExpect(status().isBadRequest());
  mvc.perform(get("/api/habits/"+id)).andExpect(jsonPath("$.targetCount").value(5)).andExpect(jsonPath("$.targetUnit").value("cốc"));
 }
 @Test void rejectsOtherOwnerAndInvalidSchedule()throws Exception{
  jdbc.update("insert into habits(HabitID,UserID,HabitName,Frequency) values(88888,2,'Private','DAILY')");
  mvc.perform(get("/api/habits/88888").header("X-User-Id","2")).andExpect(status().isNotFound());
  mvc.perform(post("/api/habits").contentType("application/json").content("{\"name\":\"Read\",\"icon\":\"book\",\"color\":\"#123456\",\"weekdays\":0,\"startDate\":\"2026-01-01\"}")).andExpect(status().isBadRequest());
 }

 @Test void scheduleEditsDoNotRewritePastAndLegacyScheduleIsExplicit()throws Exception{
  var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));var first=today.minusDays(7);
  int oldMask=1<<(first.getDayOfWeek().getValue()-1),newMask=1<<(first.getDayOfWeek().getValue()%7);
  String input="{\"name\":\"Weekly\",\"icon\":\"book\",\"color\":\"#123456\",\"weekdays\":"+oldMask+",\"startDate\":\""+first+"\"}";
  int id=create("/api/habits",input);
  mvc.perform(put("/api/habits/"+id+"/check-ins/"+first).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isOk());
  mvc.perform(put("/api/habits/"+id).contentType("application/json").content(input.replace("\"weekdays\":"+oldMask,"\"weekdays\":"+newMask))).andExpect(status().isOk());
  mvc.perform(get("/api/habits/"+id+"/history").param("from",first.toString()).param("to",first.toString())).andExpect(jsonPath("$[0].scheduled").value(true)).andExpect(jsonPath("$[0].completed").value(true));
  mvc.perform(put("/api/habits/"+id+"/check-ins/"+today).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isBadRequest());
  jdbc.update("insert into habits(HabitID,UserID,HabitName,Frequency) values(88777,1,'Legacy','WEEKLY')");
  mvc.perform(get("/api/habits/88777")).andExpect(jsonPath("$.scheduleConfigured").value(false)).andExpect(jsonPath("$.currentStreak").value(0));
  mvc.perform(put("/api/habits/88777/check-ins/"+today).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isConflict());
  mvc.perform(put("/api/habits/88777").contentType("application/json").content(input.replace(first.toString(),today.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.scheduleConfigured").value(true));
 }
}
