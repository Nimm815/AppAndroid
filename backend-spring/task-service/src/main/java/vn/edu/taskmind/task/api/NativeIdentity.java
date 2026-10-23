package vn.edu.taskmind.task.api;
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
 @Value("${taskmind.local-workspace:false}") private boolean localWorkspace;
 @Value("${server.address:}") private String bindAddress;
 @Value("${taskmind.local-user-id:1}") private int localUserId;
 @jakarta.annotation.PostConstruct void validateLocalWorkspace(){
  if(localWorkspace && (!"127.0.0.1".equals(bindAddress)||localUserId<1))
   throw new IllegalStateException("Local workspace requires server.address=127.0.0.1 and a positive owner ID");
 }
 public NativeIdentity(@Value("${taskmind.auth-url:http://127.0.0.1:8081}") String url){
  var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(5000);
  auth=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 public int id(){
  var request=((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
  if(localWorkspace && request.getRequestURI().startsWith("/api/native-workspace/")
      && "127.0.0.1".equals(request.getRemoteAddr())) return localUserId;
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
