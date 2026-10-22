package vn.edu.taskmanager.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import vn.edu.taskmanager.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom View vẽ biểu đồ cột thể hiện số lượt ghi nhận theo từng ngày (UC12).
 */
public class DailyChartView extends View {

    public static class BarData {
        public String dateLabel; // "22/10" hoặc "T2"
        public int count;

        public BarData(String dateLabel, int count) {
            this.dateLabel = dateLabel;
            this.count = count;
        }
    }

    private final List<BarData> data = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    public DailyChartView(Context context) {
        super(context);
        init();
    }

    public DailyChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DailyChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(ContextCompat.getColor(getContext(), R.color.navigation_blue));
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.workspace_muted));
        textPaint.setTextSize(spToPx(10));
        textPaint.setTextAlign(Paint.Align.CENTER);
        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.workspace_line));
        linePaint.setStrokeWidth(dpToPx(1));
    }

    public void setData(List<BarData> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || data.isEmpty()) return;

        float paddingLeft = dpToPx(16);
        float paddingRight = dpToPx(16);
        float paddingTop = dpToPx(24);
        float paddingBottom = dpToPx(28);

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        // Trục hoành
        float baselineY = height - paddingBottom;
        canvas.drawLine(paddingLeft, baselineY, width - paddingRight, baselineY, linePaint);

        // Tìm giá trị max
        int maxCount = 1;
        for (BarData b : data) {
            if (b.count > maxCount) maxCount = b.count;
        }

        int count = data.size();
        float barSlotWidth = chartWidth / count;
        float barWidth = Math.min(barSlotWidth * 0.6f, dpToPx(24));

        for (int i = 0; i < count; i++) {
            BarData item = data.get(i);
            float cx = paddingLeft + i * barSlotWidth + barSlotWidth / 2f;
            float barH = (item.count / (float) maxCount) * chartHeight;
            if (item.count > 0 && barH < dpToPx(4)) {
                barH = dpToPx(4); // Tối thiểu để nhìn thấy
            }

            float left = cx - barWidth / 2f;
            float top = baselineY - barH;
            float right = cx + barWidth / 2f;
            float bottom = baselineY;

            barRect.set(left, top, right, bottom);
            canvas.drawRoundRect(barRect, dpToPx(4), dpToPx(4), barPaint);

            // Số lượt trên đầu cột nếu > 0
            if (item.count > 0) {
                canvas.drawText(String.valueOf(item.count), cx, top - dpToPx(4), textPaint);
            }

            // Nhãn ngày bên dưới trục
            // Nếu có nhiều hơn 14 ngày thì chỉ hiện nhãn cách quãng để không bị đè chữ
            boolean showLabel = count <= 14 || (i % Math.max(1, count / 7) == 0) || i == count - 1;
            if (showLabel) {
                canvas.drawText(item.dateLabel, cx, height - dpToPx(10), textPaint);
            }
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
