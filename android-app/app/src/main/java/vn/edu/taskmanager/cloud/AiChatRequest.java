package vn.edu.taskmanager.cloud;

public final class AiChatRequest {
    public final String message;
    public final Integer chat_id;

    public AiChatRequest(String message, Integer chatId) {
        this.message = message;
        this.chat_id = chatId;
    }
}
