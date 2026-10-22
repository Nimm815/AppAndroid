package vn.edu.taskmanager;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import vn.edu.taskmanager.data.LocalStore;
import vn.edu.taskmanager.data.Reminder;
import vn.edu.taskmanager.reminder.ReminderScheduler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Hộp thoại tạo và sửa sự kiện lịch (UC10) kèm liên kết lời nhắc (UC11).
 */
public class EventEditorDialog extends DialogFragment {

    private EditText editTitle, editDescription;
    private SwitchCompat switchAllDay;
    private Button btnDate, btnStartTime, btnEndTime;
    private Spinner spinnerHabit, spinnerReminder;
    private LinearLayout timeContainer;

    private String selectedDate;
    private String startTime = "09:00";
    private String endTime = "09:30";
    private boolean isAllDay = false;
    private String eventId = null;

    private final List<String> habitIds = new ArrayList<>();
    private final List<String> habitNames = new ArrayList<>();

    public static EventEditorDialog create(String id, String defaultDate) {
        EventEditorDialog dialog = new EventEditorDialog();
        Bundle args = new Bundle();
        args.putString("id", id);
        args.putString("date", defaultDate);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        eventId = getArguments() != null ? getArguments().getString("id") : null;
        selectedDate = getArguments() != null ? getArguments().getString("date", LocalStore.today()) : LocalStore.today();
        if (selectedDate == null || selectedDate.isEmpty()) {
            selectedDate = LocalStore.today();
        }

        LocalStore store = new LocalStore(requireContext());
        LocalStore.Entry old = eventId != null ? store.find(eventId) : null;

        View view = getLayoutInflater().inflate(R.layout.dialog_event_editor, null);
        editTitle = view.findViewById(R.id.eventTitle);
        editDescription = view.findViewById(R.id.eventDescription);
        switchAllDay = view.findViewById(R.id.eventAllDay);
        btnDate = view.findViewById(R.id.eventDate);
        btnStartTime = view.findViewById(R.id.eventStartTime);
        btnEndTime = view.findViewById(R.id.eventEndTime);
        timeContainer = view.findViewById(R.id.eventTimeContainer);
        spinnerHabit = view.findViewById(R.id.eventHabitChoice);
        spinnerReminder = view.findViewById(R.id.eventReminderChoice);

        // Nạp danh sách thói quen
        habitIds.clear();
        habitNames.clear();
        habitIds.add("");
        habitNames.add("Không liên kết thói quen");
        for (LocalStore.Entry habit : store.entries("habit")) {
            habitIds.add(habit.id);
            habitNames.add(habit.title);
        }
        ArrayAdapter<String> habitAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, habitNames);
        spinnerHabit.setAdapter(habitAdapter);

        // Thiết lập giờ mặc định theo quy tắc UC10:
        // Hôm nay: mốc nửa giờ tiếp theo (ví dụ 09:12 -> 09:30, kết thúc 10:00). Ngày khác: 09:00 - 09:30.
        if (old == null) {
            if (LocalStore.today().equals(selectedDate)) {
                Calendar now = Calendar.getInstance();
                int minute = now.get(Calendar.MINUTE);
                if (minute < 30) {
                    now.set(Calendar.MINUTE, 30);
                } else {
                    now.add(Calendar.HOUR_OF_DAY, 1);
                    now.set(Calendar.MINUTE, 0);
                }
                startTime = String.format(Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
                now.add(Calendar.MINUTE, 30);
                endTime = String.format(Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
            } else {
                startTime = "09:00";
                endTime = "09:30";
            }
        } else {
            editTitle.setText(old.title);
            editDescription.setText(old.body);
            selectedDate = old.date;
            startTime = old.time != null && !old.time.isEmpty() ? old.time : "09:00";
            // Lấy endTime từ label (format "end:HH:mm" hoặc mặc định sau 30p)
            if (old.label != null && old.label.startsWith("end:")) {
                endTime = old.label.substring(4);
            } else {
                endTime = computeDefaultEnd(startTime);
            }
            isAllDay = "all_day".equals(old.label) || old.progress == 1; // progress == 1 lưu flag cả ngày
            int hIndex = habitIds.indexOf(old.sourceId);
            if (hIndex >= 0) spinnerHabit.setSelection(hIndex);
        }

        btnDate.setText(selectedDate);
        btnStartTime.setText(startTime);
        btnEndTime.setText(endTime);
        switchAllDay.setChecked(isAllDay);
        timeContainer.setVisibility(isAllDay ? View.GONE : View.VISIBLE);

        setupReminderOptions();

        switchAllDay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAllDay = isChecked;
            timeContainer.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            setupReminderOptions();
        });

        btnDate.setOnClickListener(v -> {
            String[] parts = selectedDate.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int d = Integer.parseInt(parts[2]);
            new DatePickerDialog(requireContext(), (view1, year, month, dayOfMonth) -> {
                selectedDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                btnDate.setText(selectedDate);
            }, y, m, d).show();
        });

        btnStartTime.setOnClickListener(v -> {
            String[] parts = startTime.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            new TimePickerDialog(requireContext(), (view12, hourOfDay, minute) -> {
                startTime = String.format(Locale.ROOT, "%02d:%02d", hourOfDay, minute);
                btnStartTime.setText(startTime);
                // Tự động đẩy kết thúc sau 30 phút nếu kết thúc <= bắt đầu
                if (endTime.compareTo(startTime) <= 0) {
                    endTime = computeDefaultEnd(startTime);
                    btnEndTime.setText(endTime);
                }
            }, h, m, true).show();
        });

        btnEndTime.setOnClickListener(v -> {
            String[] parts = endTime.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            new TimePickerDialog(requireContext(), (view13, hourOfDay, minute) -> {
                endTime = String.format(Locale.ROOT, "%02d:%02d", hourOfDay, minute);
                btnEndTime.setText(endTime);
            }, h, m, true).show();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(old == null ? "Thêm sự kiện lịch" : "Sửa sự kiện lịch")
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);

        if (old != null) {
            builder.setNeutralButton(R.string.delete, (dialog1, which) -> confirmDelete(old));
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            if (title.isEmpty()) {
                editTitle.setError("Vui lòng nhập tiêu đề sự kiện (1-80 ký tự)");
                return;
            }
            if (title.length() > 80) {
                editTitle.setError("Tiêu đề không quá 80 ký tự");
                return;
            }

            if (!isAllDay) {
                if (endTime.compareTo(startTime) <= 0) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Thời gian không hợp lệ")
                            .setMessage("Thời gian kết thúc phải sau thời gian bắt đầu.")
                            .setPositiveButton("Đồng ý", null)
                            .show();
                    return;
                }
            }

            LocalStore.Entry entry = old != null ? old : new LocalStore.Entry();
            entry.type = "event";
            entry.title = title;
            entry.body = editDescription.getText().toString().trim();
            entry.date = selectedDate;
            entry.time = isAllDay ? "00:00" : startTime;
            entry.label = isAllDay ? "all_day" : "end:" + endTime;
            entry.progress = isAllDay ? 1 : 0;

            int habitPos = spinnerHabit.getSelectedItemPosition();
            if (habitPos > 0) {
                entry.sourceId = habitIds.get(habitPos);
                entry.sourceType = "habit";
            } else {
                entry.sourceId = "";
                entry.sourceType = "";
            }

            store.save(entry);

            // Xử lý lời nhắc đi kèm (UC11)
            handleReminderForEvent(requireContext(), entry, spinnerReminder.getSelectedItemPosition(), isAllDay, startTime, selectedDate);

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).entrySaved(entry);
                ((MainActivity) getActivity()).refreshWorkspace();
            }
            dismiss();
        }));

        return dialog;
    }

    private void setupReminderOptions() {
        String[] options;
        if (isAllDay) {
            options = new String[]{"Không nhắc", "Đúng lúc (09:00)"};
        } else {
            options = new String[]{"Không nhắc", "Đúng lúc", "Trước 15 phút"};
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, options);
        spinnerReminder.setAdapter(adapter);

        if (eventId != null) {
            LocalStore store = new LocalStore(requireContext());
            Reminder r = store.findReminderByEvent(eventId);
            if (r != null && r.isEnabled) {
                if (isAllDay) {
                    spinnerReminder.setSelection(1);
                } else {
                    spinnerReminder.setSelection(r.eventReminderMinutes == 15 ? 2 : 1);
                }
            } else {
                spinnerReminder.setSelection(0);
            }
        }
    }

    private static String computeDefaultEnd(String start) {
        try {
            String[] parts = start.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) + 30;
            if (m >= 60) {
                h = (h + 1) % 24;
                m %= 60;
            }
            return String.format(Locale.ROOT, "%02d:%02d", h, m);
        } catch (Exception e) {
            return "10:00";
        }
    }

    private void handleReminderForEvent(Context context, LocalStore.Entry entry, int reminderChoice,
                                        boolean allDay, String startTimeStr, String dateStr) {
        LocalStore store = new LocalStore(context);
        if (reminderChoice == 0) {
            // "Không nhắc": Hủy và xóa lời nhắc cũ nếu có
            Reminder oldRem = store.findReminderByEvent(entry.id);
            if (oldRem != null) {
                ReminderScheduler.cancel(context, oldRem);
                store.deleteReminder(oldRem.id);
            }
            return;
        }

        Reminder reminder = store.findReminderByEvent(entry.id);
        if (reminder == null) {
            reminder = new Reminder();
        }
        reminder.title = entry.title;
        reminder.targetType = Reminder.TARGET_EVENT;
        reminder.targetId = entry.id;
        reminder.targetName = "Sự kiện: " + entry.title;
        reminder.isFromEvent = true;
        reminder.repeatType = Reminder.REPEAT_ONCE;
        reminder.date = dateStr;
        reminder.isEnabled = true;

        if (allDay) {
            reminder.time = "09:00";
            reminder.eventReminderMinutes = 0;
        } else if (reminderChoice == 2) { // Trước 15 phút
            reminder.eventReminderMinutes = 15;
            String[] parts = startTimeStr.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 15;
            if (m < 0) {
                h = (h - 1 + 24) % 24;
                m += 60;
            }
            reminder.time = String.format(Locale.ROOT, "%02d:%02d", h, m);
        } else { // Đúng lúc
            reminder.eventReminderMinutes = 0;
            reminder.time = startTimeStr;
        }

        ReminderScheduler.schedule(context, reminder);
    }

    private void confirmDelete(LocalStore.Entry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa sự kiện?")
                .setMessage("Bạn có muốn xóa sự kiện và lời nhắc liên quan không?")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    LocalStore store = new LocalStore(requireContext());
                    Reminder rem = store.findReminderByEvent(entry.id);
                    if (rem != null) {
                        ReminderScheduler.cancel(requireContext(), rem);
                        store.deleteReminder(rem.id);
                    }
                    store.delete(entry.id);
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshWorkspace();
                    }
                })
                .show();
    }
}
