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
                return a.title.equals(b.title) && a.completed == b.completed;
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
        holder.title.setPaintFlags(task.completed
                ? holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : holder.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        holder.completed.setOnCheckedChangeListener(null);
        holder.completed.setChecked(task.completed);
        holder.completed.setContentDescription(holder.itemView.getContext()
                .getString(R.string.complete_task, task.title));
        holder.completed.setOnCheckedChangeListener((button, checked) ->
                listener.onCompleted(task, checked));
        holder.itemView.findViewById(R.id.editTask).setOnClickListener(v -> listener.onEdit(task));
        holder.itemView.findViewById(R.id.deleteTask).setOnClickListener(v -> listener.onDelete(task));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final CheckBox completed;
        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.taskTitle);
            completed = view.findViewById(R.id.taskCompleted);
        }
    }
}
