package vn.edu.taskmanager;

import android.app.Dialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import vn.edu.taskmanager.data.LocalStore;
import java.util.Locale;

public class EntryEditorDialog extends DialogFragment {
    private EditText title, body;
    private EditText label;
    private String date, time;
    private android.widget.Spinner activityChoice;
    private EditText completion;
    private final java.util.List<String> sourceIds = new java.util.ArrayList<>();
    private final java.util.List<String> sourceTypes = new java.util.ArrayList<>();
    public static EntryEditorDialog create(String type, String id, String date) {
        EntryEditorDialog dialog = new EntryEditorDialog();
        Bundle args = new Bundle();
        args.putString("type", type); args.putString("id", id); args.putString("date", date);
        dialog.setArguments(args);
        return dialog;
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        LocalStore store = new LocalStore(requireContext());
        String type = requireArguments().getString("type");
        String id = requireArguments().getString("id");
        LocalStore.Entry old = id == null ? null : store.find(id);
        View view = getLayoutInflater().inflate(R.layout.dialog_entry, null);
        title = view.findViewById(R.id.entryTitle); body = view.findViewById(R.id.entryBody);
        if ("note".equals(type)) {
            title.setHint("Công việc / thói quen đã thực hiện");
            body.setHint("Ghi chú kết quả, những việc đã làm…");
            android.widget.LinearLayout form = (android.widget.LinearLayout) ((android.widget.ScrollView) view).getChildAt(0);
            activityChoice = new android.widget.Spinner(requireContext());
            java.util.List<String> names = new java.util.ArrayList<>();
            names.add("Chọn công việc / thói quen (hoặc nhập tên)"); sourceIds.add(""); sourceTypes.add("");
            java.util.List<vn.edu.taskmanager.data.Task> tasks = new androidx.lifecycle.ViewModelProvider(requireActivity())
                    .get(TaskViewModel.class).tasks.getValue();
            if (tasks != null) for (vn.edu.taskmanager.data.Task task : tasks) {
                names.add("Công việc · " + task.title); sourceIds.add(String.valueOf(task.id)); sourceTypes.add("task");
            }
            for (LocalStore.Entry habit : store.entries("habit")) {
                names.add("Thói quen · " + habit.title); sourceIds.add(habit.id); sourceTypes.add("habit");
            }
            if (old != null && !old.sourceId.isEmpty() && !sourceIds.contains(old.sourceId)) {
                names.add("Hoạt động đã lưu · " + old.title); sourceIds.add(old.sourceId); sourceTypes.add(old.sourceType);
            }
            activityChoice.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, names));
            form.addView(activityChoice, 0);
            int choice = state != null ? state.getInt("activityChoice", 0) : old == null ? 0 : sourceIds.indexOf(old.sourceId);
            activityChoice.setSelection(Math.max(0, choice));
            completion = new EditText(requireContext()); completion.setHint("Mức hoàn thành (%) · 0 đến 100");
            completion.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            completion.setText(state != null ? state.getString("progress", "") : old == null ? "0" : old.progress < 0 ? "" : String.valueOf(old.progress));
            form.addView(completion, 3);
            labelHint(view);
        }
        label = view.findViewById(R.id.entryLabel);
        label.setText(state != null ? state.getString("label", "") : old == null ? "" : old.label);
        label.setVisibility("note".equals(type) ? View.VISIBLE : View.GONE);
        title.setText(state != null ? state.getString("title") : old == null ? "" : old.title);
        body.setText(state != null ? state.getString("body") : old == null ? "" : old.body);
        date = state != null ? state.getString("date") : old == null ? requireArguments().getString("date") : old.date;
        String defaultTime = "note".equals(type)
                ? new java.text.SimpleDateFormat("HH:mm", Locale.ROOT).format(new java.util.Date()) : "09:00";
        time = state != null ? state.getString("time") : old == null ? requireArguments().getString("time", defaultTime) : old.time;
        Button dateButton = view.findViewById(R.id.entryDate);
        Button timeButton = view.findViewById(R.id.entryTime);
        dateButton.setText(date); timeButton.setText(time);
        boolean event = "event".equals(type);
        dateButton.setVisibility(event || "note".equals(type) ? View.VISIBLE : View.GONE);
        timeButton.setVisibility(event || "note".equals(type) ? View.VISIBLE : View.GONE);
        dateButton.setOnClickListener(v -> {
            String[] parts = date.split("-");
            DatePickerDialog picker = new DatePickerDialog(requireContext(), (view1, year, month, day) -> {
                date = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
                dateButton.setText(date);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            if ("note".equals(type)) {
                picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            }
            picker.show();
        });
        timeButton.setOnClickListener(v -> {
            String[] parts = time.split(":");
            new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
                time = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
                timeButton.setText(time);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), true).show();
        });
        String heading = "habit".equals(type) ? "Thói quen hằng ngày" : event ? "Sự kiện lịch" : old == null ? "Ghi chú hoạt động" : "Sửa ghi nhận hoạt động";
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireContext())
                .setTitle(heading)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);

        if (old != null && "note".equals(type)) {
            dialogBuilder.setNeutralButton(R.string.delete, (d, w) -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Xóa ghi chú?")
                        .setMessage("Bạn có muốn xóa ghi chú này không?")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (cd, cw) -> {
                            store.delete(old.id);
                            ((MainActivity) requireActivity()).refreshWorkspace();
                        })
                        .show();
            });
        }

        AlertDialog dialog = dialogBuilder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = title.getText().toString().trim();
            String content = body.getText().toString().trim();

            if ("note".equals(type)) {
                // Kiểm tra thời điểm ghi chú: không cho phép thời điểm tương lai (UC09)
                String today = LocalStore.today();
                if (date.compareTo(today) > 0) {
                    Toast.makeText(requireContext(), "Không thể chọn ngày trong tương lai cho ghi chú", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (date.equals(today)) {
                    String nowTime = new java.text.SimpleDateFormat("HH:mm", Locale.ROOT).format(new java.util.Date());
                    if (time.compareTo(nowTime) > 0) {
                        Toast.makeText(requireContext(), "Không thể chọn giờ trong tương lai cho ghi chú", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                // Kiểm tra độ dài nội dung 1-2.000 ký tự (UC09)
                if (name.isEmpty() && content.isEmpty()) {
                    title.setError("Vui lòng nhập nội dung ghi chú");
                    return;
                }
                if (content.length() > 2000) {
                    body.setError("Nội dung không vượt quá 2.000 ký tự");
                    return;
                }
            }

            int progress = old == null ? -1 : old.progress;
            int choice = activityChoice == null ? 0 : activityChoice.getSelectedItemPosition();
            if (choice > 0 && name.isEmpty()) name = activityChoice.getSelectedItem().toString().split(" · ", 2)[1];
            if (completion != null && !completion.getText().toString().trim().isEmpty()) {
                try { progress = Integer.parseInt(completion.getText().toString().trim()); }
                catch (NumberFormatException error) { completion.setError("Nhập số từ 0 đến 100"); return; }
                if (progress < 0 || progress > 100) { completion.setError("Nhập số từ 0 đến 100"); return; }
            }
            if (name.isEmpty()) {
                if (!content.isEmpty()) {
                    name = content.length() > 30 ? content.substring(0, 30) + "..." : content;
                } else {
                    title.setError("Vui lòng nhập tiêu đề");
                    return;
                }
            }
            LocalStore.Entry entry = old == null ? new LocalStore.Entry() : old;
            entry.type = type; entry.title = name; entry.body = content;
            entry.label = label.getText().toString().trim();
            if (activityChoice != null) {
                entry.sourceId = sourceIds.get(choice); entry.sourceType = sourceTypes.get(choice); entry.progress = progress;
                if (choice > 0) entry.label = "habit".equals(entry.sourceType) ? "Thói quen" : "Công việc";
            }
            entry.date = date; entry.time = time;
            store.save(entry);
            ((MainActivity) requireActivity()).entrySaved(entry);
            dismiss();
        }));
        return dialog;
    }
    private void labelHint(View view) {
        ((EditText) view.findViewById(R.id.entryLabel)).setHint("Loại hoạt động · ví dụ: Công việc, Thói quen");
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("title", title.getText().toString()); out.putString("body", body.getText().toString());
        out.putString("date", date); out.putString("time", time);
        out.putString("label", label.getText().toString());
        if (completion != null) { out.putString("progress", completion.getText().toString()); out.putInt("activityChoice", activityChoice.getSelectedItemPosition()); }
    }
}
