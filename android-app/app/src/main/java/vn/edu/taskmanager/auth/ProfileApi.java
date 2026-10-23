package vn.edu.taskmanager.auth;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import retrofit2.http.PUT;
import retrofit2.http.POST;

public interface ProfileApi {
    @GET("api/users/me")
    Call<ProfileModels.Profile> getProfile(@Header("Authorization") String authorization);

    @PUT("api/users/me")
    Call<ProfileModels.Profile> updateProfile(@Header("Authorization") String authorization,
                                               @Body ProfileModels.UpdateRequest request);

    @Multipart
    @POST("api/users/me/avatar")
    Call<ProfileModels.Profile> uploadAvatar(@Header("Authorization") String authorization,
                                              @Part MultipartBody.Part file);

    @GET("api/users/me/avatar")
    Call<ResponseBody> getAvatar(@Header("Authorization") String authorization);
}
