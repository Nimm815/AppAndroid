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
    public static TaskEditorDialog newInstance(long id, String title) {
        TaskEditorDialog dialog = new TaskEditorDialog();
        Bundle args = new Bundle();
        args.putLong("id", id);
        args.putString("title", title);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        long id = requireArguments().getLong("id");
        View view = requireActivity().getLayoutInflater().inflate(R.layout.dialog_task, null);
        EditText input = view.findViewById(R.id.taskNameInput);
        input.setText(state == null ? requireArguments().getString("title") : state.getString("draft"));
        input.setSelection(input.length());
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
                    new ViewModelProvider(requireActivity()).get(TaskViewModel.class).save(id, title);
                    if (id == 0) ((MainActivity) requireActivity()).taskCreated();
                    dismiss();
                }));
        return dialog;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (getDialog() != null) {
            EditText input = getDialog().findViewById(R.id.taskNameInput);
            if (input != null) out.putString("draft", input.getText().toString());
        }
    }
}
