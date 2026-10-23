from pathlib import Path
root=Path('../backend-spring')
def put(p,s):
 p=root/p;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8')
put(Path('auth-service/src/main/resources/db/migration/V2__auth_sessions.sql'),'''CREATE TABLE auth_sessions (
 TokenHash CHAR(64) NOT NULL PRIMARY KEY,
 UserID INT NOT NULL,
 ExpiresAt DATETIME NOT NULL,
 CreatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_session_user(UserID),
 CONSTRAINT fk_session_user FOREIGN KEY(UserID) REFERENCES users(UserID)
) ENGINE=InnoDB;
''')
put(Path('auth-service/src/main/java/vn/edu/taskmind/auth/api/AuthController.java'),'''package vn.edu.taskmind.auth.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.*;

/** Random bearer sessions; only a SHA-256 digest is stored in MySQL. */
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final JdbcTemplate db;
 private static final SecureRandom RANDOM=new SecureRandom();
 public AuthController(JdbcTemplate db){this.db=db;}
 public record Credentials(String email,String password,String fullName){}
 private static ResponseStatusException unauthorized(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Thông tin đăng nhập không hợp lệ");}
 private static String email(Credentials c){
  if(c.email()==null||c.email().length()>255||!c.email().trim().matches("[^ @]+@[^ @]+\\\\.[^ @]+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Email không hợp lệ");
  if(c.password()==null||c.password().length()<8||c.password().length()>128)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu cần 8–128 ký tự");
  return c.email().trim().toLowerCase(Locale.ROOT);
 }
 private static String derive(String password,byte[] salt){
  PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,600000,256);
  try{return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}
  catch(GeneralSecurityException e){throw new IllegalStateException(e);}finally{spec.clearPassword();}
 }
 private static String hashPassword(String p){byte[] salt=new byte[16];RANDOM.nextBytes(salt);return "pbkdf2-sha256$600000$"+Base64.getEncoder().encodeToString(salt)+"$"+derive(p,salt);}
 private static boolean matches(String password,String hash){
  try{String[] parts=hash.split("\\\\$");return parts.length==4&&parts[0].equals("pbkdf2-sha256")&&parts[1].equals("600000")&&MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),Base64.getDecoder().decode(derive(password,Base64.getDecoder().decode(parts[2]))));}
  catch(IllegalArgumentException e){return false;}
 }
 private static String digest(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 private Map<String,Object> issue(int id,String email){
  byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  db.update("INSERT INTO auth_sessions(TokenHash,UserID,ExpiresAt) VALUES (?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 30 DAY))",digest(token),id);
  return Map.of("token",token,"userId",id,"email",email,"expiresIn",2592000);
 }
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> register(@RequestBody Credentials c){
  String email=email(c);
  if(c.fullName()==null||c.fullName().isBlank()||c.fullName().length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên không hợp lệ");
  try{db.update("INSERT INTO users(FullName,Email,PasswordHash,Role) VALUES (?,?,?,'USER')",c.fullName().trim(),email,hashPassword(c.password()));}
  catch(DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Email đã được đăng ký");}
  return issue(db.queryForObject("SELECT LAST_INSERT_ID()",Integer.class),email);
 }
 @PostMapping("/login") @Transactional public Map<String,Object> login(@RequestBody Credentials c){
  String email=email(c);var users=db.queryForList("SELECT UserID,PasswordHash,IsLocked,IsDeleted FROM users WHERE Email=?",email);
  if(users.isEmpty()) {derive(c.password(),new byte[16]);throw unauthorized();}
  var u=users.get(0);
  if(!matches(c.password(),(String)u.get("PasswordHash"))||((Number)u.get("IsLocked")).intValue()!=0||((Number)u.get("IsDeleted")).intValue()!=0)throw unauthorized();
  return issue(((Number)u.get("UserID")).intValue(),email);
 }
 @GetMapping("/me") public Map<String,Object> me(@RequestHeader(value="Authorization",required=false) String authorization){
  if(authorization==null||!authorization.matches("Bearer [A-Za-z0-9_-]{43}"))throw unauthorized();
  var users=db.queryForList("SELECT u.UserID AS userId,u.Email AS email FROM auth_sessions s JOIN users u ON u.UserID=s.UserID WHERE s.TokenHash=? AND s.ExpiresAt>UTC_TIMESTAMP() AND u.IsLocked=0 AND u.IsDeleted=0",digest(authorization.substring(7)));
  if(users.isEmpty())throw unauthorized();return users.get(0);
 }
 @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@RequestHeader("Authorization") String authorization){me(authorization);db.update("DELETE FROM auth_sessions WHERE TokenHash=?",digest(authorization.substring(7)));}
}
''')
for service in ['task','productivity']:
 base=Path(f'{service}-service/src/main/java/vn/edu/taskmind/{service}/api')
 put(base/'NativeIdentity.java','''package vn.edu.taskmind.SERVICE.api;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.Map;
/** Resolves identity from Auth service, never from a client-supplied user ID. */
@Component @Profile("dev")
public class NativeIdentity {
 private final RestClient auth;
 public NativeIdentity(@Value("${taskmind.auth-url:http://127.0.0.1:8081}") String url){
  var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(5000);
  auth=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 public int id(){
  var request=((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
  Object cached=request.getAttribute("native.auth.user");if(cached instanceof Integer id)return id;
  String bearer=request.getHeader("Authorization");
  if(bearer==null||!bearer.matches("Bearer [A-Za-z0-9_-]{43}"))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  try{
   Map<?,?> user=auth.get().uri("/api/auth/me").header("Authorization",bearer).retrieve().body(Map.class);
   if(user==null||!(user.get("userId") instanceof Number id)||id.intValue()<1)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
   request.setAttribute("native.auth.user",id.intValue());return id.intValue();
  }catch(HttpClientErrorException e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
  catch(RestClientException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Auth service unavailable");}
 }
}
'''.replace('SERVICE',service))
 p=root/base/'DeviceWorkspaceStore.java';p.write_text(p.read_text(encoding='utf-8').replace('CurrentUser','NativeIdentity'),encoding='utf-8')
 p=root/f'{service}-service/src/main/resources/application.properties';p.write_text(p.read_text(encoding='utf-8')+'\ntaskmind.auth-url=${AUTH_SERVICE_URL:http://127.0.0.1:8081}\n',encoding='utf-8')
 p=root/f'{service}-service/src/test/java/vn/edu/taskmind/{service}/NativeWorkspaceIntegrationTest.java'
 s=p.read_text(encoding='utf-8').replace('class NativeWorkspaceIntegrationTest {','''class NativeWorkspaceIntegrationTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean vn.edu.taskmind.SERVICE.api.NativeIdentity identity;
 @org.junit.jupiter.api.BeforeEach void identity(){org.mockito.Mockito.when(identity.id()).thenReturn(1);}
'''.replace('SERVICE',service));p.write_text(s,encoding='utf-8')
p=root/'auth-service/src/test/java/vn/edu/taskmind/auth/DatabaseIntegrationTest.java';p.write_text(p.read_text(encoding='utf-8').replace('assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables','assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables'),encoding='utf-8')
