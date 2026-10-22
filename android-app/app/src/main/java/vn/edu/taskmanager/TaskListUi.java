package vn.edu.taskmanager;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import java.util.List;
import vn.edu.taskmanager.data.TaskListWithTasks;

/** Cùng một hàng danh sách cho menu và trang quản lý; tự cập nhật số việc hoàn thành. */
final class TaskListUi {
    private TaskListUi() { }

    static void bind(MainActivity activity, LinearLayout parent, AlertDialog dialog) {
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        parent.addView(rows);
        TaskViewModel model = new ViewModelProvider(activity).get(TaskViewModel.class);
        Observer<List<TaskListWithTasks>> observer = lists -> {
            rows.removeAllViews();
            if (lists == null || lists.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText(R.string.empty_task_lists);
                int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
                empty.setPadding(padding, padding, padding, padding);
                rows.addView(empty);
                return;
            }
            for (TaskListWithTasks item : lists) {
                View row = activity.getLayoutInflater().inflate(R.layout.item_task_list, rows, false);
                ((TextView) row.findViewById(R.id.taskListTitle)).setText(item.list.title);
                ((TextView) row.findViewById(R.id.taskListCount)).setText(activity.getString(
                        R.string.list_completion, item.completedCount(), item.tasks.size()));
                row.findViewById(R.id.openTaskList).setOnClickListener(v -> {
                    dialog.dismiss(); activity.openTaskList(item.list.id);
                });
                View edit = row.findViewById(R.id.taskListEdit);
                edit.setContentDescription(activity.getString(R.string.edit_named_list, item.list.title));
                edit.setOnClickListener(v -> { dialog.dismiss(); activity.editTaskList(item.list.id); });
                rows.addView(row);
            }
        };
        model.taskLists.observe(activity, observer);
        dialog.setOnDismissListener(ignored -> model.taskLists.removeObserver(observer));
    }
}
