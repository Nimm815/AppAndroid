package vn.edu.taskmanager;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import vn.edu.taskmanager.cloud.CloudModels;
import vn.edu.taskmanager.cloud.CloudRepository;

public final class HabitEditorDialog extends DialogFragment {
    static final String COLOR_RESULT_KEY = "habit_color_selected";
    static final String COLOR_RESULT_VALUE = "color";
    private static final String[] DAYS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
    private final List<CheckBox> weekdays = new ArrayList<>();
    private EditText name, description, icon, targetCount, targetUnit;
    private CheckBox reminderEnabled;
    private Button startDateButton, reminderTimeButton, saveButton, colorButton;
    private String startDate, reminderTime, selectedColor;

    public HabitEditorDialog() {}

    static HabitEditorDialog create(CloudModels.Habit habit) {
        return create(habit, vn.edu.taskmanager.data.LocalStore.today());
    }

    static HabitEditorDialog create(CloudModels.Habit habit, String initialDate) {
        HabitEditorDialog dialog = new HabitEditorDialog();
        Bundle arguments = new Bundle();
        if (habit != null) {
            arguments.putInt("id", habit.id);
            arguments.putString("name", habit.name);
            arguments.putString("description", habit.description);
            arguments.putString("icon", habit.icon);
            arguments.putString("color", habit.color);
            arguments.putInt("weekdays", habit.weekdays);
            arguments.putString("startDate", habit.startDate);
            arguments.putInt("targetCount", habit.targetCount);
            arguments.putString("targetUnit", habit.targetUnit);
            arguments.putString("reminderTime", habit.reminderTime);
            arguments.putBoolean("reminderEnabled", habit.reminderEnabled);
        } else {
            arguments.putString("startDate", initialDate);
            arguments.putInt("weekdays", defaultWeekdays(initialDate));
        }
        dialog.setArguments(arguments);
        return dialog;
    }

    private static int defaultWeekdays(String date) {
        String[] parts = date.split("-");
        Calendar value = Calendar.getInstance();
        value.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                Integer.parseInt(parts[2]), 12, 0, 0);
        int index = value.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? 6
                : value.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
        return 31 | (1 << index);
    }

    @NonNull @Override public android.app.Dialog onCreateDialog(Bundle state) {
        getParentFragmentManager().setFragmentResultListener(COLOR_RESULT_KEY, this,
                (key, result) -> {
                    selectedColor = result.getString(COLOR_RESULT_VALUE, "#5B6FDE");
                    updateColorButton();
                });
        View view = getLayoutInflater().inflate(R.layout.dialog_habit, null);
        name = view.findViewById(R.id.habitName);
        description = view.findViewById(R.id.habitDescription);
        icon = view.findViewById(R.id.habitIcon);
        colorButton = view.findViewById(R.id.habitColor);
        targetCount = view.findViewById(R.id.habitTargetCount);
        targetUnit = view.findViewById(R.id.habitTargetUnit);
        reminderEnabled = view.findViewById(R.id.habitReminderEnabled);
        startDateButton = view.findViewById(R.id.habitStartDate);
        reminderTimeButton = view.findViewById(R.id.habitReminderTime);
        Bundle args = requireArguments();
        int id = args.getInt("id", 0);
        startDate = state == null ? args.getString("startDate", vn.edu.taskmanager.data.LocalStore.today())
                : state.getString("startDate", vn.edu.taskmanager.data.LocalStore.today());
        reminderTime = state == null ? args.getString("reminderTime", "09:00")
                : state.getString("reminderTime", "09:00");
        selectedColor = state == null ? args.getString("color", "#5B6FDE")
                : state.getString("color", "#5B6FDE");
        name.setText(state == null ? args.getString("name", "") : state.getString("name", ""));
        description.setText(state == null ? args.getString("description", "") : state.getString("description", ""));
        icon.setText(state == null ? args.getString("icon", "🌱") : state.getString("icon", "🌱"));
        updateColorButton();
        targetCount.setText(state == null ? String.valueOf(args.getInt("targetCount", 1)) : state.getString("targetCount", "1"));
        targetUnit.setText(state == null ? args.getString("targetUnit", "lần") : state.getString("targetUnit", "lần"));
        reminderEnabled.setChecked(state == null && args.getBoolean("reminderEnabled", false)
                || state != null && state.getBoolean("reminderEnabled"));
        startDateButton.setText("Ngày bắt đầu · " + startDate);
        reminderTimeButton.setText("Giờ nhắc · " + reminderTime);
        setupWeekdays(view, state == null ? args.getInt("weekdays", 31) : state.getInt("weekdays", 31));
        startDateButton.setOnClickListener(v -> chooseStartDate());
        reminderTimeButton.setOnClickListener(v -> chooseReminderTime());
        colorButton.setOnClickListener(v -> chooseColor());
        reminderTimeButton.setEnabled(reminderEnabled.isChecked());
        reminderEnabled.setOnCheckedChangeListener((button, checked) -> reminderTimeButton.setEnabled(checked));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(id == 0 ? "Tạo thói quen" : "Sửa thói quen")
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> saveHabit(id));
        });
        return dialog;
    }

    private void chooseColor() {
        if (getParentFragmentManager().findFragmentByTag("habit_color_picker") == null) {
            HabitColorPickerDialog.create(selectedColor)
                    .show(getParentFragmentManager(), "habit_color_picker");
        }
    }

    private void updateColorButton() {
        if (!selectedColor.matches("#[0-9a-fA-F]{6}")) selectedColor = "#5B6FDE";
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor(selectedColor));
        background.setCornerRadius(dp(8));
        colorButton.setBackground(background);
        colorButton.setTextColor(Color.WHITE);
        colorButton.setText("Chọn màu");
        colorButton.setTag(selectedColor);
        colorButton.setContentDescription("Màu thói quen " + selectedColor + ". Chạm để chọn màu khác.");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setupWeekdays(View view, int selectedMask) {
        LinearLayout first = view.findViewById(R.id.habitWeekdaysFirst);
        LinearLayout second = view.findViewById(R.id.habitWeekdaysSecond);
        for (int i = 0; i < DAYS.length; i++) {
            CheckBox check = new CheckBox(requireContext());
            check.setText(DAYS[i]);
            check.setTag(i);
            check.setChecked((selectedMask & (1 << i)) != 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            (i < 4 ? first : second).addView(check, params);
            weekdays.add(check);
        }
    }

    private void chooseStartDate() {
        String[] parts = startDate.split("-");
        DatePickerDialog picker = new DatePickerDialog(requireContext(), (datePicker, year, month, day) -> {
            startDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
            startDateButton.setText("Ngày bắt đầu · " + startDate);
        }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }

    private void chooseReminderTime() {
        String[] parts = reminderTime.split(":");
        new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
            reminderTime = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
            reminderTimeButton.setText("Giờ nhắc · " + reminderTime);
        }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), true).show();
    }

    private void saveHabit(int id) {
        String cleanedName = name.getText().toString().trim();
        if (cleanedName.isEmpty()) {
            name.setError("Nhập tên thói quen");
            return;
        }
        String cleanedIcon = icon.getText().toString().trim();
        if (cleanedIcon.isEmpty() || cleanedIcon.length() > 32) {
            icon.setError("Nhập biểu tượng ngắn, ví dụ 🌱");
            return;
        }
        int target;
        try {
            target = Integer.parseInt(targetCount.getText().toString().trim());
        } catch (NumberFormatException error) {
            targetCount.setError("Nhập mục tiêu từ 1 đến 10000");
            return;
        }
        String unit = targetUnit.getText().toString().trim();
        if (target < 1 || target > 10000 || unit.isEmpty() || unit.length() > 20) {
            targetCount.setError("Nhập mục tiêu từ 1 đến 10000");
            targetUnit.setError("Nhập đơn vị, tối đa 20 ký tự");
            return;
        }
        int mask = 0;
        for (CheckBox day : weekdays) if (day.isChecked()) mask |= 1 << (int) day.getTag();
        if (mask == 0) {
            weekdays.get(0).setError("Chọn ít nhất một ngày");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", cleanedName);
        body.put("description", description.getText().toString().trim());
        body.put("icon", cleanedIcon);
        body.put("color", selectedColor);
        body.put("weekdays", mask);
        body.put("startDate", startDate);
        body.put("targetCount", target);
        body.put("targetUnit", unit);
        body.put("reminderEnabled", reminderEnabled.isChecked());
        body.put("reminderTime", reminderEnabled.isChecked() ? reminderTime + ":00" : null);
        saveButton.setEnabled(false);
        CloudRepository.Result<CloudModels.Habit> callback = new CloudRepository.Result<>() {
            @Override public void success(CloudModels.Habit habit) {
                if (!isAdded()) return;
                ((MainActivity) requireActivity()).onHabitSaved(habit);
                Toast.makeText(requireContext(), "Đã lưu thói quen lên máy chủ", Toast.LENGTH_SHORT).show();
                dismiss();
            }
            @Override public void error(String message) {
                if (!isAdded()) return;
                saveButton.setEnabled(true);
                Toast.makeText(requireContext(), "Chưa lưu được thói quen: " + message, Toast.LENGTH_LONG).show();
            }
        };
        ((MainActivity) requireActivity()).saveHabitToServer(id == 0 ? null : id, body, callback);
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("name", name.getText().toString());
        out.putString("description", description.getText().toString());
        out.putString("icon", icon.getText().toString());
        out.putString("color", selectedColor);
        out.putString("targetCount", targetCount.getText().toString());
        out.putString("targetUnit", targetUnit.getText().toString());
        out.putString("startDate", startDate);
        out.putString("reminderTime", reminderTime);
        out.putBoolean("reminderEnabled", reminderEnabled.isChecked());
        int mask = 0;
        for (CheckBox day : weekdays) if (day.isChecked()) mask |= 1 << (int) day.getTag();
        out.putInt("weekdays", mask);
    }

}
