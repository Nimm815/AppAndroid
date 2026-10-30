package vn.edu.taskmanager.sync;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** A durable pending revision; Room/preferences remain the cache for the original UI. */
public final class WorkspaceSync {
    public static SharedPreferences state(Context context){
        int user = vn.edu.taskmanager.auth.AccountSession.userId(context);
        if (user <= 0) user = context.getSharedPreferences("taskmind_account_session", Context.MODE_PRIVATE)
                .getInt("lastUserId", 0);
        return context.getSharedPreferences("native_sync_user_" + user, 0);
    }
    public static synchronized String device(Context context){
        SharedPreferences p=state(context);String id=p.getString("device","");
        if(id.isEmpty()){id=UUID.randomUUID().toString();if(!p.edit().putString("device",id).commit())throw new IllegalStateException("Không lưu được mã thiết bị");}
        return id;
    }
    public static synchronized void changed(Context context){
        device(context);SharedPreferences p=state(context);
        p.edit().putLong("revision",p.getLong("revision",0)+1)
                .putString("status","Đang đồng bộ với các dịch vụ MySQL").commit();
        enqueue(context);
    }
    public static void enqueue(Context context){
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(WorkspaceSyncWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork("native-workspace",ExistingWorkPolicy.REPLACE,work);
    }
    public static boolean configured(Context context){return !vn.edu.taskmanager.BuildConfig.TASK_URL.isEmpty()
            && !vn.edu.taskmanager.BuildConfig.HABIT_URL.isEmpty();}
}
