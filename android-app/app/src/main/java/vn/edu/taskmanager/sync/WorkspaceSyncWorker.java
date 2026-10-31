package vn.edu.taskmanager.sync;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.*;
import retrofit2.http.*;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.taskmanager.cloud.CloudModels;
import vn.edu.taskmanager.data.*;

/** Đồng bộ dữ liệu hiện có với CRUD API của task-service và productivity-service. */
public final class WorkspaceSyncWorker extends Worker {
    interface TaskApi {
        @GET("api/lists") Call<List<CloudModels.TaskList>> lists();
        @POST("api/lists") Call<CloudModels.TaskList> createList(@Body Map<String,Object> body);
        @PUT("api/lists/{id}") Call<CloudModels.TaskList> updateList(@Path("id") int id,@Body Map<String,Object> body);
        @DELETE("api/lists/{id}") Call<Void> deleteList(@Path("id") int id);
        @GET("api/tasks") Call<List<CloudModels.Task>> tasks(@Query("status") String status,@Query("sort") String sort);
        @POST("api/tasks") Call<CloudModels.Task> createTask(@Body Map<String,Object> body);
        @PUT("api/tasks/{id}") Call<CloudModels.Task> updateTask(@Path("id") int id,@Body Map<String,Object> body);
        @DELETE("api/tasks/{id}") Call<Void> deleteTask(@Path("id") int id);
        @PATCH("api/tasks/{id}/completion") Call<CloudModels.Task> complete(@Path("id") int id,@Body Map<String,Object> body);
        @PATCH("api/tasks/{id}/progress") Call<CloudModels.Task> progress(@Path("id") int id,@Body Map<String,Object> body);
    }
    interface CalendarApi {
        @GET("api/calendar-events") Call<List<CalendarEvent>> events();
        @POST("api/calendar-events") Call<CalendarEvent> create(@Body Map<String,Object> body);
        @PUT("api/calendar-events/{id}") Call<CalendarEvent> update(@Path("id") int id,@Body Map<String,Object> body);
        @DELETE("api/calendar-events/{id}") Call<Void> delete(@Path("id") int id);
    }
    static final class CalendarEvent {
        int eventId; String title,description,startTime,endTime; Boolean allDay;
    }

    private final Gson gson = new Gson();
    public WorkspaceSyncWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}

    @NonNull @Override public Result doWork() {
        Context context=getApplicationContext();
        SharedPreferences state=WorkspaceSync.state(context);
        if(!WorkspaceSync.configured(context)) {
            state.edit().putString("status","Chưa cấu hình địa chỉ API.").apply(); return Result.success();
        }
        if(!vn.edu.taskmanager.auth.AccountSession.isLoggedIn(context)) {
            state.edit().putString("status","Đăng nhập để đồng bộ công việc và lịch; dữ liệu cục bộ vẫn được giữ.").apply();
            return Result.success();
        }
        try {
            TaskDatabase db=TaskDatabase.getInstance(context);
            LegacyTaskListImporter.importOnce(context,db);
            TaskApi tasks=service(vn.edu.taskmanager.BuildConfig.TASK_URL,context).create(TaskApi.class);
            CalendarApi calendar=service(vn.edu.taskmanager.BuildConfig.HABIT_URL,context).create(CalendarApi.class);
            syncTasks(context,db,tasks);
            syncEvents(context,calendar);
            state.edit().putString("status","Đã đồng bộ công việc, danh sách và sự kiện lịch lên MySQL.").apply();
            return Result.success();
        } catch(Exception error) {
            if(isStopped())return Result.failure();
            String message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
            state.edit().putString("status","Chưa đồng bộ MySQL: "+message+". Dữ liệu cục bộ vẫn được giữ; ứng dụng sẽ thử lại.").apply();
            return Result.retry();
        }
    }

    private Retrofit service(String base,Context context) {
        OkHttpClient client=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS)
                .readTimeout(30,TimeUnit.SECONDS).writeTimeout(30,TimeUnit.SECONDS)
                .addInterceptor(chain->{String token=vn.edu.taskmanager.auth.Session.token(context);
                    okhttp3.Request.Builder request=chain.request().newBuilder();
                    if(token!=null&&!token.isEmpty())request.header("Authorization","Bearer "+token);
                    return chain.proceed(request.build());}).build();
        return new Retrofit.Builder().baseUrl(base).client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build();
    }

    private void syncTasks(Context context,TaskDatabase db,TaskApi api)throws Exception {
        SharedPreferences state=WorkspaceSync.state(context);
        Map<String,Integer> listIds=readIds(state,"remote_lists");
        Map<String,Integer> taskIds=readIds(state,"remote_tasks");
        List<CloudModels.TaskList> remoteLists=execute(api.lists());
        Map<Integer,Integer> remoteListToLocal=new HashMap<>();
        for(CloudModels.TaskList remote:remoteLists) {
            String localId=findLocalListId(listIds,remote.id);
            TaskList local=localId==null?null:db.taskListDao().find(localId);
            if(local==null) local=findListByTitle(db,remote.name);
            if(local==null) {
                local=new TaskList(); local.id="server-list-"+remote.id; local.title=remote.name; local.createdAt=System.currentTimeMillis();
                db.taskListDao().insertFromSync(local);
            }
            listIds.put(local.id,remote.id);
        }
        List<TaskListWithTasks> localLists=db.taskListDao().allLists();
        Set<String> localListKeys=new HashSet<>();
        Set<Integer> matchedRemoteLists=new HashSet<>(listIds.values());
        for(TaskListWithTasks row:localLists) {
            TaskList local=row.list;localListKeys.add(local.id);
            Integer remoteId=listIds.get(local.id);Map<String,Object> body=new LinkedHashMap<>();
            body.put("name",local.title);body.put("color","#5B6FDE");
            if(remoteId==null)for(CloudModels.TaskList candidate:remoteLists)
                if(!matchedRemoteLists.contains(candidate.id)&&Objects.equals(candidate.name,local.title)){remoteId=candidate.id;break;}
            CloudModels.TaskList saved=remoteId==null?execute(api.createList(body)):execute(api.updateList(remoteId,body));
            listIds.put(local.id,saved.id);
            matchedRemoteLists.add(saved.id);
        }
        for(String old:new ArrayList<>(listIds.keySet())) if(!localListKeys.contains(old)) {
            executeVoid(api.deleteList(listIds.get(old)));listIds.remove(old);
        }

        List<CloudModels.Task> remoteTasks=execute(api.tasks("ALL","due"));
        Map<Integer,CloudModels.Task> byRemoteId=new HashMap<>();
        for(CloudModels.Task t:remoteTasks)byRemoteId.put(t.id,t);
        Set<Integer> knownTaskIds=new HashSet<>(taskIds.values());
        for(CloudModels.Task remote:remoteTasks) {
            if(knownTaskIds.contains(remote.id))continue;
            Task local=findMatchingTask(db,remote);
            if(local==null) {
                String title=remote.title==null?"":remote.title;
                local=new Task(title);local.scheduledDate=remote.dueDate==null?"":remote.dueDate.substring(0,10);
                local.priority=localPriority(remote.priority);local.progress=remote.progress;local.completed=remote.progress>=100;
                local.note=remote.description==null?"":remote.description;
                long newId=db.taskDao().insertFromSync(local);if(newId<1)continue;local.id=newId;
                if(remote.listId!=null)for(CloudModels.TaskList list:remoteLists)if(list.id==remote.listId) {
                    String localList=findLocalListId(listIds,list.id);
                    if(localList!=null)db.taskListDao().addMember(new TaskListMember(localList,newId));
                }
            }
            taskIds.put(String.valueOf(local.id),remote.id);knownTaskIds.add(remote.id);
        }
        List<Task> localTasks=db.taskDao().allTasks();
        Set<String> localKeys=new HashSet<>();
        Set<Integer> matchedRemoteTasks=new HashSet<>(taskIds.values());
        for(Task local:localTasks) {
            String key=String.valueOf(local.id);localKeys.add(key);
            Map<String,Object> body=taskBody(local,db,listIds);
            Integer remoteId=taskIds.get(key);
            if(remoteId==null)for(CloudModels.Task candidate:remoteTasks)
                if(!matchedRemoteTasks.contains(candidate.id)&&Objects.equals(candidate.title,local.title)
                        &&Objects.equals(candidate.dueDate==null?"":candidate.dueDate.substring(0,10),local.scheduledDate==null?"":local.scheduledDate)){remoteId=candidate.id;break;}
            CloudModels.Task saved;
            if(remoteId!=null&&byRemoteId.containsKey(remoteId)) saved=execute(api.updateTask(remoteId,body));
            else saved=execute(api.createTask(body));
            taskIds.put(key,saved.id);
            matchedRemoteTasks.add(saved.id);
            if(local.progress!=saved.progress) execute(api.progress(saved.id,Collections.singletonMap("progress",local.progress)));
        }
        for(String key:new ArrayList<>(taskIds.keySet()))if(!localKeys.contains(key)) {
            executeVoid(api.deleteTask(taskIds.get(key)));taskIds.remove(key);
        }
        state.edit().putString("remote_lists",gson.toJson(listIds)).putString("remote_tasks",gson.toJson(taskIds)).apply();
    }

    private Map<String,Object> taskBody(Task local,TaskDatabase db,Map<String,Integer> lists) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("title",local.title);body.put("description",local.note==null||local.note.isBlank()?null:local.note);
        body.put("notes",null);body.put("priority",switch(local.priority){case 1->"URGENT";case 3->"HIGH";case 4->"LOW";default->"MEDIUM";});
        String date=local.scheduledDate==null? "":local.scheduledDate;
        if(!date.isBlank()){body.put("startDate",date+"T00:00:00");body.put("dueDate",date+"T23:59:00");}
        for(TaskListWithTasks row:db.taskListDao().allLists())for(Task t:row.tasks)
            if(t.id==local.id){Integer listId=lists.get(row.list.id);if(listId!=null)body.put("listId",listId);}
        return body;
    }
    private Task findMatchingTask(TaskDatabase db,CloudModels.Task remote) {
        String date=remote.dueDate==null?"":remote.dueDate.substring(0,10);
        for(Task local:db.taskDao().allTasks())
            if(Objects.equals(local.title,remote.title)&&Objects.equals(local.scheduledDate==null?"":local.scheduledDate,date))return local;
        return null;
    }
    private int localPriority(String value) {
        if("URGENT".equals(value))return 1;if("HIGH".equals(value))return 3;if("LOW".equals(value))return 4;return 2;
    }

    private void syncEvents(Context context,CalendarApi api)throws Exception {
        SharedPreferences state=WorkspaceSync.state(context);
        Map<String,Integer> ids=readIds(state,"remote_events");
        List<CalendarEvent> remote=execute(api.events());
        Map<Integer,CalendarEvent> byId=new HashMap<>();for(CalendarEvent e:remote)byId.put(e.eventId,e);
        LocalStore store=new LocalStore(context);List<LocalStore.Entry> entries=store.entries("event");
        Set<Integer> mapped=new HashSet<>(ids.values());
        for(CalendarEvent value:remote)if(!mapped.contains(value.eventId)) {
            LocalStore.Entry local=findMatchingEvent(entries,value);
            if(local==null) {
                local=new LocalStore.Entry();local.id="server-event-"+value.eventId;local.type="event";
                local.title=value.title==null?"":value.title;local.body=value.description==null?"":value.description;
                setEntryDateTime(local,value.startTime);
                boolean allDay=Boolean.TRUE.equals(value.allDay);
                local.progress=allDay?1:0;
                local.label=allDay?"all_day":"end:"+timeOf(value.endTime);
                store.saveFromSync(local);entries.add(local);
            }
            ids.put(local.id,value.eventId);mapped.add(value.eventId);
        }
        Set<String> keys=new HashSet<>();
        for(LocalStore.Entry entry:entries) {
            if("server_api".equals(entry.sourceType))continue;
            keys.add(entry.id);Map<String,Object> body=eventBody(entry);
            Integer id=ids.get(entry.id);CalendarEvent saved;
            if(id!=null&&byId.containsKey(id))saved=execute(api.update(id,body));else saved=execute(api.create(body));
            ids.put(entry.id,saved.eventId);
        }
        for(String key:new ArrayList<>(ids.keySet()))if(!keys.contains(key)) {
            executeVoid(api.delete(ids.get(key)));ids.remove(key);
        }
        state.edit().putString("remote_events",gson.toJson(ids)).apply();
    }
    private Map<String,Object> eventBody(LocalStore.Entry e) {
        boolean allDay="all_day".equals(e.label)||e.progress==1;
        String start=allDay?e.date+"T00:00:00":e.date+"T"+safeTime(e.time)+":00";
        String end;
        if(allDay)end=addDays(e.date,1)+"T00:00:00";
        else {String time=e.label!=null&&e.label.startsWith("end:")?e.label.substring(4):"";
            if(time.isBlank())time=addMinutes(safeTime(e.time),30);end=e.date+"T"+time+":00";}
        Map<String,Object> body=new LinkedHashMap<>();body.put("title",e.title);body.put("description",e.body);
        body.put("startTime",start);body.put("endTime",end);body.put("allDay",allDay);return body;
    }
    private String safeTime(String value){return value==null||value.length()<5?"09:00":value.substring(0,5);}
    private String timeOf(String value){return value!=null&&value.length()>=16?value.substring(11,16):"09:30";}
    private void setEntryDateTime(LocalStore.Entry entry,String value){
        if(value!=null&&value.length()>=16){entry.date=value.substring(0,10);entry.time=value.substring(11,16);}
        else {entry.date=LocalStore.today();entry.time="09:00";}
    }
    private LocalStore.Entry findMatchingEvent(List<LocalStore.Entry> entries,CalendarEvent remote){
        String date=remote.startTime!=null&&remote.startTime.length()>=10?remote.startTime.substring(0,10):"";
        for(LocalStore.Entry local:entries)if(Objects.equals(local.title,remote.title)&&Objects.equals(local.date,date))return local;
        return null;
    }
    private String addDays(String date,int days){try{java.time.LocalDate parsed=java.time.LocalDate.parse(date);return parsed.plusDays(days).toString();}catch(Exception e){return LocalStore.today();}}
    private String addMinutes(String time,int amount){try{return java.time.LocalTime.parse(time).plusMinutes(amount).toString()+":00";}catch(Exception e){return "09:30:00";}}

    private TaskList findListByTitle(TaskDatabase db,String title){for(TaskListWithTasks row:db.taskListDao().allLists())if(Objects.equals(row.list.title,title))return row.list;return null;}
    private String findLocalListId(Map<String,Integer> ids,int remote){for(Map.Entry<String,Integer> e:ids.entrySet())if(e.getValue()==remote)return e.getKey();return null;}
    private Map<String,Integer> readIds(SharedPreferences p,String key){try{java.lang.reflect.Type type=new com.google.gson.reflect.TypeToken<Map<String,Integer>>(){}.getType();Map<String,Integer> v=gson.fromJson(p.getString(key,"{}"),type);return v==null?new LinkedHashMap<>():v;}catch(Exception e){return new LinkedHashMap<>();}}
    private <T>T execute(Call<T> call)throws Exception{Response<T> r=call.execute();if(!r.isSuccessful())throw new SyncException("HTTP "+r.code()+" "+(r.errorBody()==null?"":r.errorBody().string()));return r.body();}
    private void executeVoid(Call<Void> call)throws Exception{Response<Void> r=call.execute();if(!r.isSuccessful())throw new SyncException("HTTP "+r.code()+" "+(r.errorBody()==null?"":r.errorBody().string()));}
    private static final class SyncException extends Exception{SyncException(String message){super(message);}}
}
