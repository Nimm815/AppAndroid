from pathlib import Path
r=Path('../backend-spring')
p=r/'auth-service/src/main/java/vn/edu/taskmind/auth/api/AuthController.java'
s=p.read_text(encoding='utf-8').replace('UserID,PasswordHash,IsLocked,IsDeleted FROM users','UserID,PasswordHash,IsLocked+0 AS IsLocked,IsDeleted+0 AS IsDeleted FROM users');p.write_text(s,encoding='utf-8')
p=r/'auth-service/src/test/java/vn/edu/taskmind/auth/AuthIntegrationTest.java'
p.write_text('''package vn.edu.taskmind.auth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true")
class AuthIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:"+System.getenv("MYSQL_TEST_PORT")+"/taskmind_auth_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
  p.add("spring.datasource.username",()->System.getenv("MYSQL_TEST_USERNAME"));p.add("spring.datasource.password",()->System.getenv("MYSQL_TEST_PASSWORD"));
 }
 @Test void registrationLoginExpiryLockAndLogout()throws Exception{
  String email=java.util.UUID.randomUUID()+"@test.invalid";
  String body=json.writeValueAsString(java.util.Map.of("email",email,"password","test-secret-123","fullName","Test"));
  var registered=json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  int id=registered.get("userId").asInt();String token=registered.get("token").asText();
  assertNotEquals("test-secret-123",db.queryForObject("SELECT PasswordHash FROM users WHERE UserID=?",String.class,id));
  assertEquals(64,db.queryForObject("SELECT LENGTH(TokenHash) FROM auth_sessions WHERE UserID=?",Integer.class,id));
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(id));
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isConflict());
  mvc.perform(post("/api/auth/login").contentType("application/json").content(body.replace("test-secret-123","wrong-secret-123"))).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isOk());
  mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  String fresh=json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
  db.update("UPDATE auth_sessions SET ExpiresAt=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY) WHERE UserID=?",id);
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+fresh)).andExpect(status().isUnauthorized());
  db.update("UPDATE users SET IsLocked=1 WHERE UserID=?",id);
  mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
 }
}
''',encoding='utf-8')
for service in ['task','productivity']:
 p=r/f'{service}-service/src/test/java/vn/edu/taskmind/{service}/NativeIdentityTest.java'
 p.write_text('''package vn.edu.taskmind.SERVICE;
import vn.edu.taskmind.SERVICE.api.NativeIdentity;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class NativeIdentityTest {
 @AfterEach void reset(){RequestContextHolder.resetRequestAttributes();}
 @Test void missingOrForgedBearerCannotUseDevelopmentIdentity(){
  var request=new MockHttpServletRequest();RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  var identity=new NativeIdentity("http://127.0.0.1:1");
  assertEquals(401,assertThrows(ResponseStatusException.class,identity::id).getStatusCode().value());
  request.addHeader("Authorization","Bearer user-1");
  assertEquals(401,assertThrows(ResponseStatusException.class,identity::id).getStatusCode().value());
 }
 @Test void resolvesActualAccountAndCachesOnlyWithinRequest()throws Exception{
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var count=new java.util.concurrent.atomic.AtomicInteger();
  server.createContext("/api/auth/me",exchange->{count.incrementAndGet();byte[] body="{\\"userId\\":42,\\"email\\":\\"test@example.test\\"}".getBytes();exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try{
   var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer "+"a".repeat(43));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   var identity=new NativeIdentity("http://127.0.0.1:"+server.getAddress().getPort());
   assertEquals(42,identity.id());assertEquals(42,identity.id());assertEquals(1,count.get());
  }finally{server.stop(0);}
 }
}
'''.replace('SERVICE',service),encoding='utf-8')
