package vn.edu.taskmanager;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import java.util.ArrayList;
import java.util.List;

/** Dùng đồng hồ monotonic, giữ trạng thái khi xoay hoặc chuyển màn hình. */
public class TimerState extends AndroidViewModel {
    public boolean countdown, running;
    public long duration = 15 * 60 * 1000L, elapsed, anchor;
    public String habitId = "";
    public final List<Long> laps = new ArrayList<>();
    private final SharedPreferences preferences;
    public TimerState(@NonNull Application app) {
        super(app);
        preferences = app.getSharedPreferences("focus_timer", 0);
        countdown = preferences.getBoolean("countdown", false);
        duration = preferences.getLong("duration", duration);
        elapsed = preferences.getLong("elapsed", 0);
        anchor = preferences.getLong("anchor", 0);
        running = preferences.getBoolean("running", false);
        habitId = preferences.getString("habitId", "");
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (Math.abs(boot - preferences.getLong("boot", boot)) > 10000) running = false;
    }
    public long elapsedNow() { return elapsed + (running ? Math.max(0, SystemClock.elapsedRealtime() - anchor) : 0); }
    public long display() { return countdown ? Math.max(0, duration - elapsedNow()) : elapsedNow(); }
    public void start() { anchor = SystemClock.elapsedRealtime(); running = true; persist(); }
    public void pause() { elapsed = elapsedNow(); running = false; persist(); }
    public void reset() { running = false; elapsed = 0; laps.clear(); persist(); }
    public void persist() {
        preferences.edit().putBoolean("countdown", countdown).putBoolean("running", running)
                .putLong("duration", duration).putLong("elapsed", elapsed).putLong("anchor", anchor)
                .putString("habitId", habitId)
                .putLong("boot", System.currentTimeMillis() - SystemClock.elapsedRealtime()).apply();
    }
    public static String format(long value) {
        long seconds = value / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
