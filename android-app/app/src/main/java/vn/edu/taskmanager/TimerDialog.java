package vn.edu.taskmanager;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.taskmanager.data.LocalStore;
import java.util.ArrayList;
import java.util.List;

public class TimerDialog extends DialogFragment {
    private TimerState model;
    private View view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (view == null) return;
            if (model.running && model.countdown && model.display() == 0) {
                model.pause();
                Toast.makeText(requireContext(), "Đã hết thời gian tập trung", Toast.LENGTH_LONG).show();
                render();
            }
            TextView display = view.findViewById(R.id.timerDisplay);
            String value = TimerState.format(model.display());
            if (!value.contentEquals(display.getText())) display.setText(value);
            handler.postDelayed(this, 250);
        }
    };
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        model = new ViewModelProvider(requireActivity()).get(TimerState.class);
        view = getLayoutInflater().inflate(R.layout.dialog_timer, null);
        ReferenceUi.bindNavigation(view, this);
        NumberPicker hours = view.findViewById(R.id.timerHours), minutes = view.findViewById(R.id.timerMinutes), seconds = view.findViewById(R.id.timerSeconds);
        hours.setMinValue(0); hours.setMaxValue(23);
        minutes.setMinValue(0); minutes.setMaxValue(59); seconds.setMinValue(0); seconds.setMaxValue(59);
        hours.setValue((int) (model.duration / 3600000)); minutes.setValue((int) (model.duration / 60000 % 60)); seconds.setValue((int) (model.duration / 1000 % 60));
        NumberPicker.OnValueChangeListener change = (picker, oldValue, newValue) -> {
            model.duration = (hours.getValue() * 3600L + minutes.getValue() * 60L + seconds.getValue()) * 1000;
            model.reset(); render();
        };
        hours.setOnValueChangedListener(change); minutes.setOnValueChangedListener(change); seconds.setOnValueChangedListener(change);
        RadioGroup modes = view.findViewById(R.id.timerModes);
        modes.check(model.countdown ? R.id.countdownMode : R.id.stopwatchMode);
        modes.setOnCheckedChangeListener((group, checked) -> {
            model.reset(); model.countdown = checked == R.id.countdownMode; model.persist(); render();
        });
        Spinner focus = view.findViewById(R.id.focusHabit);
        List<LocalStore.Entry> habits = new LocalStore(requireContext()).entries("habit");
        List<String> names = new ArrayList<>(); names.add("Không có Thói quen");
        int selected = 0;
        for (LocalStore.Entry habit : habits) {
            names.add(habit.title); if (habit.id.equals(model.habitId)) selected = names.size() - 1;
        }
        ArrayAdapter<String> choices = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, names) {
            @NonNull @Override public View getView(int position, View recycled, @NonNull ViewGroup parent) {
                TextView label = (TextView) super.getView(position, recycled, parent);
                label.setTextSize(16); label.setGravity(android.view.Gravity.CENTER);
                label.setPadding(0, 0, 0, 0);
                return label;
            }
        };
        focus.setAdapter(choices);
        focus.setSelection(selected);
        focus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View item, int position, long id) {
                model.habitId = position == 0 ? "" : habits.get(position - 1).id; model.persist();
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        view.findViewById(R.id.timerStart).setOnClickListener(v -> {
            if (model.running) model.pause();
            else if (model.countdown && model.duration == 0) Toast.makeText(requireContext(), "Chọn thời gian lớn hơn 0", Toast.LENGTH_SHORT).show();
            else { if (model.countdown && model.display() == 0) model.reset(); model.start(); }
            render();
        });
        view.findViewById(R.id.timerReset).setOnClickListener(v -> {
            if (!model.countdown && model.running) model.laps.add(model.elapsedNow()); else model.reset();
            render();
        });
        view.findViewById(R.id.timerLap).setOnClickListener(v -> {
            if (model.running) { model.laps.add(model.elapsedNow()); render(); }
        });
        view.findViewById(R.id.closeTimer).setOnClickListener(v -> dismiss());
        render();
        Dialog dialog = new Dialog(requireContext(), R.style.Theme_TaskManager);
        dialog.setContentView(view);
        return dialog;
    }
    private void render() {
        ((TextView) view.findViewById(R.id.timerDisplay)).setText(TimerState.format(model.display()));
        ((Button) view.findViewById(R.id.timerStart)).setText(model.running ? R.string.pause : R.string.start);
        ((Button) view.findViewById(R.id.timerReset)).setText(!model.countdown && model.running ? "Vòng" : model.countdown ? "Dừng" : "Đặt lại");
        view.findViewById(R.id.countdownInputs).setVisibility(model.countdown ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.countdownLabels).setVisibility(model.countdown ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.timerLap).setVisibility(View.GONE);
        view.findViewById(R.id.timerDisplay).setVisibility(model.countdown && !model.running && model.elapsed == 0 ? View.GONE : View.VISIBLE);
        for (int id : new int[]{R.id.timerHours, R.id.timerMinutes, R.id.timerSeconds, R.id.stopwatchMode, R.id.countdownMode, R.id.focusHabit})
            view.findViewById(id).setEnabled(!model.running);
        StringBuilder laps = new StringBuilder();
        for (int i = Math.max(0, model.laps.size() - 3); i < model.laps.size(); i++)
            laps.append("Vòng ").append(i + 1).append(": ").append(TimerState.format(model.laps.get(i))).append('\n');
        ((TextView) view.findViewById(R.id.timerLaps)).setText(laps);
    }
    @Override public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    @Override public void onResume() { super.onResume(); handler.post(tick); }
    @Override public void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override public void onDestroyView() { handler.removeCallbacks(tick); view = null; super.onDestroyView(); }
}
