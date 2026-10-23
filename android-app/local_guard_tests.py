from pathlib import Path
p=Path('../backend-spring/task-service/src/test/java/vn/edu/taskmind/task/NativeIdentityTest.java')
s=p.read_text(encoding='utf-8').replace(' @AfterEach void reset()', ''' @Test void localModeRefusesPublicBindAndNonNativeRoutes(){
  var identity=new NativeIdentity("http://127.0.0.1:1");
  org.springframework.test.util.ReflectionTestUtils.setField(identity,"localWorkspace",true);
  org.springframework.test.util.ReflectionTestUtils.setField(identity,"localUserId",1);
  org.springframework.test.util.ReflectionTestUtils.setField(identity,"bindAddress","0.0.0.0");
  assertThrows(IllegalStateException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(identity,"validateLocalWorkspace"));
  org.springframework.test.util.ReflectionTestUtils.setField(identity,"bindAddress","127.0.0.1");
  org.springframework.test.util.ReflectionTestUtils.invokeMethod(identity,"validateLocalWorkspace");
  var request=new MockHttpServletRequest();request.setRequestURI("/api/native-workspace/test");request.setRemoteAddr("192.168.1.5");
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  assertEquals(401,assertThrows(ResponseStatusException.class,identity::id).getStatusCode().value());
  request.setRemoteAddr("127.0.0.1");assertEquals(1,identity.id());
  request.setRequestURI("/api/tasks");assertEquals(401,assertThrows(ResponseStatusException.class,identity::id).getStatusCode().value());
 }
 @AfterEach void reset()''');p.write_text(s,encoding='utf-8')
