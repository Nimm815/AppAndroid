package vn.edu.taskmanager.cloud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Asynchronous requests; cancel callbacks when the owning screen is destroyed. */
public final class CloudRepository {
    public final TaskApi tasks;
    public final HabitApi habits;
    private final Set<Call<?>> calls = new HashSet<>();
    private boolean closed;
    public interface Result<T> { void success(T value); void error(String message); }
    public CloudRepository(String taskUrl,String habitUrl) {
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS)
                .readTimeout(20,TimeUnit.SECONDS).writeTimeout(20,TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
        tasks=retrofit(taskUrl,client).create(TaskApi.class);
        habits=retrofit(habitUrl,client).create(HabitApi.class);
    }
    private Retrofit retrofit(String url,OkHttpClient client) {
        return new Retrofit.Builder().baseUrl(url).client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().serializeNulls().create())).build();
    }
    public <T> void request(Call<T> call,Result<T> result) {
        if(closed)return;
        calls.add(call);
        call.enqueue(new Callback<T>() {
            public void onResponse(Call<T> c,Response<T> response) {
                calls.remove(c);if(closed)return;
                if(response.isSuccessful()){
                    if(response.code()!=204&&response.body()==null){result.error("Máy chủ trả về dữ liệu rỗng không hợp lệ.");return;}
                    result.success(response.body());return;
                }
                String message="Lỗi HTTP "+response.code()+". Hãy thử tải lại.";
                if(response.code()==404) message="Không tìm thấy dữ liệu hoặc API chưa bật profile dev.";
                try { if(response.errorBody()!=null) {
                    String body=response.errorBody().string();
                    var error=JsonParser.parseString(body).getAsJsonObject();
                    if(error.has("message"))message=error.get("message").getAsString();
                }}catch(Exception ignored) { }
                result.error(message);
            }
            public void onFailure(Call<T> c,Throwable error) {
                calls.remove(c);if(closed||c.isCanceled())return;
                result.error("Không kết nối được máy chủ hoặc dữ liệu trả về không hợp lệ. Kiểm tra mạng, địa chỉ và cổng, rồi tải lại.");
            }
        });
    }
    public void close(){closed=true;for(Call<?> c:calls)c.cancel();calls.clear();}
}
