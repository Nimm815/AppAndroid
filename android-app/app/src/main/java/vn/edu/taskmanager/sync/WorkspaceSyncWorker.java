package vn.edu.taskmanager.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import vn.edu.taskmanager.data.*;
import okhttp3.OkHttpClient;
import retrofit2.*;
import retrofit2.http.*;
import retrofit2.converter.gson.GsonConverterFactory;
import com.google.gson.GsonBuilder;
import java.util.*;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/** Sends changes without a separate screen; repeat requests cannot duplicate server entities. */
public final class WorkspaceSyncWorker extends Worker {
    interface Api { @PUT("api/native-workspace/{device}") Call<Map<String,Object>> save(@Path("device") String device,@Body Map<String,Object> body); }
    public WorkspaceSyncWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}
    @NonNull @Override public Result doWork(){
        Context context=getApplicationContext();var state=WorkspaceSync.state(context);
        long revision=state.getLong("revision",0);
        if(state.getLong("acknowledged",-1)>=revision)return Result.success();
        if(!WorkspaceSync.configured(context)){state.edit().putString("status","Chưa cấu hình địa chỉ API trong bản build").apply();return Result.success();}
        try {
            String device=WorkspaceSync.device(context);

            Map<String,Object> taskBody=new LinkedHashMap<>();
            List<Map<String,Object>> tasks=new ArrayList<>(),lists=new ArrayList<>();
            TaskDatabase db=TaskDatabase.getInstance(context);
            LegacyTaskListImporter.importOnce(context,db);
            db.runInTransaction(()->{
                for(Task t:db.taskDao().allTasks()){
                    Map<String,Object> item=new LinkedHashMap<>();item.put("id",String.valueOf(t.id));item.put("title",t.title);item.put("scheduledDate",t.scheduledDate);
                    item.put("priority",t.priority);item.put("progress",t.progress);item.put("note",t.note);tasks.add(item);
                }
                for(TaskListWithTasks l:db.taskListDao().allLists()){
                    Map<String,Object> item=new LinkedHashMap<>();item.put("id",l.list.id);item.put("title",l.list.title);
                    List<String> members=new ArrayList<>();for(Task t:l.tasks)members.add(String.valueOf(t.id));item.put("members",members);lists.add(item);
                }
            });
            taskBody.put("revision",revision);taskBody.put("tasks",tasks);taskBody.put("lists",lists);
            send(vn.edu.taskmanager.BuildConfig.TASK_URL,device,taskBody);
            synchronized(WorkspaceSync.class){
                if(!isStopped() && state.getLong("acknowledged",-1)<revision)
                    state.edit().putLong("acknowledged",revision).putString("status",state.getLong("revision",0)==revision?"Đã lưu MySQL":"Đang chờ lưu thay đổi tiếp theo").apply();
            }
            return Result.success();
        }catch(Exception error){
            if(isStopped())return Result.failure();
            String detail=error instanceof ApiFailure?error.getMessage():connectionMessage(error);
            state.edit().putString("status","Chưa lưu MySQL: "+detail+". Thay đổi được giữ trên điện thoại và sẽ thử lại.").apply();
            return Result.retry();
        }
    }
    private void send(String url,String device,Map<String,Object> body)throws Exception{
        String token=vn.edu.taskmanager.auth.Session.token(getApplicationContext());
        OkHttpClient client=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).writeTimeout(30,TimeUnit.SECONDS).retryOnConnectionFailure(false)
            .addInterceptor(chain->{okhttp3.Request.Builder request=chain.request().newBuilder();if(!token.isEmpty())request.header("Authorization","Bearer "+token);return chain.proceed(request.build());}).build();
        Api api=new Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(GsonConverterFactory.create(new GsonBuilder().serializeNulls().create())).build().create(Api.class);
        Response<Map<String,Object>> response=api.save(device,body).execute();
        if(!response.isSuccessful()){
            if(response.errorBody()!=null)response.errorBody().close();
            throw new ApiFailure("HTTP "+response.code()+(response.code()==404
                    ?" — Task API chưa được bật; hãy chạy Update-DevelopmentBackend.ps1 để khởi động profile dev":""));
        }
        if(response.body()==null||!(response.body().get("revision") instanceof Number)||((Number)response.body().get("revision")).longValue()<((Number)body.get("revision")).longValue())throw new ApiFailure("Máy chủ chưa xác nhận phiên bản dữ liệu");
    }
    private String connectionMessage(Exception error) {
        String detail=causedBy(error,ConnectException.class)||causedBy(error,SocketException.class)
                ?"không có dịch vụ lắng nghe hoặc chưa chuyển tiếp cổng"
                :causedBy(error,UnknownHostException.class)?"không tìm thấy địa chỉ máy chủ"
                :causedBy(error,SocketTimeoutException.class)?"máy chủ phản hồi quá thời gian chờ"
                :error.getMessage();
        int port=android.net.Uri.parse(vn.edu.taskmanager.BuildConfig.TASK_URL).getPort();
        return "không kết nối được Task Service ("+detail+"). Nếu dùng USB, chạy `adb reverse tcp:"
                +port+" tcp:"+port+"` trên máy tính";
    }
    private boolean causedBy(Throwable error,Class<? extends Throwable> type) {
        for(Throwable cause=error;cause!=null;cause=cause.getCause())if(type.isInstance(cause))return true;
        return false;
    }
    private static class ApiFailure extends Exception {ApiFailure(String message){super(message);}}
}
