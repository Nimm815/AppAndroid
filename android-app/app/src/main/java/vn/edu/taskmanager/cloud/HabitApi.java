package vn.edu.taskmanager.cloud;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.*;
import vn.edu.taskmanager.cloud.CloudModels.*;
public interface HabitApi {
    @GET("api/habits") Call<List<Habit>> habits();
    @GET("api/habits/{id}") Call<Habit> habit(@Path("id") int id);
    @POST("api/habits") Call<Habit> create(@Body Map<String,Object> body);
    @PUT("api/habits/{id}") Call<Habit> update(@Path("id") int id,@Body Map<String,Object> body);
    @DELETE("api/habits/{id}") Call<Void> delete(@Path("id") int id);
    @PUT("api/habits/{id}/check-ins/{date}") Call<Habit> check(@Path("id") int id,@Path("date") String date,@Body Map<String,Object> body);
    @GET("api/habits/{id}/history") Call<List<Day>> history(@Path("id") int id,@Query("from") String from,@Query("to") String to);
}
