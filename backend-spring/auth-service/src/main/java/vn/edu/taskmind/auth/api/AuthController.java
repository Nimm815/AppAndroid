package vn.edu.taskmind.auth.api;

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
  if(c.email()==null||c.email().length()>255||!c.email().trim().matches("[^ @]+@[^ @]+\\.[^ @]+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Email không hợp lệ");
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
  try{String[] parts=hash.split("\\$");return parts.length==4&&parts[0].equals("pbkdf2-sha256")&&parts[1].equals("600000")&&MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),Base64.getDecoder().decode(derive(password,Base64.getDecoder().decode(parts[2]))));}
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
  String email=email(c);var users=db.queryForList("SELECT UserID,PasswordHash,IsLocked+0 AS IsLocked,IsDeleted+0 AS IsDeleted FROM users WHERE Email=?",email);
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
