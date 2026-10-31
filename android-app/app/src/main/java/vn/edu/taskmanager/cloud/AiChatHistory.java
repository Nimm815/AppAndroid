package vn.edu.taskmanager.cloud;

import java.util.List;

public final class AiChatHistory {
    public int chat_id;
    public List<AiChatMessage> messages;

    public static final class AiChatMessage {
        public String role;
        public String content;
        public String created_at;
    }
}
