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
class ApiIntegrationTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean vn.edu.taskmind.task.api.NativeIdentity identity;
 @org.junit.jupiter.api.BeforeEach void identity(){org.mockito.Mockito.when(identity.id()).thenReturn(1);}

 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:"+required("MYSQL_TEST_PORT")+"/taskmind_task_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
  p.add("spring.datasource.username",()->required("MYSQL_TEST_USERNAME"));p.add("spring.datasource.password",()->required("MYSQL_TEST_PASSWORD"));
 }
 static String required(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException(name);return v;}
 int create(String route,String body)throws Exception{return json.readTree(mvc.perform(post(route).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asInt();}

 @Test void crudMoveFilterAndOwnership()throws Exception{
  int a=create("/api/lists","{\"name\":\"A\",\"color\":\"#336699\"}");
  int b=create("/api/lists","{\"name\":\"B\",\"color\":\"#336699\"}");
  String input="{\"title\":\"Test task\",\"priority\":\"HIGH\",\"description\":\"Mô tả\",\"notes\":\"Ghi chú\",\"startDate\":\"2026-01-01T08:00:00\",\"dueDate\":\"2026-01-02T08:00:00\",\"listId\":"+a+"}";
  int id=create("/api/tasks",input);
  mvc.perform(put("/api/tasks/"+id+"/list").contentType("application/json").content("{\"listId\":"+b+"}")).andExpect(status().isOk()).andExpect(jsonPath("$.listId").value(b));
  assertEquals(0,jdbc.queryForObject("select count(*) from tasklistitems where ListID=?",Integer.class,a));
  mvc.perform(patch("/api/tasks/"+id+"/completion").contentType("application/json").content("{\"completed\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.progress").value(100));
  mvc.perform(get("/api/tasks").param("status","COMPLETED").param("listId",""+b)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
  mvc.perform(put("/api/tasks/"+id).contentType("application/json").content(input.replace("Test task","Updated"))).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated"));
  mvc.perform(delete("/api/lists/"+a)).andExpect(status().isNoContent());
  mvc.perform(get("/api/tasks/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.listId").doesNotExist());
  jdbc.update("insert into tasks(TaskID,UserID,Title,Priority,Status) values(88888,2,'Private','HIGH','PENDING')");
  mvc.perform(get("/api/tasks/88888").header("X-User-Id","2")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/tasks/88888")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/tasks/"+id)).andExpect(status().isNoContent());
  mvc.perform(get("/api/tasks/"+id)).andExpect(status().isNotFound());
 }
 @Test void validationRejectsInvalidDatesAndClientOwner()throws Exception{
  mvc.perform(post("/api/tasks").contentType("application/json").content("{\"title\":\"x\",\"priority\":\"HIGH\",\"userId\":2}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/tasks").contentType("application/json").content("{\"title\":\"x\",\"priority\":\"HIGH\",\"startDate\":\"2026-02-01T08:00:00\",\"dueDate\":\"2026-01-01T08:00:00\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/lists").contentType("application/json").content("{\"name\":\"  \"}")).andExpect(status().isBadRequest());
 }
}
