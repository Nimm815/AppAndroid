package vn.edu.taskmanager;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import vn.edu.taskmanager.data.LocalStore;

/** Trang đọc riêng; chỉ thay đổi dữ liệu khi đánh dấu hoặc xác nhận xóa. */
public class JournalReaderDialog extends DialogFragment {
    public static JournalReaderDialog create(String id) {
        JournalReaderDialog dialog = new JournalReaderDialog();
        Bundle args = new Bundle(); args.putString("id", id); dialog.setArguments(args);
        return dialog;
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        Dialog dialog = new Dialog(requireContext(), R.style.Theme_TaskManager);
        View view = getLayoutInflater().inflate(R.layout.dialog_journal_reader, null);
        dialog.setContentView(view);
        LocalStore store = new LocalStore(requireContext());
        LocalStore.Entry note = store.find(requireArguments().getString("id"));
        view.findViewById(R.id.readerClose).setOnClickListener(v -> dismiss());
        if (note == null) {
            ((TextView) view.findViewById(R.id.readerTitle)).setText("Bài nhật ký không còn tồn tại");
            view.findViewById(R.id.readerEdit).setEnabled(false);
            view.findViewById(R.id.readerDelete).setEnabled(false);
            view.findViewById(R.id.readerBookmark).setEnabled(false);
            return dialog;
        }
        ((TextView) view.findViewById(R.id.readerTitle)).setText(note.title);
        ((TextView) view.findViewById(R.id.readerDate)).setText(MainActivity.displayDate(note.date)
                + (note.label.isEmpty() ? "" : "  ·  " + note.label));
        ((TextView) view.findViewById(R.id.readerBody)).setText(note.body.isEmpty() ? "Chưa có nội dung." : note.body);
        ImageButton bookmark = view.findViewById(R.id.readerBookmark);
        updateBookmark(bookmark, note);
        bookmark.setOnClickListener(v -> {
            note.bookmarked = !note.bookmarked; store.save(note); updateBookmark(bookmark, note);
            ((MainActivity) requireActivity()).refreshWorkspace();
        });
        view.findViewById(R.id.readerEdit).setOnClickListener(v -> {
            MainActivity activity = (MainActivity) requireActivity();
            dismiss();
            if (activity.getSupportFragmentManager().findFragmentByTag("entry_editor") == null)
                EntryEditorDialog.create("note", note.id, note.date).show(activity.getSupportFragmentManager(), "entry_editor");
        });
        view.findViewById(R.id.readerDelete).setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Xóa bài nhật ký?").setMessage("Bạn có muốn xóa “" + note.title + "” không?")
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (confirmation, which) -> {
                    store.delete(note.id); ((MainActivity) requireActivity()).refreshWorkspace(); dismiss();
                }).show());
        return dialog;
    }
    private void updateBookmark(ImageButton button, LocalStore.Entry note) {
        button.setImageTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(),
                        note.bookmarked ? R.color.workspace_accent : R.color.workspace_muted)));
        button.setContentDescription(note.bookmarked ? "Bỏ đánh dấu bài nhật ký" : "Đánh dấu bài nhật ký");
    }
    @Override public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
