package vn.edu.taskmanager.cloud;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/** CRUD API cho ghi chú cá nhân trong nhật ký. */
public interface JournalApi {
    @GET("api/journal-entries")
    Call<List<JournalEntry>> entries();

    @POST("api/journal-entries")
    Call<JournalEntry> create(@Body Map<String, Object> body);

    @PUT("api/journal-entries/{id}")
    Call<JournalEntry> update(@Path("id") long id, @Body Map<String, Object> body);

    @DELETE("api/journal-entries/{id}")
    Call<Void> delete(@Path("id") long id);

    final class JournalEntry {
        public long entryId;
        public int userId;
        public String title;
        public String content;
        public String entryDate;
        public boolean pinned;
        public String label;
        public String sourceId;
        public String sourceType;
        public Integer progress;
    }
}
