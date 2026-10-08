package vn.edu.taskmanager;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import vn.edu.taskmanager.data.Task;

public class TaskAdapter extends ListAdapter<Task, TaskAdapter.Holder> {
    public interface Listener {
        void onEdit(Task task);
        void onDelete(Task task);
        void onCompleted(Task task, boolean completed);
    }
    private final Listener listener;

    public TaskAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<Task>() {
            @Override public boolean areItemsTheSame(@NonNull Task a, @NonNull Task b) {
                return a.id == b.id;
            }
            @Override public boolean areContentsTheSame(@NonNull Task a, @NonNull Task b) {
                return a.title.equals(b.title) && a.completed == b.completed && a.scheduledDate.equals(b.scheduledDate) && a.priority == b.priority && a.progress == b.progress && a.note.equals(b.note);
            }
        });
        this.listener = listener;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Task task = getItem(position);
        holder.title.setText(task.title);
        holder.priorityStripe.setVisibility(task.scheduledDate.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        holder.priorityStripe.setBackgroundColor(TaskPriority.color(task.priority));
        holder.date.setText((TaskSchedule.overdue(task, vn.edu.taskmanager.data.LocalStore.today()) ? "Quá hạn · " : "")
                + MainActivity.displayDate(task.scheduledDate)
                + (task.scheduledDate.isEmpty() ? "" : " · " + TaskPriority.label(task.priority))
                + " · " + (task.completed ? 100 : task.progress) + "%");
        holder.title.setPaintFlags(task.completed
                ? holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : holder.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        holder.completed.setOnCheckedChangeListener(null);
        holder.completed.setChecked(task.completed);
        holder.completed.setContentDescription(holder.itemView.getContext()
                .getString(R.string.complete_task, task.title));
        holder.completed.setOnCheckedChangeListener((button, checked) ->
                listener.onCompleted(task, checked));
        holder.itemView.setOnClickListener(v -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(v.getContext(), v);
            menu.getMenu().add(0, 1, 0, R.string.edit);
            menu.getMenu().add(0, 2, 1, R.string.delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) listener.onEdit(task); else listener.onDelete(task);
                return true;
            });
            menu.show();
        });
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title, date;
        final CheckBox completed;
        final View priorityStripe;
        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.taskTitle);
            priorityStripe = view.findViewById(R.id.taskPriorityStripe);
            date = view.findViewById(R.id.taskDateLabel);
            completed = view.findViewById(R.id.taskCompleted);
        }
    }
}
