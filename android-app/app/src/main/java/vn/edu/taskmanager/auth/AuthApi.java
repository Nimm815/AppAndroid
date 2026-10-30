package vn.edu.taskmanager.auth;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android client for the TaskMind Spring Boot authentication API. */
public final class AuthApi {
    // Android Emulator -> host machine. For a physical phone, replace with the computer's LAN IP.

    public static final String BASE_URL = vn.edu.taskmanager.BuildConfig.AUTH_URL + "api/auth";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private AuthApi() {}

    public interface Callback { void onResult(boolean success, JSONObject data, String message); }

    public static void register(String fullName, String email, String password, Callback callback) {
        JSONObject body = new JSONObject();
        try { body.put("fullName", fullName); body.put("email", email); body.put("password", password); }
        catch (Exception e) { callback.onResult(false, null, "Dữ liệu không hợp lệ"); return; }
        request("/register", body, null, callback);
    }

    public static void login(String email, String password, Callback callback) {
        JSONObject body = new JSONObject();
        try { body.put("email", email); body.put("password", password); }
        catch (Exception e) { callback.onResult(false, null, "Dữ liệu không hợp lệ"); return; }
        request("/login", body, null, callback);
    }

    public static void deleteAccount(String password, String token, Callback callback) {
        JSONObject body = new JSONObject();
        try { body.put("password", password); }
        catch (Exception e) { callback.onResult(false, null, "Dữ liệu không hợp lệ"); return; }
        request("/delete-account", body, token, callback);
    }
    public static void changePassword(
            String currentPassword,
            String newPassword,
            String token,
            Callback callback) {

        JSONObject body = new JSONObject();

        try {
            body.put("currentPassword", currentPassword);
            body.put("newPassword", newPassword);
        } catch (Exception e) {
            callback.onResult(false, null, "Dữ liệu không hợp lệ");
            return;
        }

        request("/change-password", body, token, callback);
    }


    private static void request(String path, JSONObject body, String token, Callback callback) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            boolean success = false; JSONObject response = null; String message = "Không thể kết nối máy chủ";
            try {
                connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder text = new StringBuilder();
                if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) text.append(line);
                }
                if (text.length() > 0) response = new JSONObject(text.toString());
                success = code >= 200 && code < 300;
                if (response != null) message = response.optString("message", success ? "Thành công" : "Yêu cầu thất bại");
                else message = success ? "Thành công" : "Yêu cầu thất bại (HTTP " + code + ")";
            } catch (Exception e) {
                android.util.Log.e("TaskMindAuth",
                        "Lỗi gọi API: " + BASE_URL + path, e);

                message = "Lỗi kết nối: " +
                        (e.getMessage() != null
                                ? e.getMessage()
                                : e.getClass().getSimpleName());
            } finally { if (connection != null) connection.disconnect(); }
            final boolean ok = success; final JSONObject data = response; final String msg = message;
            MAIN.post(() -> callback.onResult(ok, data, msg));
        });
    }
}
