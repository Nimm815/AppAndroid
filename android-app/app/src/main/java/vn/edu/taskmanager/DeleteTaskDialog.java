package vn.edu.taskmanager;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

public class DeleteTaskDialog extends DialogFragment {
    public static DeleteTaskDialog newInstance(long id, String title) {
        DeleteTaskDialog dialog = new DeleteTaskDialog();
        Bundle args = new Bundle();
        args.putLong("id", id);
        args.putString("title", title);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_task)
                .setMessage(getString(R.string.delete_confirmation, requireArguments().getString("title")))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        new ViewModelProvider(requireActivity()).get(TaskViewModel.class)
                                .delete(requireArguments().getLong("id")))
                .create();
    }
}
