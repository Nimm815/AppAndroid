package vn.edu.taskmanager;

import android.app.Dialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import vn.edu.taskmanager.data.LocalStore;
import java.util.Locale;

public class EntryEditorDialog extends DialogFragment {
    private EditText title, body;
    private String date, time;
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
        title.setText(state != null ? state.getString("title") : old == null ? "" : old.title);
        body.setText(state != null ? state.getString("body") : old == null ? "" : old.body);
        date = state != null ? state.getString("date") : old == null ? requireArguments().getString("date") : old.date;
        time = state != null ? state.getString("time") : old == null ? requireArguments().getString("time", "09:00") : old.time;
        Button dateButton = view.findViewById(R.id.entryDate);
        Button timeButton = view.findViewById(R.id.entryTime);
        dateButton.setText(date); timeButton.setText(time);
        boolean event = "event".equals(type);
        dateButton.setVisibility(event ? View.VISIBLE : View.GONE);
        timeButton.setVisibility(event ? View.VISIBLE : View.GONE);
        dateButton.setOnClickListener(v -> {
            String[] parts = date.split("-");
            new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
                date = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
                dateButton.setText(date);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2])).show();
        });
        timeButton.setOnClickListener(v -> {
            String[] parts = time.split(":");
            new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
                time = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
                timeButton.setText(time);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), true).show();
        });
        String heading = "habit".equals(type) ? "Thói quen hằng ngày" : event ? "Sự kiện lịch" : "Ghi chú nhật ký";
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle(heading).setView(view)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = title.getText().toString().trim();
            if (name.isEmpty()) { title.setError(getString(R.string.name_required)); return; }
            LocalStore.Entry entry = old == null ? new LocalStore.Entry() : old;
            entry.type = type; entry.title = name; entry.body = body.getText().toString().trim();
            entry.date = date; entry.time = time;
            store.save(entry);
            ((MainActivity) requireActivity()).entrySaved(entry);
            dismiss();
        }));
        return dialog;
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("title", title.getText().toString()); out.putString("body", body.getText().toString());
        out.putString("date", date); out.putString("time", time);
    }
}
