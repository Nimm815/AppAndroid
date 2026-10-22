package vn.edu.taskmanager.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;

import vn.edu.taskmanager.data.LocalStore;
import vn.edu.taskmanager.data.Reminder;
import vn.edu.taskmanager.receiver.ReminderReceiver;

import java.util.List;

/**
 * Điều phối đặt lịch và hủy lịch báo thức qua AlarmManager cho UC11.
 */
public class ReminderScheduler {

    public static final String ACTION_REMIND = "vn.edu.taskmanager.ACTION_REMIND";
    public static final String EXTRA_REMINDER_ID = "extra_reminder_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_TARGET_NAME = "extra_target_name";

    /**
     * Kiểm tra ứng dụng có quyền gửi thông báo hay không (POST_NOTIFICATIONS trên Android 13+).
     */
    public static boolean hasNotificationPermission(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Kiểm tra ứng dụng có quyền đặt lịch báo thức chính xác hay không (Android 12+).
     */
    public static boolean hasExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    /**
     * Lên lịch báo thức cho một Reminder.
     */
    public static void schedule(Context context, Reminder reminder) {
        if (!reminder.isEnabled) {
            cancel(context, reminder);
            return;
        }

        long now = System.currentTimeMillis();
        long nextTrigger = reminder.calculateNextTrigger(now);
        reminder.nextTriggerMillis = nextTrigger;

        LocalStore store = new LocalStore(context);
        store.saveReminder(reminder);

        if (nextTrigger <= now) {
            cancel(context, reminder);
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = getPendingIntent(context, reminder);

        boolean canExact = hasExactAlarmPermission(context);
        try {
            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
                }
            }
        } catch (SecurityException e) {
            // Trường hợp thiếu quyền bảo mật exact alarm, fallback sang chế độ thường
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
            }
        }
    }

    /**
     * Hủy lịch báo thức của một Reminder.
     */
    public static void cancel(Context context, Reminder reminder) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || reminder == null) return;
        PendingIntent pi = getPendingIntent(context, reminder);
        am.cancel(pi);
        pi.cancel();
    }

    /**
     * Khôi phục tất cả lịch báo thức còn hiệu lực (dùng sau khởi động máy hoặc đổi múi giờ).
     */
    public static void rescheduleAll(Context context) {
        LocalStore store = new LocalStore(context);
        List<Reminder> list = store.reminders();
        long now = System.currentTimeMillis();
        for (Reminder r : list) {
            if (r.isEnabled) {
                schedule(context, r);
            } else {
                cancel(context, r);
            }
        }
    }

    private static PendingIntent getPendingIntent(Context context, Reminder reminder) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMIND);
        intent.putExtra(EXTRA_REMINDER_ID, reminder.id);
        intent.putExtra(EXTRA_TITLE, reminder.title);
        intent.putExtra(EXTRA_TARGET_NAME, reminder.targetName);

        int requestCode = Math.abs(reminder.id.hashCode());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
