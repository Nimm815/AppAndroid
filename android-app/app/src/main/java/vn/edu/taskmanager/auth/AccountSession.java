package vn.edu.taskmanager.auth;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores only the current client session; passwords are never stored on Android. */
public final class AccountSession {
    private static final String PREF = "taskmind_account_session";
    private AccountSession() {}
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    public static boolean isLoggedIn(Context c) { return !prefs(c).getString("token", "").isEmpty(); }
    public static String token(Context c) { return prefs(c).getString("token", ""); }
    public static int userId(Context c) { return prefs(c).getInt("userId", 0); }
    public static boolean authenticationRequired(Context c) { return prefs(c).getBoolean("auth_required", false); }
    public static String fullName(Context c) { return prefs(c).getString("fullName", ""); }
    public static String email(Context c) { return prefs(c).getString("email", ""); }
    public static void save(Context c, org.json.JSONObject data) {
        int userId = data.optInt("userId", 0);
        prefs(c).edit().putBoolean("auth_required", true)
                .putInt("lastUserId", userId)
                .putString("token", data.optString("token", ""))
                .putInt("userId", userId)
                .putString("fullName", data.optString("fullName", ""))
                .putString("email", data.optString("email", "")).putString("role", data.optString("role", "USER")).apply();
        if (userId > 0) {
            android.content.SharedPreferences legacy = c.getSharedPreferences("auth_session", Context.MODE_PRIVATE);
            int legacyOwner = legacy.getInt("legacy_owner", 0);
            if (legacyOwner == 0) legacy.edit().putInt("legacy_owner", userId).apply();
            SharedPreferences account = prefs(c);
            if (!account.getBoolean("legacy_sync_migrated", false)) {
                migrateSyncState(c, userId);
                account.edit().putBoolean("legacy_sync_migrated", true).apply();
            }
            vn.edu.taskmanager.sync.WorkspaceSync.enqueue(c);
        }
    }
    private static void migrateSyncState(Context c, int userId) {
        String oldName = "native_sync_user_0";
        String newName = "native_sync_user_" + userId;
        android.content.SharedPreferences oldState = c.getSharedPreferences(oldName, Context.MODE_PRIVATE);
        android.content.SharedPreferences newState = c.getSharedPreferences(newName, Context.MODE_PRIVATE);
        if (oldState.getAll().isEmpty()) return;
        android.content.SharedPreferences.Editor editor = newState.edit();
        for (java.util.Map.Entry<String, ?> entry : oldState.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
        }
        editor.putLong("acknowledged", -1).putLong("task_acknowledged", -1)
                .putLong("productivity_acknowledged", -1).putString("status", "Đang đồng bộ dữ liệu tài khoản lên các dịch vụ.").apply();
    }
    public static void clear(Context c) {
        prefs(c).edit().remove("token").remove("userId").remove("fullName")
                .remove("email").remove("role").apply();
    }
}
