package vn.edu.taskmanager.cloud;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.*;
import vn.edu.taskmanager.cloud.CloudModels.*;
public interface TaskApi {
    @GET("api/lists") Call<List<TaskList>> lists();
    @POST("api/lists") Call<TaskList> createList(@Body Map<String,Object> body);
    @PUT("api/lists/{id}") Call<TaskList> updateList(@Path("id") int id,@Body Map<String,Object> body);
    @DELETE("api/lists/{id}") Call<Void> deleteList(@Path("id") int id);
    @GET("api/tasks") Call<List<Task>> tasks(@Query("listId") Integer listId,@Query("status") String status,@Query("sort") String sort);
    @GET("api/tasks/{id}") Call<Task> task(@Path("id") int id);
    @POST("api/tasks") Call<Task> createTask(@Body Map<String,Object> body);
    @PUT("api/tasks/{id}") Call<Task> updateTask(@Path("id") int id,@Body Map<String,Object> body);
    @DELETE("api/tasks/{id}") Call<Void> deleteTask(@Path("id") int id);
    @PATCH("api/tasks/{id}/completion") Call<Task> complete(@Path("id") int id,@Body Map<String,Object> body);
    @PUT("api/tasks/{id}/list") Call<Task> move(@Path("id") int id,@Body Map<String,Object> body);
}
