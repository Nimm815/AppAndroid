package vn.edu.taskmanager;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import vn.edu.taskmanager.data.LocalStore;
import java.io.OutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReportDialog extends DialogFragment {
    private final Calendar period = Calendar.getInstance();
    private int mode = 1;
    private View view;
    private String reportText = "";
    private String exportText;
    public static ReportDialog create(int mode) {
        ReportDialog dialog = new ReportDialog();
        Bundle args = new Bundle(); args.putInt("mode", mode); dialog.setArguments(args);
        return dialog;
    }
    private final ActivityResultLauncher<String> saveImage = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("image/png"), uri -> {
                if (uri == null) return;
                try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
                    Bitmap image = reportImage(exportText == null ? reportText : exportText);
                    try {
                        if (output == null || !image.compress(Bitmap.CompressFormat.PNG, 100, output))
                            throw new IOException("Không ghi được ảnh");
                    } finally { image.recycle(); }
                    Toast.makeText(requireContext(), "Đã lưu ảnh báo cáo", Toast.LENGTH_SHORT).show();
                } catch (IOException | SecurityException error) {
                    Toast.makeText(requireContext(), "Không lưu được ảnh. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
                }
            });
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        if (getArguments() != null) mode = getArguments().getInt("mode", 1);
        if (state != null) {
            mode = state.getInt("mode", 1); period.setTimeInMillis(state.getLong("period", period.getTimeInMillis()));
            exportText = state.getString("exportText");
        }
        view = getLayoutInflater().inflate(R.layout.dialog_report, null);
        ReferenceUi.bindNavigation(view, this);
        RadioGroup modes = view.findViewById(R.id.reportModes);
        modes.check(mode == 0 ? R.id.reportWeek : mode == 1 ? R.id.reportMonth : R.id.reportYear);
        modes.setOnCheckedChangeListener((group, checked) -> {
            mode = checked == R.id.reportWeek ? 0 : checked == R.id.reportMonth ? 1 : 2; render();
        });
        view.findViewById(R.id.reportPrevious).setOnClickListener(v -> move(-1));
        view.findViewById(R.id.reportNext).setOnClickListener(v -> move(1));
        view.findViewById(R.id.reportToday).setOnClickListener(v -> { period.setTime(new Date()); render(); });
        view.findViewById(R.id.closeReport).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.shareReport).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, reportText);
            startActivity(Intent.createChooser(intent, "Chia sẻ báo cáo"));
        });
        view.findViewById(R.id.saveReportImage).setOnClickListener(v -> {
            exportText = reportText;
            saveImage.launch("bao-cao-thoi-quen-" + LocalStore.today() + ".png");
        });
        render();
        Dialog dialog = new Dialog(requireContext(), R.style.Theme_TaskManager);
        dialog.setContentView(view);
        return dialog;
    }
    private void move(int amount) {
        period.add(mode == 0 ? Calendar.WEEK_OF_YEAR : mode == 1 ? Calendar.MONTH : Calendar.YEAR, amount); render();
    }
    private void render() {
        Calendar start = (Calendar) period.clone();
        if (mode == 0) start = WorkspaceController.monday(period);
        else if (mode == 1) start.set(Calendar.DAY_OF_MONTH, 1);
        else { start.set(Calendar.DAY_OF_YEAR, 1); }
        Calendar end = (Calendar) start.clone();
        end.add(mode == 0 ? Calendar.DAY_OF_MONTH : mode == 1 ? Calendar.MONTH : Calendar.YEAR, mode == 0 ? 7 : 1);
        end.add(Calendar.DAY_OF_MONTH, -1);
        String from = LocalStore.day(start.getTime()), to = LocalStore.day(end.getTime());
        String label = mode == 0 ? from + " – " + to : new SimpleDateFormat(mode == 1 ? "'Tháng' MM yyyy" : "'Năm' yyyy", new Locale("vi", "VN")).format(start.getTime());
        ((TextView) view.findViewById(R.id.reportPeriod)).setText(label);
        LinearLayout rows = view.findViewById(R.id.reportEntries); rows.removeAllViews();
        StringBuilder result = new StringBuilder("BÁO CÁO THÓI QUEN\n").append(label).append("\n\n");
        List<LocalStore.Entry> habits = new LocalStore(requireContext()).entries("habit");
        for (LocalStore.Entry habit : habits) {
            int eligible = 0, completed = 0;
            Calendar date = (Calendar) start.clone();
            while (!date.after(end)) {
                String key = LocalStore.day(date.getTime());
                if (key.compareTo(habit.created) >= 0 && key.compareTo(LocalStore.today()) <= 0) {
                    eligible++; if (habit.days.contains(key)) completed++;
                }
                date.add(Calendar.DAY_OF_MONTH, 1);
            }
            int percent = eligible == 0 ? 0 : (int) Math.round(completed * 100.0 / eligible);
            TextView title = new TextView(requireContext()); title.setText(habit.title); title.setTextSize(20); title.setPadding(12, 24, 12, 8); rows.addView(title);
            TextView detail = new TextView(requireContext());
            String summary = eligible == 0 ? "Chưa có ngày cần ghi nhận trong kỳ" : completed + "/" + eligible + " ngày · " + percent + "%";
            detail.setText(summary); detail.setPadding(12, 8, 12, 8); rows.addView(detail);
            ProgressBar bar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100); bar.setProgress(percent); rows.addView(bar);
            result.append(habit.title).append(": ").append(summary).append('\n');
        }
        if (habits.isEmpty()) {
            rows.setGravity(android.view.Gravity.CENTER);
            rows.setHorizontalGravity(android.view.Gravity.CENTER_HORIZONTAL);
            TextView empty = new TextView(requireContext()); empty.setText("Không có thói quen nào");
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setTextSize(18); empty.setTypeface(null, android.graphics.Typeface.BOLD);
            empty.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.workspace_text));
            rows.addView(empty);
            result.append("Không có thói quen nào\n");
        } else {
            rows.setGravity(android.view.Gravity.TOP);
        }
        result.append("\nChỉ tính từ ngày tạo đến hôm nay. Mỗi thói quen có mục tiêu hằng ngày.");
        reportText = result.toString();
    }
    private Bitmap reportImage(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.rgb(35, 35, 35)); paint.setTextSize(28);
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) { lines.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (paint.measureText(line + " " + word) > 840 && line.length() > 0) { lines.add(line.toString()); line.setLength(0); }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            lines.add(line.toString());
        }
        Bitmap image = Bitmap.createBitmap(920, Math.max(320, 80 + lines.size() * 44), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); canvas.drawColor(Color.WHITE);
        for (int i = 0; i < lines.size(); i++) canvas.drawText(lines.get(i), 40, 54 + i * 44, paint);
        return image;
    }
    @Override public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); out.putInt("mode", mode); out.putLong("period", period.getTimeInMillis()); out.putString("exportText", exportText);
    }
}
