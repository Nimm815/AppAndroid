package vn.edu.taskmind.auth;
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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true")
class AuthIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
 @TempDir static Path tempDirectory;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:"+System.getenv("MYSQL_TEST_PORT")+"/taskmind_auth_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
  p.add("spring.datasource.username",()->System.getenv("MYSQL_TEST_USERNAME"));p.add("spring.datasource.password",()->System.getenv("MYSQL_TEST_PASSWORD"));
  p.add("taskmind.avatar.directory",()->tempDirectory.resolve("avatars").toString());
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
 @Test void profileIsSessionScopedValidatedAndPersistedWithoutExposingCredentials()throws Exception{
  String email=java.util.UUID.randomUUID()+"@test.invalid";
  String body=json.writeValueAsString(java.util.Map.of("email",email,"password","test-secret-123","fullName","Test"));
  var registered=json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
          .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  int id=registered.get("userId").asInt();String token=registered.get("token").asText();
  String authorization="Bearer "+token;
  mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/users/me").header("Authorization",authorization))
          .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(id))
          .andExpect(jsonPath("$.fullName").value("Test"))
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
  String updated=json.writeValueAsString(java.util.Map.of("fullName","Nguyễn An","email",email,
          "phoneNumber","0912345678","birthDate","1998-04-12","gender","OTHER"));
  mvc.perform(put("/api/users/me").header("Authorization",authorization).contentType("application/json").content(updated))
          .andExpect(status().isOk()).andExpect(jsonPath("$.phoneNumber").value("0912345678"))
          .andExpect(jsonPath("$.birthDate").value("1998-04-12")).andExpect(jsonPath("$.gender").value("OTHER"))
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
  assertEquals("Nguyễn An",db.queryForObject("SELECT FullName FROM users WHERE UserID=?",String.class,id));
  mvc.perform(put("/api/users/me").header("Authorization",authorization).contentType("application/json")
          .content(updated.replace(email,"invalid-email"))).andExpect(status().isBadRequest());
  mvc.perform(put("/api/users/me").header("Authorization",authorization).contentType("application/json")
          .content(updated.replace("0912345678","123"))).andExpect(status().isBadRequest());
  mvc.perform(put("/api/users/me").header("Authorization",authorization).contentType("application/json")
          .content(updated.replace("1998-04-12",java.time.LocalDate.now().plusDays(1).toString())))
          .andExpect(status().isBadRequest());
  String otherEmail=java.util.UUID.randomUUID()+"@test.invalid";
  String otherBody=json.writeValueAsString(java.util.Map.of("email",otherEmail,"password","test-secret-123","fullName","Other"));
  mvc.perform(post("/api/auth/register").contentType("application/json").content(otherBody)).andExpect(status().isCreated());
  mvc.perform(put("/api/users/me").header("Authorization",authorization).contentType("application/json")
          .content(updated.replace(email,otherEmail))).andExpect(status().isConflict());
 }
 @Test void avatarUploadIsOwnedBySessionAndServedAsImage()throws Exception{
  String email=java.util.UUID.randomUUID()+"@test.invalid";
  String body=json.writeValueAsString(java.util.Map.of("email",email,"password","test-secret-123","fullName","Avatar User"));
  var registered=json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
          .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  String authorization="Bearer "+registered.get("token").asText();
  ByteArrayOutputStream image=new ByteArrayOutputStream();
  ImageIO.write(new BufferedImage(24,24,BufferedImage.TYPE_INT_RGB),"png",image);
  MockMultipartFile file=new MockMultipartFile("file","profile.png","image/png",image.toByteArray());
  var updated=json.readTree(mvc.perform(multipart("/api/users/me/avatar").file(file)
          .header("Authorization",authorization)).andExpect(status().isOk())
          .andExpect(jsonPath("$.avatarUrl").value("/api/users/me/avatar"))
          .andReturn().getResponse().getContentAsString());
  int userId=registered.get("userId").asInt();
  String reference=db.queryForObject("SELECT AvatarRef FROM users WHERE UserID=?",String.class,userId);
  assertTrue(java.nio.file.Files.isRegularFile(tempDirectory.resolve("avatars").resolve(reference)));
  mvc.perform(get("/api/users/me/avatar").header("Authorization",authorization))
          .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
  mvc.perform(get("/api/users/me/avatar")).andExpect(status().isUnauthorized());
 }
}
