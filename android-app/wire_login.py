from pathlib import Path
base=Path('app/src/main/java/vn/edu/taskmanager')
def edit(p,a,b):
 s=p.read_text(encoding='utf-8');assert a in s,(p,a);p.write_text(s.replace(a,b),encoding='utf-8')
edit(Path('app/build.gradle'),"    compileSdk 35",'''    compileSdk 35
    buildFeatures { buildConfig true }
    buildTypes {
        debug {
            buildConfigField 'String', 'AUTH_URL', '"http://127.0.0.1:8081/"'
            buildConfigField 'String', 'TASK_URL', '"http://127.0.0.1:8082/"'
            buildConfigField 'String', 'HABIT_URL', '"http://127.0.0.1:8083/"'
        }
        release {
            buildConfigField 'String', 'AUTH_URL', groovy.json.JsonOutput.toJson(providers.gradleProperty('authUrl').getOrElse(''))
            buildConfigField 'String', 'TASK_URL', groovy.json.JsonOutput.toJson(providers.gradleProperty('taskUrl').getOrElse(''))
            buildConfigField 'String', 'HABIT_URL', groovy.json.JsonOutput.toJson(providers.gradleProperty('habitUrl').getOrElse(''))
        }
    }''')
edit(Path('app/src/main/AndroidManifest.xml'),'<activity android:name=".MainActivity"','<activity android:name=".auth.LoginActivity" android:exported="false" />\n        <activity android:name=".MainActivity"')
edit(base/'OnboardingActivity.java','new Intent(this, MainActivity.class)','new Intent(this, vn.edu.taskmanager.auth.LoginActivity.class)')
p=base/'MainActivity.java';s=p.read_text(encoding='utf-8');s=s.replace('        super.onCreate(state);','''        super.onCreate(state);
        if (vn.edu.taskmanager.auth.Session.token(this).isEmpty()) {
            startActivity(new android.content.Intent(this, vn.edu.taskmanager.auth.LoginActivity.class)); finish(); return;
        }''');s=s.replace('        super.onResume();','''        super.onResume();
        if (vn.edu.taskmanager.auth.Session.token(this).isEmpty()) {
            startActivity(new android.content.Intent(this, vn.edu.taskmanager.auth.LoginActivity.class)); finish(); return;
        }''')
s=s.replace('        if (state == null && !vn.edu.taskmanager.sync.WorkspaceSync.configured(this)) showBackendConnection();','')
start=s.index('    public void showBackendConnection()');end=s.index('    private void renderTasks()',start);s=s[:start]+s[end:]
s=s.replace('            if (status.startsWith("Đã lưu") || status.startsWith("Chưa lưu"))','''            if (vn.edu.taskmanager.auth.Session.token(this).isEmpty()) {
                startActivity(new android.content.Intent(this, vn.edu.taskmanager.auth.LoginActivity.class)); finish(); return;
            }
            if (status.startsWith("Chưa lưu"))''');p.write_text(s,encoding='utf-8')
p=base/'WorkspaceController.java';s=p.read_text(encoding='utf-8').replace('activity.getString(R.string.local_account_detail)','vn.edu.taskmanager.auth.Session.email(activity)').replace('                .setNeutralButton("Kết nối máy chủ", (dialog, which) -> activity.showBackendConnection())\n','');p.write_text(s,encoding='utf-8')
p=base/'sync/WorkspaceSync.java';s=p.read_text(encoding='utf-8').replace('getSharedPreferences("native_sync",0)','getSharedPreferences("native_sync_user_"+vn.edu.taskmanager.auth.Session.user(context),0)');start=s.index('    public static boolean configured(');s=s[:start]+'''    public static boolean configured(Context context){return !vn.edu.taskmanager.auth.Session.token(context).isEmpty();}
}
''';p.write_text(s,encoding='utf-8')
p=base/'sync/WorkspaceSyncWorker.java';s=p.read_text(encoding='utf-8').replace('Chưa lưu MySQL: cần thiết lập kết nối máy chủ','Vui lòng đăng nhập để tiếp tục lưu dữ liệu')
s=s.replace('        try {\n            String device','        String token=vn.edu.taskmanager.auth.Session.token(context);\n        try {\n            String device')
s=s.replace('            var connection=context.getSharedPreferences("backend_connection",0);','')
s=s.replace('send(connection.getString("taskUrl",""),device,taskBody);','send(vn.edu.taskmanager.BuildConfig.TASK_URL,device,taskBody,token);').replace('send(connection.getString("habitUrl",""),device,habitBody);','send(vn.edu.taskmanager.BuildConfig.HABIT_URL,device,habitBody,token);')
s=s.replace('String detail=error instanceof ApiFailure?', '''if(error instanceof AuthExpired){
                vn.edu.taskmanager.auth.Session.expire(context,token);
                state.edit().putString("status","Chưa lưu: phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.").apply();
                return Result.success();
            }
            String detail=error instanceof ApiFailure?''')
s=s.replace('String device,Map<String,Object> body)throws Exception','String device,Map<String,Object> body,String token)throws Exception')
s=s.replace('.retryOnConnectionFailure(false).build();','.retryOnConnectionFailure(false).addInterceptor(chain->chain.proceed(chain.request().newBuilder().header("Authorization","Bearer "+token).build())).build();')
s=s.replace('throw new ApiFailure("HTTP "+response.code()', 'if(response.code()==401)throw new AuthExpired();throw new ApiFailure("HTTP "+response.code()')
s=s.replace('    private static class ApiFailure','    private static class AuthExpired extends Exception {}\n    private static class ApiFailure');p.write_text(s,encoding='utf-8')
# Keep legacy data for the first authenticated account; isolate other accounts.
for name,pref in [('LocalStore','personal_workspace'),('DailyHistory','daily_activity_history')]:
 p=base/f'data/{name}.java';edit(p,f'getSharedPreferences("{pref}",',f'getSharedPreferences(vn.edu.taskmanager.auth.Session.storage(context,"{pref}"),')
p=base/'data/TaskDatabase.java';s=p.read_text(encoding='utf-8');start=s.index('    public static TaskDatabase getInstance(')
s=s[:start]+'''    private static final java.util.Map<String,TaskDatabase> databases = new java.util.HashMap<>();
    public static synchronized TaskDatabase getInstance(Context context) {
        String name=vn.edu.taskmanager.auth.Session.storage(context,"personal_tasks.db");
        TaskDatabase database=databases.get(name);
        if(database==null){
            database=Room.databaseBuilder(context.getApplicationContext(),TaskDatabase.class,name)
                .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6).build();
            databases.put(name,database);
        }
        return database;
    }
}
''';s=s.replace('    private static volatile TaskDatabase instance;\n','');p.write_text(s,encoding='utf-8')
