package vn.edu.taskmanager;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageButton;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

/** Thanh điều hướng dùng chung giữa các tab và màn hình hẹn giờ/báo cáo. */
public final class ReferenceUi {
    private ReferenceUi() { }
    public static void bindNavigation(View view, DialogFragment screen) {
        int[] buttons = {R.id.navTasks, R.id.navJournal, R.id.navCalendar, R.id.navSettings};
        for (int i = 0; i < buttons.length; i++) {
            final int tab = i;
            ImageButton button = view.findViewById(buttons[i]);
            button.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(view.getContext(),
                    i == 0 ? R.color.workspace_text : R.color.workspace_muted)));
            button.setOnClickListener(v -> {
                MainActivity activity = (MainActivity) screen.requireActivity();
                screen.dismiss(); activity.selectWorkspaceTab(tab);
            });
        }
    }
}
