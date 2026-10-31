package vn.edu.taskmanager;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonParser;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.taskmanager.auth.Session;
import vn.edu.taskmanager.cloud.AiApi;
import vn.edu.taskmanager.cloud.AiChatRequest;
import vn.edu.taskmanager.cloud.AiChatResponse;
import vn.edu.taskmanager.cloud.AiChatHistory;
import vn.edu.taskmanager.cloud.AiChatSummary;

/** Cửa sổ chat nhỏ nổi trên Trang chủ, dùng AI Service riêng. */
public final class AiChatDialog extends AppCompatDialogFragment {
    private AiChatAdapter adapter;
    private EditText input;
    private Button send;
    private TextView status;
    private TextView loginPrompt;
    private RecyclerView messages;
    private AiApi api;
    private Call<?> activeCall;
    private Integer chatId;
    private boolean waiting;
    private boolean restoreAttempted;

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_ai_chat);
        dialog.setCanceledOnTouchOutside(true);
        if (state != null && state.containsKey("ai_chat_id")) {
            chatId = state.getInt("ai_chat_id");
        } else {
            int savedChatId = chatPreferences().getInt("current_chat_id", 0);
            chatId = savedChatId > 0 ? savedChatId : null;
        }

        int textColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.workspace_text);
        adapter = new AiChatAdapter(textColor);
        messages = dialog.findViewById(R.id.aiChatMessages);
        messages.setLayoutManager(new LinearLayoutManager(requireContext()));
        messages.setAdapter(adapter);
        input = dialog.findViewById(R.id.aiChatInput);
        send = dialog.findViewById(R.id.aiChatSend);
        status = dialog.findViewById(R.id.aiChatStatus);
        loginPrompt = dialog.findViewById(R.id.aiLoginPrompt);

        dialog.findViewById(R.id.aiChatClose).setOnClickListener(view -> dismiss());
        dialog.findViewById(R.id.aiChatNew).setOnClickListener(view -> startNewConversation());
        dialog.findViewById(R.id.aiChatHistory).setOnClickListener(view -> openChatHistory());
        send.setOnClickListener(view -> sendMessage());
        loginPrompt.setOnClickListener(view -> {
            startActivity(new android.content.Intent(requireContext(), AuthActivity.class));
        });
        String aiUrl = BuildConfig.AI_URL;
        if (!aiUrl.isEmpty()) {
            Context appContext = requireContext().getApplicationContext();
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        String token = Session.token(appContext);
                        okhttp3.Request.Builder request = chain.request().newBuilder();
                        if (token != null && !token.isEmpty())
                            request.header("Authorization", "Bearer " + token);
                        return chain.proceed(request.build());
                    })
                    .build();
            api = new Retrofit.Builder().baseUrl(aiUrl).client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(AiApi.class);
        } else {
            send.setEnabled(false);
            setStatus("Bản ứng dụng chưa cấu hình địa chỉ AI Service.");
        }
        updateLoginPrompt();
        if (!Session.token(requireContext()).isEmpty()) {
            restoreAttempted = true;
            if (chatId == null) showWelcome();
            else loadConversation(chatId);
        } else {
            showWelcome();
        }
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams params = window.getAttributes();
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        params.width = getResources().getDisplayMetrics().widthPixels - dp(28);
        params.height = Math.min(screenHeight - dp(150), dp(640));
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(48);
        window.setAttributes(params);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override public void onResume() {
        super.onResume();
        updateLoginPrompt();
        if (api != null && !restoreAttempted && !Session.token(requireContext()).isEmpty()) {
            restoreAttempted = true;
            int savedChatId = chatPreferences().getInt("current_chat_id", 0);
            if (chatId == null && savedChatId > 0) chatId = savedChatId;
            if (chatId == null) showWelcome(); else loadConversation(chatId);
        }
    }

    private void sendMessage() {
        String message = input.getText().toString().trim();
        if (message.isEmpty() || waiting) return;
        if (Session.token(requireContext()).isEmpty()) {
            updateLoginPrompt();
            setStatus(getString(R.string.ai_chat_login));
            return;
        }
        if (api == null) return;

        adapter.add(message, true);
        input.setText("");
        scrollToEnd();
        waiting = true;
        send.setEnabled(false);
        setStatus(getString(R.string.ai_chat_loading));
        Call<AiChatResponse> chatCall = api.chat(new AiChatRequest(message, chatId));
        activeCall = chatCall;
        chatCall.enqueue(new Callback<AiChatResponse>() {
            @Override public void onResponse(@NonNull Call<AiChatResponse> call,
                                            @NonNull Response<AiChatResponse> response) {
                if (!isAdded()) return;
                waiting = false;
                send.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    chatId = response.body().chat_id;
                    chatPreferences().edit().putInt("current_chat_id", chatId).apply();
                    String answer = response.body().answer;
                    adapter.add(answer == null || answer.trim().isEmpty()
                            ? getString(R.string.ai_chat_error) : answer, false);
                    setStatus("");
                } else {
                    setStatus(errorMessage(response));
                }
                scrollToEnd();
            }

            @Override public void onFailure(@NonNull Call<AiChatResponse> call, @NonNull Throwable error) {
                if (!isAdded() || call.isCanceled()) return;
                waiting = false;
                send.setEnabled(true);
                setStatus("Không kết nối được AI Service. Hãy kiểm tra backend và thử lại.");
            }
        });
    }

    private <T> String errorMessage(Response<T> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                var json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("detail")) return json.get("detail").getAsString();
                if (json.has("message")) return json.get("message").getAsString();
            }
        } catch (Exception ignored) { }
        if (response.code() == 401) return "Phiên đăng nhập hết hạn. Hãy đăng nhập lại để dùng AI.";
        if (response.code() == 503) return "AI chưa sẵn sàng. Kiểm tra Gemini key, database và các service.";
        return getString(R.string.ai_chat_error) + " (HTTP " + response.code() + ")";
    }

    private void updateLoginPrompt() {
        boolean loggedIn = !Session.token(requireContext()).isEmpty();
        loginPrompt.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
    }

    private SharedPreferences chatPreferences() {
        Context context = requireContext();
        String name = Session.storage(context, "ai_chat_session");
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private void showWelcome() {
        adapter.clear();
        adapter.add(getString(R.string.ai_chat_welcome), false);
        scrollToEnd();
    }

    private void startNewConversation() {
        if (activeCall != null) activeCall.cancel();
        waiting = false;
        send.setEnabled(true);
        chatId = null;
        restoreAttempted = true;
        chatPreferences().edit().remove("current_chat_id").apply();
        setStatus("");
        showWelcome();
    }

    private void loadConversation(int selectedChatId) {
        if (api == null) return;
        restoreAttempted = true;
        adapter.clear();
        setStatus(getString(R.string.ai_chat_loading));
        Call<AiChatHistory> historyCall = api.history(selectedChatId);
        activeCall = historyCall;
        historyCall.enqueue(new Callback<AiChatHistory>() {
            @Override public void onResponse(@NonNull Call<AiChatHistory> call,
                                            @NonNull Response<AiChatHistory> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    chatId = null;
                    chatPreferences().edit().remove("current_chat_id").apply();
                    showWelcome();
                    setStatus(errorMessage(response));
                    return;
                }
                chatId = selectedChatId;
                chatPreferences().edit().putInt("current_chat_id", selectedChatId).apply();
                adapter.clear();
                List<AiChatHistory.AiChatMessage> history = response.body().messages;
                if (history != null) for (AiChatHistory.AiChatMessage message : history) {
                    if (message != null && message.content != null)
                        adapter.add(message.content, "user".equalsIgnoreCase(message.role));
                }
                if (adapter.getItemCount() == 0) adapter.add(getString(R.string.ai_chat_welcome), false);
                setStatus("");
                scrollToEnd();
            }

            @Override public void onFailure(@NonNull Call<AiChatHistory> call, @NonNull Throwable error) {
                if (!isAdded() || call.isCanceled()) return;
                showWelcome();
                setStatus(getString(R.string.ai_chat_history_failed));
            }
        });
    }

    private void openChatHistory() {
        if (api == null) return;
        if (Session.token(requireContext()).isEmpty()) {
            setStatus(getString(R.string.ai_chat_login));
            return;
        }
        setStatus(getString(R.string.ai_chat_loading));
        Call<List<AiChatSummary>> chatsCall = api.chats();
        activeCall = chatsCall;
        chatsCall.enqueue(new Callback<List<AiChatSummary>>() {
            @Override public void onResponse(@NonNull Call<List<AiChatSummary>> call,
                                            @NonNull Response<List<AiChatSummary>> response) {
                if (!isAdded()) return;
                setStatus("");
                if (!response.isSuccessful() || response.body() == null) {
                    setStatus(getString(R.string.ai_chat_history_failed));
                    return;
                }
                List<AiChatSummary> chats = response.body();
                if (chats.isEmpty()) {
                    new AlertDialog.Builder(requireContext()).setTitle(R.string.ai_chat_history_title)
                            .setMessage(R.string.ai_chat_no_history).setPositiveButton(R.string.close, null).show();
                    return;
                }
                List<AiChatSummary> valid = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                for (AiChatSummary chat : chats) if (chat != null && chat.chat_id > 0) {
                    valid.add(chat);
                    String title = chat.title == null || chat.title.trim().isEmpty()
                            ? getString(R.string.ai_chat_title) : chat.title.trim();
                    labels.add(title.length() > 56 ? title.substring(0, 53) + "…" : title);
                }
                String[] items = labels.toArray(new String[0]);
                new AlertDialog.Builder(requireContext()).setTitle(R.string.ai_chat_history_title)
                        .setItems(items, (dialog, which) -> loadConversation(valid.get(which).chat_id))
                        .setNegativeButton(R.string.cancel, null).show();
            }

            @Override public void onFailure(@NonNull Call<List<AiChatSummary>> call, @NonNull Throwable error) {
                if (!isAdded() || call.isCanceled()) return;
                setStatus(getString(R.string.ai_chat_history_failed));
            }
        });
    }

    private void setStatus(String message) {
        status.setText(message);
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void scrollToEnd() {
        if (adapter.getItemCount() > 0) messages.scrollToPosition(adapter.getItemCount() - 1);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (chatId != null) outState.putInt("ai_chat_id", chatId);
    }

    @Override public void onDestroyView() {
        if (activeCall != null) activeCall.cancel();
        super.onDestroyView();
    }
}
