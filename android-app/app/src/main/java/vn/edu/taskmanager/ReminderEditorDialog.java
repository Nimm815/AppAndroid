package vn.edu.taskmanager;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import vn.edu.taskmanager.data.LocalStore;
import vn.edu.taskmanager.data.Reminder;
import vn.edu.taskmanager.reminder.ReminderScheduler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Hộp thoại thêm và sửa lời nhắc việc (UC11).
 */
public class ReminderEditorDialog extends DialogFragment {

    private EditText editTitle;
    private Spinner spinnerHabit;
    private Button btnTime, btnDate;
    private RadioGroup radioRepeat;
    private RadioButton repeatDaily, repeatOnce, repeatWeekdays;
    private LinearLayout dateContainer, weekdaysContainer;
    private SwitchCompat switchEnabled;

    private ToggleButton[] dayToggles;
    private final int[] dayCalendarConsts = {
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    };

    private String reminderId;
    private String selectedTime = "09:00";
    private String selectedDate = LocalStore.today();
    private String repeatType = Reminder.REPEAT_DAILY;
    private final List<Integer> selectedDays = new ArrayList<>();

    private final List<String> habitIds = new ArrayList<>();
    private final List<String> habitNames = new ArrayList<>();

    public static ReminderEditorDialog create(String id) {
        ReminderEditorDialog dialog = new ReminderEditorDialog();
        Bundle args = new Bundle();
        args.putString("id", id);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        reminderId = getArguments() != null ? getArguments().getString("id") : null;
        LocalStore store = new LocalStore(requireContext());
        Reminder old = reminderId != null ? store.findReminder(reminderId) : null;

        View view = getLayoutInflater().inflate(R.layout.dialog_reminder_editor, null);
        editTitle = view.findViewById(R.id.reminderTitle);
        spinnerHabit = view.findViewById(R.id.reminderHabitChoice);
        btnTime = view.findViewById(R.id.reminderTime);
        btnDate = view.findViewById(R.id.reminderDate);
        radioRepeat = view.findViewById(R.id.reminderRepeatGroup);
        repeatDaily = view.findViewById(R.id.repeatDaily);
        repeatOnce = view.findViewById(R.id.repeatOnce);
        repeatWeekdays = view.findViewById(R.id.repeatWeekdays);
        dateContainer = view.findViewById(R.id.reminderDateContainer);
        weekdaysContainer = view.findViewById(R.id.reminderWeekdaysContainer);
        switchEnabled = view.findViewById(R.id.reminderEnabled);

        dayToggles = new ToggleButton[]{
                view.findViewById(R.id.dayMon), view.findViewById(R.id.dayTue),
                view.findViewById(R.id.dayWed), view.findViewById(R.id.dayThu),
                view.findViewById(R.id.dayFri), view.findViewById(R.id.daySat),
                view.findViewById(R.id.daySun)
        };

        // Danh sách thói quen
        habitIds.clear();
        habitNames.clear();
        habitIds.add("");
        habitNames.add("Việc riêng (không gắn thói quen)");
        for (LocalStore.Entry habit : store.entries("habit")) {
            habitIds.add(habit.id);
            habitNames.add(habit.title);
        }
        ArrayAdapter<String> habitAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, habitNames);
        spinnerHabit.setAdapter(habitAdapter);

        spinnerHabit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (position > 0 && editTitle.getText().toString().trim().isEmpty()) {
                    editTitle.setText(habitNames.get(position));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Thiết lập giờ mặc định (mốc giờ tròn tiếp theo)
        if (old == null) {
            Calendar now = Calendar.getInstance();
            now.add(Calendar.HOUR_OF_DAY, 1);
            now.set(Calendar.MINUTE, 0);
            selectedTime = String.format(Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), 0);
            repeatType = Reminder.REPEAT_DAILY;
            repeatDaily.setChecked(true);
            selectedDays.clear();
            selectedDays.add(Calendar.MONDAY);
            selectedDays.add(Calendar.WEDNESDAY);
            selectedDays.add(Calendar.FRIDAY);
        } else {
            editTitle.setText(old.title);
            selectedTime = old.time;
            selectedDate = old.date != null && !old.date.isEmpty() ? old.date : LocalStore.today();
            repeatType = old.repeatType;
            switchEnabled.setChecked(old.isEnabled);

            int hPos = habitIds.indexOf(old.targetId);
            if (hPos >= 0) spinnerHabit.setSelection(hPos);

            if (Reminder.REPEAT_ONCE.equals(repeatType)) repeatOnce.setChecked(true);
            else if (Reminder.REPEAT_WEEKDAYS.equals(repeatType)) repeatWeekdays.setChecked(true);
            else repeatDaily.setChecked(true);

            selectedDays.clear();
            if (old.daysOfWeek != null) selectedDays.addAll(old.daysOfWeek);
        }

        btnTime.setText(selectedTime);
        btnDate.setText(selectedDate);
        updateRepeatViews();

        for (int i = 0; i < dayToggles.length; i++) {
            final int calDay = dayCalendarConsts[i];
            dayToggles[i].setChecked(selectedDays.contains(calDay));
            dayToggles[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !selectedDays.contains(calDay)) {
                    selectedDays.add(calDay);
                } else if (!isChecked) {
                    selectedDays.remove(Integer.valueOf(calDay));
                }
            });
        }

        radioRepeat.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.repeatOnce) repeatType = Reminder.REPEAT_ONCE;
            else if (checkedId == R.id.repeatWeekdays) repeatType = Reminder.REPEAT_WEEKDAYS;
            else repeatType = Reminder.REPEAT_DAILY;
            updateRepeatViews();
        });

        btnTime.setOnClickListener(v -> {
            String[] parts = selectedTime.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            new TimePickerDialog(requireContext(), (view1, hourOfDay, minute) -> {
                selectedTime = String.format(Locale.ROOT, "%02d:%02d", hourOfDay, minute);
                btnTime.setText(selectedTime);
            }, h, m, true).show();
        });

        btnDate.setOnClickListener(v -> {
            String[] parts = selectedDate.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int d = Integer.parseInt(parts[2]);
            DatePickerDialog dp = new DatePickerDialog(requireContext(), (view12, year, month, dayOfMonth) -> {
                selectedDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                btnDate.setText(selectedDate);
            }, y, m, d);
            dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dp.show();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(old == null ? "Thêm nhắc việc" : "Sửa nhắc việc")
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);

        if (old != null) {
            builder.setNeutralButton(R.string.delete, (dialog1, which) -> {
                ReminderScheduler.cancel(requireContext(), old);
                store.deleteReminder(old.id);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshWorkspace();
                }
            });
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            if (title.isEmpty()) {
                editTitle.setError("Vui lòng nhập nội dung nhắc việc (1-80 ký tự)");
                return;
            }
            if (title.length() > 80) {
                editTitle.setError("Nội dung không quá 80 ký tự");
                return;
            }

            if (Reminder.REPEAT_WEEKDAYS.equals(repeatType) && selectedDays.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng chọn ít nhất một thứ trong tuần", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra trùng lặp cấu hình theo quy tắc UC11
            int habitPos = spinnerHabit.getSelectedItemPosition();
            String targetId = habitPos > 0 ? habitIds.get(habitPos) : "";
            for (Reminder existing : store.reminders()) {
                if (old != null && existing.id.equals(old.id)) continue;
                if (existing.isEnabled && existing.time.equals(selectedTime) && existing.repeatType.equals(repeatType)) {
                    if ((!targetId.isEmpty() && targetId.equals(existing.targetId)) ||
                        (targetId.isEmpty() && title.equalsIgnoreCase(existing.title.trim()))) {
                        Toast.makeText(requireContext(), "Đã có lời nhắc trùng cấu hình này", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }

            Reminder reminder = old != null ? old : new Reminder();
            reminder.title = title;
            reminder.time = selectedTime;
            reminder.repeatType = repeatType;
            reminder.isEnabled = switchEnabled.isChecked();

            if (habitPos > 0) {
                reminder.targetType = Reminder.TARGET_HABIT;
                reminder.targetId = habitIds.get(habitPos);
                reminder.targetName = habitNames.get(habitPos);
            } else {
                reminder.targetType = Reminder.TARGET_CUSTOM;
                reminder.targetId = "";
                reminder.targetName = "";
            }

            if (Reminder.REPEAT_ONCE.equals(repeatType)) {
                reminder.date = selectedDate;
            }
            if (Reminder.REPEAT_WEEKDAYS.equals(repeatType)) {
                reminder.daysOfWeek = new ArrayList<>(selectedDays);
            }

            // Lên lịch hoặc hủy
            ReminderScheduler.schedule(requireContext(), reminder);

            // Kiểm tra quyền thông báo nếu đang bật
            if (reminder.isEnabled && !ReminderScheduler.hasNotificationPermission(requireContext())) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Cần cấp quyền thông báo")
                        .setMessage("Ứng dụng cần quyền thông báo để hiển thị chuông nhắc nhở đúng giờ.\nBạn có muốn mở Cài đặt để bật quyền thông báo không?")
                        .setPositiveButton("Cài đặt", (dialogInterface, which) -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                            startActivity(intent);
                        })
                        .setNegativeButton("Để sau", null)
                        .show();
            }

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshWorkspace();
            }
            dismiss();
        }));

        return dialog;
    }

    private void updateRepeatViews() {
        dateContainer.setVisibility(Reminder.REPEAT_ONCE.equals(repeatType) ? View.VISIBLE : View.GONE);
        weekdaysContainer.setVisibility(Reminder.REPEAT_WEEKDAYS.equals(repeatType) ? View.VISIBLE : View.GONE);
    }
}
