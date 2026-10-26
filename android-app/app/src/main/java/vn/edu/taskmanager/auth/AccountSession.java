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
    public static String fullName(Context c) { return prefs(c).getString("fullName", ""); }
    public static String email(Context c) { return prefs(c).getString("email", ""); }
    public static void save(Context c, org.json.JSONObject data) {
        prefs(c).edit().putString("token", data.optString("token", ""))
                .putString("fullName", data.optString("fullName", ""))
                .putString("email", data.optString("email", "")).putString("role", data.optString("role", "USER")).apply();
    }
    public static void clear(Context c) { prefs(c).edit().clear().apply(); }
}
