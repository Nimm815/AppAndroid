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
        int[] indicators = {R.id.navTasksIndicator, R.id.navJournalIndicator, R.id.navCalendarIndicator, R.id.navSettingsIndicator};
        for (int i = 0; i < buttons.length; i++) {
            view.findViewById(indicators[i]).setVisibility(i == 0 ? View.VISIBLE : View.INVISIBLE);
            final int tab = i;
            ImageButton button = view.findViewById(buttons[i]);
            button.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(view.getContext(),
                    i == 0 ? R.color.navigation_blue : R.color.workspace_muted)));
            button.setOnClickListener(v -> {
                MainActivity activity = (MainActivity) screen.requireActivity();
                screen.dismiss(); activity.selectWorkspaceTab(tab);
            });
        }
    }
}
