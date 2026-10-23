package vn.edu.taskmind.productivity;
import vn.edu.taskmind.productivity.api.NativeIdentity;
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
 @Test void localWorkspaceIdentityIsLimitedToLoopbackHabitRequests(){
  var request=new MockHttpServletRequest();request.setRequestURI("/api/habits");
  request.setRemoteAddr("127.0.0.1");
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  var identity=new NativeIdentity("http://127.0.0.1:1");
  org.springframework.test.util.ReflectionTestUtils.setField(identity,"localWorkspace",true);
  org.springframework.test.util.ReflectionTestUtils.setField(identity,"bindAddress","127.0.0.1");
  org.springframework.test.util.ReflectionTestUtils.setField(identity,"localUserId",1);
  assertEquals(1,identity.id());
  request.setRemoteAddr("192.168.1.10");
  assertEquals(401,assertThrows(ResponseStatusException.class,identity::id).getStatusCode().value());
  request.setRemoteAddr("127.0.0.1");request.setRequestURI("/api/tasks");
  assertEquals(401,assertThrows(ResponseStatusException.class,identity::id).getStatusCode().value());
 }
 @Test void resolvesActualAccountAndCachesOnlyWithinRequest()throws Exception{
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var count=new java.util.concurrent.atomic.AtomicInteger();
  server.createContext("/api/auth/me",exchange->{count.incrementAndGet();byte[] body="{\"userId\":42,\"email\":\"test@example.test\"}".getBytes();exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try{
   var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer "+"a".repeat(43));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   var identity=new NativeIdentity("http://127.0.0.1:"+server.getAddress().getPort());
   assertEquals(42,identity.id());assertEquals(42,identity.id());assertEquals(1,count.get());
  }finally{server.stop(0);}
 }
}
