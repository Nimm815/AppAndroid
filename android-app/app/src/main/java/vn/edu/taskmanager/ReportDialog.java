package vn.edu.taskmanager;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;

import vn.edu.taskmanager.data.LocalStore;
import vn.edu.taskmanager.report.ReportPdfGenerator;
import vn.edu.taskmanager.view.DailyChartView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Màn hình Báo cáo cá nhân (UC12) theo quy chuẩn HelloHabit.
 * Hỗ trợ kỳ 7 ngày, 30 ngày, tùy chọn; tính 3 chỉ số bắt buộc; vẽ biểu đồ; xuất/chia sẻ file PDF.
 */
public class ReportDialog extends DialogFragment {

    private int periodMode = 0; // 0: 7 ngày, 1: 30 ngày, 2: Tùy chọn
    private String fromDateStr;
    private String toDateStr;
    private String selectedHabitId = ""; // Rỗng là Tất cả
    private String selectedHabitName = "Tất cả thói quen";

    private View rootView;
    private TextView tvPeriodLabel, tvTotalCheckIns, tvActiveDays, tvCompletionRate, tvCompletionDetail;
    private DailyChartView chartView;
    private LinearLayout entriesContainer;
    private Button btnPickCustomDate;
    private Spinner habitFilterSpinner;

    private final List<String> habitFilterIds = new ArrayList<>();
    private final List<String> habitFilterNames = new ArrayList<>();
    private ReportPdfGenerator.ReportData lastReportData;

    public static ReportDialog create(int initialMode) {
        ReportDialog dialog = new ReportDialog();
        Bundle args = new Bundle();
        args.putInt("mode", initialMode);
        dialog.setArguments(args);
        return dialog;
    }

    private final ActivityResultLauncher<String> savePdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                if (uri == null) return;
                try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (output != null && lastReportData != null) {
                        ReportPdfGenerator.generatePdf(lastReportData, output);
                        Toast.makeText(requireContext(), "Đã lưu báo cáo PDF thành công", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Không thể lưu PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        if (getArguments() != null) {
            periodMode = getArguments().getInt("mode", 0);
        }
        if (state != null) {
            periodMode = state.getInt("periodMode", 0);
            fromDateStr = state.getString("fromDateStr");
            toDateStr = state.getString("toDateStr");
            selectedHabitId = state.getString("selectedHabitId", "");
        } else {
            initDatesForMode();
        }

        rootView = getLayoutInflater().inflate(R.layout.dialog_report, null);

        tvPeriodLabel = rootView.findViewById(R.id.reportPeriodLabel);
        tvTotalCheckIns = rootView.findViewById(R.id.reportTotalCheckIns);
        tvActiveDays = rootView.findViewById(R.id.reportActiveDays);
        tvCompletionRate = rootView.findViewById(R.id.reportCompletionRate);
        tvCompletionDetail = rootView.findViewById(R.id.reportCompletionDetail);
        chartView = rootView.findViewById(R.id.reportChartView);
        entriesContainer = rootView.findViewById(R.id.reportEntries);
        btnPickCustomDate = rootView.findViewById(R.id.reportPickCustomDate);
        habitFilterSpinner = rootView.findViewById(R.id.reportHabitFilter);

        rootView.findViewById(R.id.closeReport).setOnClickListener(v -> dismiss());

        RadioGroup radioModes = rootView.findViewById(R.id.reportModes);
        if (periodMode == 0) radioModes.check(R.id.report7Days);
        else if (periodMode == 1) radioModes.check(R.id.report30Days);
        else radioModes.check(R.id.reportCustom);

        radioModes.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.report7Days) periodMode = 0;
            else if (checkedId == R.id.report30Days) periodMode = 1;
            else periodMode = 2;
            initDatesForMode();
            render();
        });

        btnPickCustomDate.setOnClickListener(v -> pickCustomDateRange());

        // Nạp danh sách thói quen vào Spinner
        LocalStore store = new LocalStore(requireContext());
        habitFilterIds.clear();
        habitFilterNames.clear();
        habitFilterIds.add("");
        habitFilterNames.add("Tất cả thói quen");
        for (LocalStore.Entry h : store.entries("habit")) {
            habitFilterIds.add(h.id);
            habitFilterNames.add(h.title);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, habitFilterNames);
        habitFilterSpinner.setAdapter(adapter);

        int pos = habitFilterIds.indexOf(selectedHabitId);
        if (pos >= 0) habitFilterSpinner.setSelection(pos);

        habitFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedHabitId = habitFilterIds.get(position);
                selectedHabitName = habitFilterNames.get(position);
                render();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        rootView.findViewById(R.id.saveReportPdf).setOnClickListener(v -> {
            if (lastReportData != null) {
                String fileName = ReportPdfGenerator.getStandardFileName(fromDateStr, toDateStr);
                savePdfLauncher.launch(fileName);
            }
        });

        rootView.findViewById(R.id.shareReportPdf).setOnClickListener(v -> shareReportPdf());

        render();

        Dialog dialog = new Dialog(requireContext(), R.style.Theme_TaskManager);
        dialog.setContentView(rootView);
        return dialog;
    }

    private void initDatesForMode() {
        Calendar endCal = Calendar.getInstance();
        toDateStr = LocalStore.day(endCal.getTime());

        Calendar startCal = (Calendar) endCal.clone();
        if (periodMode == 0) {
            // 7 ngày gần nhất (tính cả hôm nay)
            startCal.add(Calendar.DAY_OF_MONTH, -6);
            fromDateStr = LocalStore.day(startCal.getTime());
        } else if (periodMode == 1) {
            // 30 ngày gần nhất
            startCal.add(Calendar.DAY_OF_MONTH, -29);
            fromDateStr = LocalStore.day(startCal.getTime());
        } else {
            if (fromDateStr == null || fromDateStr.isEmpty()) {
                startCal.add(Calendar.DAY_OF_MONTH, -6);
                fromDateStr = LocalStore.day(startCal.getTime());
            }
        }
    }

    private void pickCustomDateRange() {
        String[] fromParts = fromDateStr.split("-");
        new DatePickerDialog(requireContext(), (view1, y1, m1, d1) -> {
            String newFrom = String.format(Locale.ROOT, "%04d-%02d-%02d", y1, m1 + 1, d1);
            String[] toParts = toDateStr.split("-");
            new DatePickerDialog(requireContext(), (view2, y2, m2, d2) -> {
                String newTo = String.format(Locale.ROOT, "%04d-%02d-%02d", y2, m2 + 1, d2);
                if (newFrom.compareTo(newTo) > 0) {
                    Toast.makeText(requireContext(), "Ngày bắt đầu phải trước ngày kết thúc", Toast.LENGTH_SHORT).show();
                    return;
                }
                fromDateStr = newFrom;
                toDateStr = newTo;
                render();
            }, Integer.parseInt(toParts[0]), Integer.parseInt(toParts[1]) - 1, Integer.parseInt(toParts[2])).show();
        }, Integer.parseInt(fromParts[0]), Integer.parseInt(fromParts[1]) - 1, Integer.parseInt(fromParts[2])).show();
    }

    private void render() {
        btnPickCustomDate.setVisibility(periodMode == 2 ? View.VISIBLE : View.GONE);
        tvPeriodLabel.setText(MainActivity.displayDate(fromDateStr) + " – " + MainActivity.displayDate(toDateStr));

        LocalStore store = new LocalStore(requireContext());
        List<LocalStore.Entry> habits = store.entries("habit");
        String today = LocalStore.today();

        // 1. Thu thập dữ liệu hoạt động theo ngày
        Map<String, Integer> dateCounts = new HashMap<>();
        List<String> dateRange = new ArrayList<>();

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            Calendar cur = Calendar.getInstance();
            cur.setTime(sdf.parse(fromDateStr));
            Calendar end = Calendar.getInstance();
            end.setTime(sdf.parse(toDateStr));

            while (!cur.after(end)) {
                String d = LocalStore.day(cur.getTime());
                dateRange.add(d);
                dateCounts.put(d, 0);
                cur.add(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception e) {
            dateRange.add(fromDateStr);
            dateCounts.put(fromDateStr, 0);
        }

        int totalCheckIns = 0;
        Set<String> activeDatesSet = new HashSet<>();

        // Danh sách thói quen lọc
        List<LocalStore.Entry> targetHabits = new ArrayList<>();
        for (LocalStore.Entry h : habits) {
            if (selectedHabitId.isEmpty() || selectedHabitId.equals(h.id)) {
                targetHabits.add(h);
            }
        }

        // Tính toán các chỉ số cho từng thói quen
        List<ReportPdfGenerator.ReportData.HabitStat> habitStats = new ArrayList<>();
        int sumEligiblePeriods = 0;
        int sumCompletedPeriods = 0;

        for (LocalStore.Entry habit : targetHabits) {
            int hCheckIns = 0;
            Set<String> hActiveDays = new HashSet<>();
            int hEligible = 0;
            int hCompleted = 0;

            for (String day : dateRange) {
                int prog = habit.progressOn(day);
                if (prog > 0) {
                    hCheckIns++;
                    hActiveDays.add(day);
                    activeDatesSet.add(day);
                    dateCounts.put(day, dateCounts.get(day) + 1);
                    totalCheckIns++;
                }

                // Tính kỳ mục tiêu đã kết thúc:
                // Thói quen hằng ngày: kỳ kết thúc là các ngày trước hôm nay (day < today)
                // và bắt đầu từ ngày tạo (day >= habit.created).
                if (day.compareTo(today) < 0 && (habit.created == null || day.compareTo(habit.created) >= 0)) {
                    hEligible++;
                    if (prog == 100) {
                        hCompleted++;
                    }
                }
            }

            String hRateStr = "-";
            if (hEligible > 0) {
                double rate = (hCompleted * 100.0) / hEligible;
                hRateStr = String.format(Locale.ROOT, "%.1f%%", rate);
            }

            sumEligiblePeriods += hEligible;
            sumCompletedPeriods += hCompleted;

            habitStats.add(new ReportPdfGenerator.ReportData.HabitStat(habit.title, hCheckIns, hActiveDays.size(), hRateStr));
        }

        // 2. Hiển thị 3 chỉ số chính
        tvTotalCheckIns.setText(String.valueOf(totalCheckIns));
        tvActiveDays.setText(String.valueOf(activeDatesSet.size()));

        String completionRateStr = "-";
        if (sumEligiblePeriods > 0) {
            double overallRate = (sumCompletedPeriods * 100.0) / sumEligiblePeriods;
            completionRateStr = String.format(Locale.ROOT, "%.1f%%", overallRate);
            tvCompletionDetail.setText(sumCompletedPeriods + "/" + sumEligiblePeriods + " kỳ đạt (không gồm kỳ đang diễn ra)");
        } else {
            tvCompletionDetail.setText("Không gồm kỳ đang diễn ra");
        }
        tvCompletionRate.setText(completionRateStr);

        // 3. Hiển thị Biểu đồ cột
        List<DailyChartView.BarData> chartData = new ArrayList<>();
        List<ReportPdfGenerator.ReportData.DailyStat> dailyPdfStats = new ArrayList<>();
        for (String day : dateRange) {
            int c = dateCounts.get(day);
            String label = day.length() >= 5 ? day.substring(5) : day; // MM-dd
            chartData.add(new DailyChartView.BarData(label, c));
            dailyPdfStats.add(new ReportPdfGenerator.ReportData.DailyStat(day, c));
        }
        chartView.setData(chartData);

        // 4. Bảng chi tiết từng thói quen trên màn hình
        entriesContainer.removeAllViews();
        if (targetHabits.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("Không có thói quen nào phù hợp bộ lọc.");
            empty.setPadding(16, 24, 16, 24);
            empty.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.workspace_muted));
            entriesContainer.addView(empty);
        } else {
            for (int i = 0; i < targetHabits.size(); i++) {
                LocalStore.Entry habit = targetHabits.get(i);
                ReportPdfGenerator.ReportData.HabitStat hs = habitStats.get(i);

                LinearLayout item = new LinearLayout(requireContext());
                item.setOrientation(LinearLayout.VERTICAL);
                item.setBackgroundResource(R.drawable.task_card);
                item.setPadding(24, 20, 24, 20);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = 16;
                item.setLayoutParams(lp);

                TextView title = new TextView(requireContext());
                title.setText(habit.title);
                title.setTextSize(16);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                title.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.workspace_text));
                item.addView(title);

                TextView sub = new TextView(requireContext());
                sub.setText(hs.checkIns + " lượt ghi nhận  ·  " + hs.activeDays + " ngày có hoạt động  ·  Tỷ lệ: " + hs.rateStr);
                sub.setTextSize(13);
                sub.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.workspace_muted));
                sub.setPadding(0, 6, 0, 10);
                item.addView(sub);

                int progressVal = 0;
                if (!"-".equals(hs.rateStr)) {
                    try {
                        progressVal = (int) Math.round(Double.parseDouble(hs.rateStr.replace("%", "")));
                    } catch (Exception ignored) {}
                }
                ProgressBar pb = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
                pb.setMax(100);
                pb.setProgress(progressVal);
                item.addView(pb);

                entriesContainer.addView(item);
            }
        }

        // Lưu dữ liệu để xuất PDF
        lastReportData = new ReportPdfGenerator.ReportData();
        lastReportData.fromDate = fromDateStr;
        lastReportData.toDate = toDateStr;
        lastReportData.habitFilterName = selectedHabitName;
        lastReportData.totalCheckIns = totalCheckIns;
        lastReportData.activeDays = activeDatesSet.size();
        lastReportData.completionRateStr = completionRateStr;
        lastReportData.completedPeriods = sumCompletedPeriods;
        lastReportData.eligiblePeriods = sumEligiblePeriods;
        lastReportData.dailyStats = dailyPdfStats;
        lastReportData.habitStats = habitStats;
    }

    private void shareReportPdf() {
        if (lastReportData == null) return;
        try {
            File cacheDir = new File(requireContext().getCacheDir(), "reports");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            String fileName = ReportPdfGenerator.getStandardFileName(fromDateStr, toDateStr);
            File pdfFile = new File(cacheDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                ReportPdfGenerator.generatePdf(lastReportData, fos);
            }

            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", pdfFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Báo cáo thói quen: " + fromDateStr + " đến " + toDateStr);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Chia sẻ báo cáo PDF"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Lỗi khi chia sẻ PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("periodMode", periodMode);
        out.putString("fromDateStr", fromDateStr);
        out.putString("toDateStr", toDateStr);
        out.putString("selectedHabitId", selectedHabitId);
    }
}
