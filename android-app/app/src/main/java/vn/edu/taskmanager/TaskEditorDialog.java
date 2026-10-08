package vn.edu.taskmanager;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

// DialogFragment giữ hộp thoại và nội dung đang nhập khi xoay điện thoại.
public class TaskEditorDialog extends DialogFragment {
    private String scheduledDate;
    private EditText progressInput, noteInput;
    private android.widget.Spinner priorityInput;
    public static TaskEditorDialog newInstance(long id, String title, String date) {
        return newInstance(id, title, date, 2);
    }
    public static TaskEditorDialog newInstance(long id, String title, String date, int priority) {
        TaskEditorDialog dialog = new TaskEditorDialog();
        Bundle args = new Bundle();
        args.putLong("id", id);
        args.putString("title", title);
        args.putString("date", date);
        args.putInt("priority", priority);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        long id = requireArguments().getLong("id");
        View view = requireActivity().getLayoutInflater().inflate(R.layout.dialog_task, null);
        EditText input = view.findViewById(R.id.taskNameInput);
        progressInput = view.findViewById(R.id.taskProgressInput);
        noteInput = view.findViewById(R.id.taskNoteInput);
        vn.edu.taskmanager.data.Task current = null;
        java.util.List<vn.edu.taskmanager.data.Task> tasks = new ViewModelProvider(requireActivity()).get(TaskViewModel.class).tasks.getValue();
        if (tasks != null) for (vn.edu.taskmanager.data.Task task : tasks) if (task.id == id) current = task;
        progressInput.setText(state != null ? state.getString("progress", "0") : String.valueOf(current == null ? 0 : current.completed ? 100 : current.progress));
        noteInput.setText(state != null ? state.getString("note", "") : current == null ? "" : current.note);
        priorityInput = view.findViewById(R.id.taskPriority);
        android.widget.ArrayAdapter<String> choices = new android.widget.ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, TaskPriority.LABELS) {
            @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                android.widget.TextView text = (android.widget.TextView) super.getView(position, recycled, parent);
                decorate(text, position); return text;
            }
            @Override public View getDropDownView(int position, View recycled, android.view.ViewGroup parent) {
                android.widget.TextView text = (android.widget.TextView) super.getDropDownView(position, recycled, parent);
                decorate(text, position); return text;
            }
            private void decorate(android.widget.TextView text, int position) {
                android.view.ViewGroup.LayoutParams params = text.getLayoutParams();
                params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                text.setLayoutParams(params);
                text.setSingleLine(false);
                text.setMaxLines(3);
                text.setEllipsize(null);
                text.setText(TaskPriority.LABELS[position] + "\n" + TaskPriority.DETAILS[position]);
                text.setTextSize(14);
                text.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.workspace_text));
                int size = (int) (12 * getResources().getDisplayMetrics().density);
                text.setMinHeight((int) (72 * getResources().getDisplayMetrics().density));
                android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
                dot.setShape(android.graphics.drawable.GradientDrawable.OVAL); dot.setColor(TaskPriority.COLORS[position]);
                dot.setBounds(0, 0, size, size); text.setCompoundDrawables(dot, null, null, null);
                text.setCompoundDrawablePadding(size); text.setPadding(size, size, size, size);
            }
        };
        priorityInput.setAdapter(choices);
        priorityInput.setSelection(TaskPriority.index(state == null ? requireArguments().getInt("priority", 2) : state.getInt("priority", 2)));
        input.setText(state == null ? requireArguments().getString("title") : state.getString("draft"));
        input.setSelection(input.length());
        scheduledDate = state == null ? requireArguments().getString("date", "") : state.getString("date", "");
        if (id == 0 && !scheduledDate.isEmpty()
                && scheduledDate.compareTo(vn.edu.taskmanager.data.LocalStore.today()) < 0) {
            scheduledDate = vn.edu.taskmanager.data.LocalStore.today();
        }
        android.widget.Button dateButton = view.findViewById(R.id.taskDate);
        dateButton.setText(MainActivity.displayDate(scheduledDate));
        dateButton.setOnClickListener(v -> {
            java.util.Calendar date = java.util.Calendar.getInstance();
            if (!scheduledDate.isEmpty() && scheduledDate.compareTo(vn.edu.taskmanager.data.LocalStore.today()) >= 0) {
                String[] parts = scheduledDate.split("-");
                date.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            }
            android.app.DatePickerDialog pickerDialog = new android.app.DatePickerDialog(requireContext(), (picker, year, month, day) -> {
                scheduledDate = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
                dateButton.setError(null);
                dateButton.setText(MainActivity.displayDate(scheduledDate));
            }, date.get(java.util.Calendar.YEAR), date.get(java.util.Calendar.MONTH), date.get(java.util.Calendar.DAY_OF_MONTH));
            java.util.Calendar today = java.util.Calendar.getInstance();
            today.set(java.util.Calendar.HOUR_OF_DAY, 0);
            today.set(java.util.Calendar.MINUTE, 0);
            today.set(java.util.Calendar.SECOND, 0);
            today.set(java.util.Calendar.MILLISECOND, 0);
            pickerDialog.getDatePicker().setMinDate(today.getTimeInMillis());
            pickerDialog.show();
        });
        view.findViewById(R.id.clearTaskDate).setOnClickListener(v -> {
            dateButton.setError(null);
            scheduledDate = ""; dateButton.setText(MainActivity.displayDate(scheduledDate));
        });
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(id == 0 ? R.string.add_task : R.string.edit_task)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) {
                        input.setError(getString(R.string.name_required));
                        input.requestFocus();
                        return;
                    }
                    String originalDate = id == 0 ? "" : requireArguments().getString("date", "");
                    if (!TaskSchedule.canSaveDate(scheduledDate, originalDate,
                            vn.edu.taskmanager.data.LocalStore.today())) {
                        dateButton.setError("Chọn ngày từ hôm nay trở đi");
                        android.widget.Toast.makeText(requireContext(), "Ngày thực hiện không được ở quá khứ", android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    int progress;
                    try { progress = Integer.parseInt(progressInput.getText().toString().trim()); }
                    catch (NumberFormatException error) { progressInput.setError("Nhập số từ 0 đến 100"); return; }
                    if (progress < 0 || progress > 100) { progressInput.setError("Nhập số từ 0 đến 100"); return; }
                    new ViewModelProvider(requireActivity()).get(TaskViewModel.class).save(id, title, scheduledDate,
                            priorityInput.getSelectedItemPosition() + 1, progress, noteInput.getText().toString().trim());
                    ((MainActivity) requireActivity()).taskSaved(scheduledDate);
                    dismiss();
                }));
        return dialog;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("progress", progressInput.getText().toString());
        out.putString("note", noteInput.getText().toString());
        out.putString("date", scheduledDate);
        out.putInt("priority", priorityInput.getSelectedItemPosition() + 1);
        if (getDialog() != null) {
            EditText input = getDialog().findViewById(R.id.taskNameInput);
            if (input != null) out.putString("draft", input.getText().toString());
        }
    }
}
