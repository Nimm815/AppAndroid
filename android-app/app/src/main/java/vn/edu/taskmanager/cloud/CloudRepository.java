package vn.edu.taskmanager.cloud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.*;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/** Asynchronous requests; cancel callbacks when the owning screen is destroyed. */
public final class CloudRepository {
    public final TaskApi tasks;
    public final HabitApi habits;
    private final Set<Call<?>> calls = new HashSet<>();
    private boolean closed;
    private final android.content.Context context;
    public interface Result<T> { void success(T value); void error(String message); }
    public CloudRepository(String taskUrl,String habitUrl,android.content.Context context) {
        this.context=context.getApplicationContext();
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS)
                .readTimeout(20,TimeUnit.SECONDS).writeTimeout(20,TimeUnit.SECONDS).retryOnConnectionFailure(false)
                .addInterceptor(chain -> {
                    String token = vn.edu.taskmanager.auth.Session.token(this.context);
                    okhttp3.Request.Builder request = chain.request().newBuilder();
                    if (token != null && !token.isEmpty()) request.header("Authorization", "Bearer " + token);
                    return chain.proceed(request.build());
                }).build();
        tasks=retrofit(taskUrl,client).create(TaskApi.class);
        habits=retrofit(habitUrl,client).create(HabitApi.class);
    }
    private Retrofit retrofit(String url,OkHttpClient client) {
        return new Retrofit.Builder().baseUrl(url).client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().serializeNulls().create())).build();
    }
    public <T> void request(Call<T> call,Result<T> result) {
        if(closed)return;
        if (!vn.edu.taskmanager.auth.AccountSession.isLoggedIn(context)) {
            result.error(vn.edu.taskmanager.auth.AccountSession.authenticationRequired(context)
                    ? "Đăng nhập lại để đồng bộ dữ liệu với tài khoản của bạn."
                    : "Đăng nhập để dùng dữ liệu được lưu trên máy chủ.");
            return;
        }
        calls.add(call);
        call.enqueue(new Callback<T>() {
            public void onResponse(Call<T> c,Response<T> response) {
                calls.remove(c);if(closed)return;
                if(response.isSuccessful()){
                    if(response.code()!=204&&response.body()==null){result.error("Máy chủ trả về dữ liệu rỗng không hợp lệ.");return;}
                    result.success(response.body());return;
                }
                String message="Lỗi HTTP "+response.code()+". Hãy thử tải lại.";
                if(response.code()==404) {
                    String service = serviceName(c);
                    message=service + " chưa mở API ở đường dẫn này. Hãy khởi động backend với profile dev.";
                }
                try { if(response.errorBody()!=null) {
                    String body=response.errorBody().string();
                    var error=JsonParser.parseString(body).getAsJsonObject();
                    if(error.has("message"))message=error.get("message").getAsString();
                }}catch(Exception ignored) { }
                result.error(message);
            }
            public void onFailure(Call<T> c,Throwable error) {
                calls.remove(c);if(closed||c.isCanceled())return;
                okhttp3.HttpUrl url = c.request().url();
                int port = url.port();
                String detail = causedBy(error, ConnectException.class) || causedBy(error, SocketException.class)
                        ? "Không có dịch vụ lắng nghe hoặc chưa chuyển tiếp cổng."
                        : causedBy(error, UnknownHostException.class) ? "Không tìm thấy địa chỉ máy chủ."
                        : causedBy(error, SocketTimeoutException.class) ? "Máy chủ phản hồi quá thời gian chờ."
                        : error.getMessage();
                result.error("Không kết nối được " + serviceName(c) + " tại "
                        + url.host() + ":" + port + ". " + detail
                        + " Nếu điện thoại đang cắm USB, chạy `adb reverse tcp:" + port + " tcp:"
                        + port + "` trên máy tính rồi thử lại.");
            }
        });
    }
    private String serviceName(Call<?> call) {
        return call.request().url().encodedPath().startsWith("/api/habits")
                ? "Productivity Service" : "Task Service";
    }
    private boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (type.isInstance(cause)) return true;
        return false;
    }
    public void close(){closed=true;for(Call<?> c:calls)c.cancel();calls.clear();}
}
