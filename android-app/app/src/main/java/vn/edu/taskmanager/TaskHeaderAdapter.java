package vn.edu.taskmanager;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Tiêu đề các nhóm bổ sung, dùng chung vùng cuộn với danh sách công việc. */
public class TaskHeaderAdapter extends RecyclerView.Adapter<TaskHeaderAdapter.Holder> {
    private String title = "";
    public void show(String value) { title = value; notifyDataSetChanged(); }
    @Override public int getItemCount() { return title.isEmpty() ? 0 : 1; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        TextView text = new TextView(parent.getContext());
        int padding = (int) (12 * parent.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding); text.setTextSize(16);
        text.setTextColor(androidx.core.content.ContextCompat.getColor(parent.getContext(), R.color.workspace_text));
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        return new Holder(text);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) { holder.text.setText(title); }
    static class Holder extends RecyclerView.ViewHolder {
        final TextView text;
        Holder(TextView text) { super(text); this.text = text; }
    }
}
