package vn.edu.taskmanager;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public final class HabitColorPickerDialog extends DialogFragment {
    private static final String[] NAMES = {
            "Tím", "Xanh lá", "Xanh dương", "Xanh ngọc", "Hồng", "Cam",
            "Vàng", "Đỏ", "Nâu", "Tím đậm", "Xám", "Than"
    };
    private static final String[] COLORS = {
            "#5B6FDE", "#2E9D62", "#3285D1", "#1BAA9A", "#D65A83", "#E08A36",
            "#D7AC20", "#D84A4A", "#9A6B4F", "#8552B8", "#78828F", "#3A414A"
    };

    public HabitColorPickerDialog() {}

    static HabitColorPickerDialog create(String selectedColor) {
        HabitColorPickerDialog dialog = new HabitColorPickerDialog();
        Bundle args = new Bundle();
        args.putString("selectedColor", selectedColor);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        String selected = requireArguments().getString("selectedColor", "#5B6FDE");
        GridLayout palette = new GridLayout(requireContext());
        palette.setId(R.id.habitColorPalette);
        palette.setColumnCount(4);
        palette.setPadding(dp(8), dp(8), dp(8), dp(8));
        for (int i = 0; i < COLORS.length; i++) {
            String color = COLORS[i];
            View swatch = new View(requireContext());
            swatch.setTag(color);
            swatch.setContentDescription(NAMES[i]);
            swatch.setFocusable(true);
            swatch.setClickable(true);
            swatch.setBackground(colorSwatch(color, color.equalsIgnoreCase(selected)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(44);
            params.height = dp(44);
            params.setGravity(Gravity.CENTER);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            palette.addView(swatch, params);
            swatch.setOnClickListener(view -> {
                Bundle result = new Bundle();
                result.putString(HabitEditorDialog.COLOR_RESULT_VALUE, color);
                getParentFragmentManager().setFragmentResult(HabitEditorDialog.COLOR_RESULT_KEY, result);
                dismiss();
            });
        }
        return new AlertDialog.Builder(requireContext())
                .setTitle("Chọn màu thói quen")
                .setView(palette)
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    private GradientDrawable colorSwatch(String value, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.parseColor(value));
        shape.setStroke(dp(selected ? 3 : 1), selected ? Color.WHITE : Color.LTGRAY);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
