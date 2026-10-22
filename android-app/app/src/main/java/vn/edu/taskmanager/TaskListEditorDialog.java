package vn.edu.taskmanager;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import vn.edu.taskmanager.data.Task;
import vn.edu.taskmanager.data.TaskListWithTasks;

/** Form danh sách giữ tên và lựa chọn công việc khi xoay máy. */
public class TaskListEditorDialog extends DialogFragment {
    private EditText title;
    private TextView error;
    private TaskViewModel model;
    private final Set<Long> members = new HashSet<>();
    private String request = "";
    private boolean deleting, busy;
    private View content;

    public static TaskListEditorDialog create(TaskListWithTasks item) {
        TaskListEditorDialog dialog = new TaskListEditorDialog();
        Bundle args = new Bundle();
        args.putBoolean("creating", item == null);
        args.putString("id", item == null ? UUID.randomUUID().toString() : item.list.id);
        args.putString("title", item == null ? "" : item.list.title);
        ArrayList<String> ids = new ArrayList<>();
        if (item != null) for (Task task : item.tasks) ids.add(String.valueOf(task.id));
        args.putStringArrayList("members", ids);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        model = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        content = getLayoutInflater().inflate(R.layout.dialog_task_list, null);
        title = content.findViewById(R.id.taskListName);
        error = content.findViewById(R.id.taskListError);
        title.setText(state == null ? requireArguments().getString("title") : state.getString("title"));
        ArrayList<String> saved = (state == null ? requireArguments() : state).getStringArrayList("members");
        if (saved != null) for (String id : saved) members.add(Long.parseLong(id));
        if (state != null) {
            request = state.getString("request", ""); deleting = state.getBoolean("deleting");
            // Sau khi process bị hủy, cho phép thử lại nếu không còn yêu cầu đang chạy.
            busy = state.getBoolean("busy") && model.isListRequestActive(request);
        }
        boolean creating = requireArguments().getBoolean("creating");
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(creating ? R.string.create_task_list : R.string.edit_task_list)
                .setView(content).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .setNeutralButton(creating ? R.string.close : R.string.delete, null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(creating ? View.GONE : View.VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = title.getText().toString().trim();
                if (name.isEmpty()) { title.setError(getString(R.string.list_name_required)); return; }
                request = UUID.randomUUID().toString(); deleting = false; setBusy(true);
                model.saveList(request, requireArguments().getString("id"), name, creating, members);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    new AlertDialog.Builder(requireContext()).setTitle(R.string.delete_task_list)
                            .setMessage(R.string.delete_task_list_detail)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.delete, (confirm, which) -> {
                                request = UUID.randomUUID().toString(); deleting = true; setBusy(true);
                                model.deleteList(request, requireArguments().getString("id"));
                            }).show());
            setBusy(busy);
        });
        model.tasks.observe(this, tasks -> {
            LinearLayout rows = content.findViewById(R.id.taskListChoices);
            rows.removeAllViews();
            content.findViewById(R.id.taskListEmpty).setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
            for (Task task : tasks) {
                CheckBox check = new CheckBox(requireContext());
                check.setText(task.title + (task.completed ? " · " + getString(R.string.list_task_done) : ""));
                check.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
                check.setChecked(members.contains(task.id)); check.setEnabled(!busy);
                check.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) members.add(task.id); else members.remove(task.id);
                });
                rows.addView(check);
            }
        });
        model.listResult.observe(this, result -> {
            if (result == null || !result.request.equals(request)) return;
            setBusy(false);
            if (!result.success) { error.setText(R.string.save_error); error.setVisibility(View.VISIBLE); return; }
            MainActivity activity = (MainActivity) requireActivity();
            if (deleting) activity.taskListDeleted(result.listId);
            else activity.openTaskList(result.listId);
            dismiss();
        });
        return dialog;
    }

    private void setBusy(boolean value) {
        busy = value; setCancelable(!value);
        title.setEnabled(!value);
        LinearLayout rows = content.findViewById(R.id.taskListChoices);
        for (int i = 0; i < rows.getChildCount(); i++) rows.getChildAt(i).setEnabled(!value);
        if (getDialog() instanceof AlertDialog) {
            AlertDialog dialog = (AlertDialog) getDialog();
            for (int button : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL})
                if (dialog.getButton(button) != null) dialog.getButton(button).setEnabled(!value);
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("title", title.getText().toString());
        ArrayList<String> ids = new ArrayList<>();
        for (Long id : members) ids.add(String.valueOf(id));
        state.putStringArrayList("members", ids);
        state.putString("request", request); state.putBoolean("busy", busy); state.putBoolean("deleting", deleting);
    }
}
