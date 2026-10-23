package vn.edu.taskmanager.report;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Xuất tài liệu PDF chuẩn theo yêu cầu nghiệm thu của UC12.
 * Tên tệp xuất: Bao_cao_YYYYMMDD_YYYYMMDD.pdf
 */
public class ReportPdfGenerator {

    public static class ReportData {
        public String fromDate; // yyyy-MM-dd
        public String toDate;   // yyyy-MM-dd
        public String habitFilterName; // "Tất cả thói quen" hoặc tên cụ thể
        public int totalCheckIns;
        public int activeDays;
        public String completionRateStr; // ví dụ "87.5%" hoặc "-"
        public int completedPeriods;
        public int eligiblePeriods;
        public List<DailyStat> dailyStats;
        public List<HabitStat> habitStats;

        public static class DailyStat {
            public String date;
            public int count;
            public DailyStat(String date, int count) {
                this.date = date;
                this.count = count;
            }
        }

        public static class HabitStat {
            public String name;
            public int checkIns;
            public int activeDays;
            public String rateStr;
            public HabitStat(String name, int checkIns, int activeDays, String rateStr) {
                this.name = name;
                this.checkIns = checkIns;
                this.activeDays = activeDays;
                this.rateStr = rateStr;
            }
        }
    }

    public static String getStandardFileName(String fromDate, String toDate) {
        String fromClean = fromDate.replace("-", "");
        String toClean = toDate.replace("-", "");
        return "Bao_cao_" + fromClean + "_" + toClean + ".pdf";
    }

    public static void generatePdf(ReportData data, OutputStream outputStream) throws IOException {
        PdfDocument document = new PdfDocument();
        int pageWidth = 595; // A4 chuẩn ở 72dpi
        int pageHeight = 842;
        int pageNumber = 1;

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint boldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boldPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        int margin = 40;
        int y = margin + 20;

        // Tiêu đề
        boldPaint.setTextSize(18);
        boldPaint.setColor(Color.rgb(30, 30, 30));
        canvas.drawText("BÁO CÁO CÁ NHÂN - QUẢN LÝ THÓI QUEN", margin, y, boldPaint);

        y += 24;
        paint.setTextSize(11);
        paint.setColor(Color.rgb(100, 100, 100));
        String subHeader = "Khoảng thời gian: " + data.fromDate + " đến " + data.toDate +
                "  |  Bộ lọc: " + data.habitFilterName;
        canvas.drawText(subHeader, margin, y, paint);

        y += 16;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss (z)", Locale.ROOT);
        String meta = "Thời điểm chốt: " + sdf.format(new Date());
        canvas.drawText(meta, margin, y, paint);

        // Đường kẻ phân cách
        y += 16;
        paint.setColor(Color.rgb(220, 220, 220));
        canvas.drawLine(margin, y, pageWidth - margin, y, paint);

        // 3 Chỉ số chính (3 thẻ)
        y += 25;
        int cardWidth = (pageWidth - margin * 2 - 20) / 3;
        int cardHeight = 65;

        drawKpiCard(canvas, margin, y, cardWidth, cardHeight, "LƯỢT GHI NHẬN", String.valueOf(data.totalCheckIns), "");
        drawKpiCard(canvas, margin + cardWidth + 10, y, cardWidth, cardHeight, "NGÀY CÓ HOẠT ĐỘNG", String.valueOf(data.activeDays), "");
        String rateDetail = data.eligiblePeriods > 0 ? "(" + data.completedPeriods + "/" + data.eligiblePeriods + " kỳ)" : "";
        drawKpiCard(canvas, margin + (cardWidth + 10) * 2, y, cardWidth, cardHeight, "TỶ LỆ HOÀN THÀNH", data.completionRateStr, rateDetail);

        y += cardHeight + 30;

        // Biểu đồ lượt ghi nhận theo ngày
        boldPaint.setTextSize(13);
        boldPaint.setColor(Color.rgb(30, 30, 30));
        canvas.drawText("BIỂU ĐỒ HOẠT ĐỘNG THEO NGÀY", margin, y, boldPaint);

        y += 12;
        int chartH = 90;
        drawChart(canvas, margin, y, pageWidth - margin * 2, chartH, data.dailyStats);
        y += chartH + 30;

        // Bảng thống kê chi tiết theo thói quen
        boldPaint.setTextSize(13);
        boldPaint.setColor(Color.rgb(30, 30, 30));
        canvas.drawText("BẢNG THEO DÕI TỪNG THÓI QUEN", margin, y, boldPaint);

        y += 16;
        // Header bảng
        paint.setColor(Color.rgb(240, 240, 240));
        canvas.drawRect(margin, y, pageWidth - margin, y + 24, paint);

        boldPaint.setTextSize(10);
        boldPaint.setColor(Color.rgb(60, 60, 60));
        canvas.drawText("Tên thói quen", margin + 8, y + 16, boldPaint);
        canvas.drawText("Số lượt", margin + 250, y + 16, boldPaint);
        canvas.drawText("Số ngày", margin + 340, y + 16, boldPaint);
        canvas.drawText("Tỷ lệ", margin + 430, y + 16, boldPaint);

        y += 24;
        paint.setTextSize(10);

        if (data.habitStats == null || data.habitStats.isEmpty()) {
            paint.setColor(Color.rgb(120, 120, 120));
            canvas.drawText("Không có dữ liệu thói quen trong kỳ", margin + 8, y + 20, paint);
            y += 30;
        } else {
            for (ReportData.HabitStat hs : data.habitStats) {
                // Kiểm tra tràn trang
                if (y + 24 > pageHeight - margin - 40) {
                    document.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin + 20;
                }

                paint.setColor(Color.rgb(245, 245, 245));
                canvas.drawLine(margin, y + 22, pageWidth - margin, y + 22, paint);

                paint.setColor(Color.rgb(40, 40, 40));
                canvas.drawText(hs.name, margin + 8, y + 15, paint);
                canvas.drawText(String.valueOf(hs.checkIns), margin + 250, y + 15, paint);
                canvas.drawText(String.valueOf(hs.activeDays), margin + 340, y + 15, paint);
                canvas.drawText(hs.rateStr, margin + 430, y + 15, paint);
                y += 24;
            }
        }

        // Chú thích cuối báo cáo
        y += 20;
        paint.setColor(Color.rgb(130, 130, 130));
        paint.setTextSize(9);
        canvas.drawText("* Ghi chú: Tỷ lệ hoàn thành tính theo các kỳ mục tiêu đã kết thúc. Không gồm kỳ đang diễn ra.", margin, y, paint);

        document.finishPage(page);

        document.writeTo(outputStream);
        document.close();
    }

    private static void drawKpiCard(Canvas canvas, int x, int y, int w, int h, String title, String value, String sub) {
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.rgb(245, 247, 250));
        canvas.drawRoundRect(x, y, x + w, y + h, 8, 8, bgPaint);

        Paint tPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tPaint.setTextSize(9);
        tPaint.setColor(Color.rgb(100, 110, 120));
        canvas.drawText(title, x + 12, y + 18, tPaint);

        Paint vPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        vPaint.setTextSize(20);
        vPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        vPaint.setColor(Color.rgb(38, 117, 232));
        canvas.drawText(value, x + 12, y + 44, vPaint);

        if (!sub.isEmpty()) {
            Paint sPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            sPaint.setTextSize(9);
            sPaint.setColor(Color.rgb(120, 120, 120));
            canvas.drawText(sub, x + 12, y + 58, sPaint);
        }
    }

    private static void drawChart(Canvas canvas, int x, int y, int w, int h, List<ReportData.DailyStat> stats) {
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.rgb(220, 220, 220));
        float baseline = y + h - 16;
        canvas.drawLine(x, baseline, x + w, baseline, linePaint);

        if (stats == null || stats.isEmpty()) return;

        int max = 1;
        for (ReportData.DailyStat s : stats) {
            if (s.count > max) max = s.count;
        }

        int count = stats.size();
        float slotW = (float) w / count;
        float barW = Math.min(slotW * 0.6f, 16);

        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.rgb(38, 117, 232));

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(8);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.rgb(120, 120, 120));

        for (int i = 0; i < count; i++) {
            ReportData.DailyStat s = stats.get(i);
            float cx = x + i * slotW + slotW / 2f;
            float barH = (s.count / (float) max) * (h - 26);
            if (s.count > 0 && barH < 3) barH = 3;

            canvas.drawRect(cx - barW / 2f, baseline - barH, cx + barW / 2f, baseline, barPaint);

            // Nhãn ngày ngắn gọn
            String label = s.date.length() >= 5 ? s.date.substring(5) : s.date;
            if (count <= 10 || i % Math.max(1, count / 7) == 0 || i == count - 1) {
                canvas.drawText(label, cx, y + h, textPaint);
            }
        }
    }
}
