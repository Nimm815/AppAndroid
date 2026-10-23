package vn.edu.taskmanager.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import vn.edu.taskmanager.reminder.ReminderScheduler;

/**
 * Đón sự kiện khởi động lại thiết bị (BOOT_COMPLETED) để khôi phục các lịch nhắc hẹn giờ (UC11).
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ReminderScheduler.rescheduleAll(context);
        }
    }
}
