package vn.edu.taskmanager.cloud;

import org.junit.*;
import okhttp3.mockwebserver.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class CloudRepositoryTest {
    private MockWebServer server;
    private CloudRepository repository;
    @Before public void setUp()throws Exception{server=new MockWebServer();server.start();repository=new CloudRepository(server.url("/").toString(),server.url("/").toString());}
    @After public void close()throws Exception{repository.close();server.shutdown();}
    @Test public void clearsOptionalFieldsAndDoesNotSendOwner()throws Exception{
        server.enqueue(new MockResponse().setBody("{\"id\":4,\"title\":\"Updated\",\"progress\":0}"));
        Map<String,Object> body=new HashMap<>();body.put("title","Updated");body.put("priority","HIGH");body.put("listId",null);body.put("dueDate",null);
        var response=repository.tasks.updateTask(4,body).execute();assertTrue(response.isSuccessful());assertEquals(4,response.body().id);
        var request=server.takeRequest(2,TimeUnit.SECONDS);assertEquals("PUT",request.getMethod());assertEquals("/api/tasks/4",request.getPath());
        String json=request.getBody().readUtf8();assertTrue(json.contains("\"listId\":null"));assertTrue(json.contains("\"dueDate\":null"));assertFalse(json.contains("userId"));
    }
    @Test public void mapsCheckInAndHistoryWithoutLocalData()throws Exception{
        server.enqueue(new MockResponse().setBody("{\"id\":3,\"completedToday\":true,\"currentStreak\":2}"));
        var response=repository.habits.check(3,"2026-10-23",Collections.singletonMap("completed",true)).execute();
        assertTrue(response.body().completedToday);assertEquals(2,response.body().currentStreak);
        assertEquals("/api/habits/3/check-ins/2026-10-23",server.takeRequest().getPath());
        server.enqueue(new MockResponse().setBody("[{\"date\":\"2026-10-23\",\"scheduled\":true,\"completed\":true,\"actualCount\":1}]"));
        var history=repository.habits.history(3,"2026-10-23","2026-10-23").execute();assertTrue(history.body().get(0).completed);
    }
    @Test public void surfacesHttpValidationMessage()throws Exception{
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"message\":\"Invalid dates\"}"));
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();
        repository.request(repository.tasks.lists(),new CloudRepository.Result<List<CloudModels.TaskList>>(){
            public void success(List<CloudModels.TaskList> value){error.set("unexpected success");done.countDown();}
            public void error(String value){error.set(value);done.countDown();}
        });assertTrue(done.await(5,TimeUnit.SECONDS));assertEquals("Invalid dates",error.get());
    }
    @Test public void malformedResponseIsAnErrorNotFakeSuccess()throws Exception{
        server.enqueue(new MockResponse().setBody("not-json"));CountDownLatch done=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();
        repository.request(repository.tasks.lists(),new CloudRepository.Result<List<CloudModels.TaskList>>(){
            public void success(List<CloudModels.TaskList> value){done.countDown();}
            public void error(String value){error.set(value);done.countDown();}
        });assertTrue(done.await(5,TimeUnit.SECONDS));assertNotNull(error.get());
    }
    @Test public void taskApiNotEnabledExplainsDevelopmentProfile()throws Exception{
        server.enqueue(new MockResponse().setResponseCode(404));
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();
        repository.request(repository.tasks.lists(),new CloudRepository.Result<List<CloudModels.TaskList>>(){
            public void success(List<CloudModels.TaskList> value){error.set("unexpected success");done.countDown();}
            public void error(String value){error.set(value);done.countDown();}
        });
        assertTrue(done.await(5,TimeUnit.SECONDS));
        assertTrue(error.get().contains("Task Service"));
        assertTrue(error.get().contains("profile dev"));
    }
    @Test public void taskConnectionErrorShowsCorrectReversePort()throws Exception{
        int port=server.getPort();
        server.shutdown();
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();
        repository.request(repository.tasks.lists(),new CloudRepository.Result<List<CloudModels.TaskList>>(){
            public void success(List<CloudModels.TaskList> value){error.set("unexpected success");done.countDown();}
            public void error(String value){error.set(value);done.countDown();}
        });
        assertTrue(done.await(5,TimeUnit.SECONDS));
        assertTrue(error.get().contains("Task Service"));
        assertTrue(error.get().contains(":" + port));
        assertTrue(error.get().contains("adb reverse tcp:" + port + " tcp:" + port));
    }
}
