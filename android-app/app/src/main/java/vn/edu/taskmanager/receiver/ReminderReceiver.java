package vn.edu.taskmanager.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import vn.edu.taskmanager.MainActivity;
import vn.edu.taskmanager.R;
import vn.edu.taskmanager.data.LocalStore;
import vn.edu.taskmanager.data.Reminder;
import vn.edu.taskmanager.reminder.ReminderScheduler;

/**
 * Nhận broadcast khi đến giờ báo thức và hiển thị Notification đến người dùng (UC11).
 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "task_reminders_channel";
    public static final String CHANNEL_NAME = "Nhắc nhở công việc & thói quen";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!ReminderScheduler.ACTION_REMIND.equals(action)) return;

        String reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        String title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        String targetName = intent.getStringExtra(ReminderScheduler.EXTRA_TARGET_NAME);

        LocalStore store = new LocalStore(context);
        Reminder reminder = store.findReminder(reminderId);

        String displayTitle = title != null && !title.isEmpty() ? title : "Nhắc nhở";
        String displayContent = targetName != null && !targetName.isEmpty() ? targetName : "Đã đến giờ thực hiện kế hoạch của bạn.";

        showNotification(context, reminderId, displayTitle, displayContent);

        // Cập nhật lượt tiếp theo cho lời nhắc
        if (reminder != null) {
            if (Reminder.REPEAT_ONCE.equals(reminder.repeatType)) {
                reminder.nextTriggerMillis = 0;
                store.saveReminder(reminder);
            } else {
                ReminderScheduler.schedule(context, reminder);
            }
        }
    }

    private void showNotification(Context context, String reminderId, String title, String content) {
        NotificationManager nm = (Context.NOTIFICATION_SERVICE != null)
                ? (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE) : null;
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo nhắc nhở lịch trình và thói quen hằng ngày");
            channel.enableVibration(true);
            channel.enableLights(true);
            nm.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openApp.putExtra("open_tab", 2); // Tab Lịch & Nhắc việc

        int reqCode = reminderId != null ? Math.abs(reminderId.hashCode()) : 1001;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(context, reqCode, openApp, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(contentIntent);

        try {
            NotificationManagerCompat.from(context).notify(reqCode, builder.build());
        } catch (SecurityException e) {
            // Trường hợp quyền POST_NOTIFICATIONS bị thu hồi
        }
    }
}
