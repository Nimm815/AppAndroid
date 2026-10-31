package vn.edu.taskmanager.cloud;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.POST;

public interface AiApi {
    @POST("api/ai/chat")
    Call<AiChatResponse> chat(@Body AiChatRequest request);

    @GET("api/ai/chats")
    Call<java.util.List<AiChatSummary>> chats();

    @GET("api/ai/chats/{chatId}/messages")
    Call<AiChatHistory> history(@Path("chatId") int chatId);
}
