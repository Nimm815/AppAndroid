package vn.edu.taskmanager;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class AiChatAdapter extends RecyclerView.Adapter<AiChatAdapter.MessageHolder> {
    static final class Message {
        final String text;
        final boolean fromUser;
        Message(String text, boolean fromUser) { this.text = text; this.fromUser = fromUser; }
    }

    private final List<Message> messages = new ArrayList<>();
    private final int textColor;

    AiChatAdapter(int textColor) { this.textColor = textColor; }
    void add(String text, boolean fromUser) {
        messages.add(new Message(text, fromUser));
        notifyItemInserted(messages.size() - 1);
    }
    void clear() {
        int previousSize = messages.size();
        messages.clear();
        if (previousSize > 0) notifyItemRangeRemoved(0, previousSize);
    }

    @NonNull @Override public MessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(2, 4, 2, 4);
        TextView bubble = new TextView(parent.getContext());
        bubble.setTextSize(14);
        bubble.setTextColor(textColor);
        bubble.setLineSpacing(2, 1.0f);
        bubble.setIncludeFontPadding(true);
        bubble.setPadding(12, 9, 12, 9);
        bubble.setMaxWidth((int) (parent.getResources().getDisplayMetrics().widthPixels * .70f));
        row.addView(bubble, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new MessageHolder(row, bubble);
    }

    @Override public void onBindViewHolder(@NonNull MessageHolder holder, int position) {
        Message message = messages.get(position);
        holder.row.setGravity(message.fromUser ? Gravity.END : Gravity.START);
        holder.bubble.setText(message.fromUser ? message.text : AiMarkdown.format(message.text));
        holder.bubble.setTextIsSelectable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(message.fromUser ? Color.rgb(230, 233, 255) : Color.rgb(242, 243, 247));
        background.setCornerRadius(holder.bubble.getResources().getDisplayMetrics().density * 16);
        holder.bubble.setBackground(background);
    }

    @Override public int getItemCount() { return messages.size(); }

    static final class MessageHolder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView bubble;
        MessageHolder(LinearLayout row, TextView bubble) { super(row); this.row = row; this.bubble = bubble; }
    }
}
