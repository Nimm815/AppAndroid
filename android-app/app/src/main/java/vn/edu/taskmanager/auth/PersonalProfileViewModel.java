package vn.edu.taskmanager.auth;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import vn.edu.taskmanager.BuildConfig;

public final class PersonalProfileViewModel extends AndroidViewModel {
    public static final class State {
        public final ProfileModels.Profile profile;
        public final String avatarPath;
        public final boolean loading;
        public final boolean saving;
        public final boolean avatarLoading;
        public final String status;
        public final int savedEvent;

        State(ProfileModels.Profile profile, String avatarPath, boolean loading, boolean saving,
              boolean avatarLoading, String status, int savedEvent) {
            this.profile = profile;
            this.avatarPath = avatarPath;
            this.loading = loading;
            this.saving = saving;
            this.avatarLoading = avatarLoading;
            this.status = status;
            this.savedEvent = savedEvent;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final Application application;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final android.content.SharedPreferences preferences;
    private final ProfileApi api;
    private final String token;
    private final String imageFileName;
    private final String pendingFileName;
    private String pendingAvatarPath;
    private boolean loadStarted;

    public PersonalProfileViewModel(@NonNull Application application) {
        super(application);
        this.application = application;
        token = Session.token(application);
        int userId = Session.user(application);
        String profileKey = userId > 0 ? "user_" + userId : token.isEmpty() ? "local" : "session";
        preferences = application.getSharedPreferences("personal_profile_" + profileKey, 0);
        imageFileName = "personal_avatar_" + profileKey + ".jpg";
        pendingFileName = "personal_avatar_pending_" + profileKey + ".jpg";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        api = BuildConfig.AUTH_URL.isEmpty() ? null : new Retrofit.Builder()
                .baseUrl(BuildConfig.AUTH_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ProfileApi.class);
        state.setValue(new State(readSavedProfile(), savedAvatarPath(), false, false, false, "", 0));
    }

    public LiveData<State> state() { return state; }

    public void restorePendingAvatar(String path) {
        if (path == null || !new File(path).isFile()) return;
        pendingAvatarPath = path;
        State current = value();
        publish(current.profile, path, current.loading, current.saving, false,
                current.status, current.savedEvent);
    }

    public String pendingAvatarPath() { return pendingAvatarPath; }

    public void load() {
        if (loadStarted) return;
        loadStarted = true;
        State current = value();
        if (token.isEmpty() || api == null) {
            publish(current.profile, current.avatarPath, false, false, false,
                    "Hồ sơ hiện chỉ lưu trên thiết bị. Đăng nhập để đồng bộ với MySQL.", current.savedEvent);
            return;
        }
        publish(current.profile, current.avatarPath, true, false, false,
                "Đang tải thông tin từ máy chủ…", current.savedEvent);
        api.getProfile(bearer()).enqueue(new Callback<>() {
            @Override public void onResponse(Call<ProfileModels.Profile> call, Response<ProfileModels.Profile> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    State now = value();
                    publish(now.profile, now.avatarPath, false, false, false,
                            "Chưa tải được hồ sơ từ máy chủ: " + responseError(response), now.savedEvent);
                    return;
                }
                ProfileModels.Profile profile = response.body();
                persistProfile(profile, savedAvatarPath());
                State now = value();
                publish(profile, now.avatarPath, false, false, false,
                        "Đang dùng hồ sơ đã đồng bộ với máy chủ.", now.savedEvent);
                if (profile.avatarUrl != null && !profile.avatarUrl.isBlank()) downloadAvatar();
            }

            @Override public void onFailure(Call<ProfileModels.Profile> call, Throwable error) {
                State now = value();
                publish(now.profile, now.avatarPath, false, false, false,
                        "Không kết nối được Auth Service (cổng 8081). Hồ sơ trên thiết bị vẫn được giữ.", now.savedEvent);
            }
        });
    }

    public void selectAvatar(Uri uri) {
        State current = value();
        publish(current.profile, current.avatarPath, current.loading, current.saving, true,
                "Đang xử lý ảnh…", current.savedEvent);
        imageExecutor.execute(() -> {
            try {
                File destination = new File(application.getCacheDir(), pendingFileName);
                try (InputStream input = application.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Không mở được ảnh đã chọn");
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(input, null, bounds);
                    if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 12000
                            || bounds.outHeight > 12000 || (long) bounds.outWidth * bounds.outHeight > 60_000_000L) {
                        throw new IOException("Kích thước ảnh không hợp lệ hoặc quá lớn");
                    }
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize(uri);
                Bitmap bitmap;
                try (InputStream input = application.getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(input, null, options);
                }
                if (bitmap == null) throw new IOException("Không đọc được ảnh đã chọn");
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                        throw new IOException("Không thể xử lý ảnh đại diện");
                    }
                } finally {
                    bitmap.recycle();
                }
                if (destination.length() > 5L * 1024 * 1024) {
                    if (!destination.delete()) {
                        throw new IOException("Không xóa được ảnh tạm vượt giới hạn");
                    }
                    throw new IOException("Ảnh sau khi xử lý vượt quá 5 MB");
                }
                pendingAvatarPath = destination.getAbsolutePath();
                State latest = value();
                publish(latest.profile, pendingAvatarPath, latest.loading, latest.saving, false,
                        latest.status, latest.savedEvent);
            } catch (IOException | SecurityException error) {
                State latest = value();
                publish(latest.profile, latest.avatarPath, latest.loading, latest.saving, false,
                        "Không chọn được ảnh đại diện: " + error.getMessage(), latest.savedEvent);
            }
        });
    }

    private int sampleSize(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = application.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Không mở được ảnh đã chọn");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1024) sample *= 2;
        return sample;
    }

    public void save(ProfileModels.UpdateRequest request) {
        State current = value();
        if (current.saving || current.avatarLoading) return;
        if (token.isEmpty() || api == null) {
            try {
                String imagePath = commitAvatar(pendingAvatarPath, current.avatarPath);
                persistProfile(fromRequest(request, current.profile == null ? 0 : current.profile.userId), imagePath);
                pendingAvatarPath = null;
                int event = current.savedEvent + 1;
                publish(fromRequest(request, current.profile == null ? 0 : current.profile.userId), imagePath,
                        false, false, false,
                        "Đã lưu trên thiết bị. Chưa đồng bộ MySQL vì ứng dụng chưa có phiên đăng nhập.", event);
            } catch (IOException error) {
                publish(current.profile, current.avatarPath, false, false, false,
                        "Không lưu được ảnh trên thiết bị: " + error.getMessage(), current.savedEvent);
            }
            return;
        }
        publish(current.profile, current.avatarPath, current.loading, true, false,
                "Đang lưu thay đổi…", current.savedEvent);
        updateServer(request);
    }

    private void updateServer(ProfileModels.UpdateRequest request) {
        api.updateProfile(bearer(), request).enqueue(new Callback<>() {
            @Override public void onResponse(Call<ProfileModels.Profile> call,
                                              Response<ProfileModels.Profile> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    saveFailed(responseError(response));
                    return;
                }
                if (pendingAvatarPath != null) uploadAvatarAfterProfile(request, response.body());
                else finishSuccessfulSave(response.body(), "Đã cập nhật thông tin cá nhân trên máy chủ.");
            }

            @Override public void onFailure(Call<ProfileModels.Profile> call, Throwable error) {
                saveFailed("Không kết nối được Auth Service (cổng 8081). Thay đổi chưa được xác nhận trên máy chủ.");
            }
        });
    }

    private void uploadAvatarAfterProfile(ProfileModels.UpdateRequest request,
                                          ProfileModels.Profile savedProfile) {
        File image = new File(pendingAvatarPath);
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), image);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", "avatar.jpg", body);
        api.uploadAvatar(bearer(), part).enqueue(new Callback<>() {
            @Override public void onResponse(Call<ProfileModels.Profile> call,
                                              Response<ProfileModels.Profile> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    finishProfileOnlySave(savedProfile,
                            "Thông tin văn bản đã lưu trên máy chủ, nhưng ảnh chưa được cập nhật: "
                                    + responseError(response));
                    return;
                }
                finishSuccessfulSave(response.body(), "Đã cập nhật hồ sơ và ảnh đại diện trên máy chủ.");
            }

            @Override public void onFailure(Call<ProfileModels.Profile> call, Throwable error) {
                finishProfileOnlySave(savedProfile,
                        "Thông tin văn bản đã lưu trên máy chủ, nhưng chưa tải được ảnh. Kiểm tra Auth Service (cổng 8081).");
            }
        });
    }

    private void finishSuccessfulSave(ProfileModels.Profile profile, String message) {
        try {
            String imagePath = commitAvatar(pendingAvatarPath, value().avatarPath);
            persistProfile(profile, imagePath);
            pendingAvatarPath = null;
            State now = value();
            publish(profile, imagePath, false, false, false, message, now.savedEvent + 1);
        } catch (IOException error) {
            State now = value();
            publish(profile, now.avatarPath, false, false, false,
                    "Máy chủ đã lưu hồ sơ, nhưng không lưu được bản sao ảnh trên thiết bị: "
                            + error.getMessage(), now.savedEvent + 1);
        }
    }

    private void finishProfileOnlySave(ProfileModels.Profile profile, String message) {
        State now = value();
        String savedImagePath = savedAvatarPath();
        persistProfile(profile, savedImagePath);
        publish(profile, pendingAvatarPath == null ? savedImagePath : pendingAvatarPath,
                false, false, false, message, now.savedEvent + 1);
    }

    private void downloadAvatar() {
        api.getAvatar(bearer()).enqueue(new Callback<>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                imageExecutor.execute(() -> {
                    try (ResponseBody body = response.body();
                         InputStream input = body.byteStream();
                         FileOutputStream output = new FileOutputStream(avatarFile())) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        State current = value();
                        publish(current.profile, avatarFile().getAbsolutePath(), current.loading,
                                current.saving, current.avatarLoading, current.status, current.savedEvent);
                    } catch (IOException error) {
                        State current = value();
                        publish(current.profile, current.avatarPath, current.loading, current.saving,
                                current.avatarLoading, "Không tải được ảnh đại diện: " + error.getMessage(),
                                current.savedEvent);
                    }
                });
            }

            @Override public void onFailure(Call<ResponseBody> call, Throwable error) {
                State current = value();
                publish(current.profile, current.avatarPath, current.loading, current.saving,
                        current.avatarLoading, "Không tải được ảnh đại diện từ máy chủ.", current.savedEvent);
            }
        });
    }

    private void saveFailed(String message) {
        State current = value();
        publish(current.profile, current.avatarPath, current.loading, false, false,
                "Chưa lưu được thay đổi: " + message, current.savedEvent);
    }

    private String responseError(Response<?> response) {
        int code = response.code();
        String message = "Máy chủ trả về HTTP " + code + ".";
        if (code == 400) message = "Thông tin hồ sơ không hợp lệ.";
        if (code == 401) message = "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.";
        if (code == 409) message = "Email đã được sử dụng bởi tài khoản khác.";
        if (response.errorBody() != null) {
            try {
                String body = response.errorBody().string();
                if (!body.isBlank()) {
                    var json = JsonParser.parseString(body);
                    if (json.isJsonObject() && json.getAsJsonObject().has("message")) {
                        message = json.getAsJsonObject().get("message").getAsString();
                    }
                }
            } catch (IOException | IllegalStateException error) {
                return message + " Không đọc được thông báo lỗi từ máy chủ.";
            }
        }
        return message;
    }

    private String bearer() { return "Bearer " + token; }

    private ProfileModels.Profile readSavedProfile() {
        ProfileModels.Profile profile = new ProfileModels.Profile();
        profile.fullName = preferences.getString("fullName", "");
        profile.email = preferences.getString("email", "");
        profile.phoneNumber = preferences.getString("phoneNumber", "");
        profile.birthDate = preferences.getString("birthDate", "");
        profile.gender = preferences.getString("gender", "");
        profile.userId = preferences.getInt("userId", 0);
        return profile;
    }

    private String savedAvatarPath() {
        String path = preferences.getString("avatarPath", "");
        return path.isEmpty() || !new File(path).isFile() ? null : path;
    }

    private void persistProfile(ProfileModels.Profile profile, String avatarPath) {
        preferences.edit()
                .putString("fullName", nullToEmpty(profile.fullName))
                .putString("email", nullToEmpty(profile.email))
                .putString("phoneNumber", nullToEmpty(profile.phoneNumber))
                .putString("birthDate", nullToEmpty(profile.birthDate))
                .putString("gender", nullToEmpty(profile.gender))
                .putInt("userId", profile.userId)
                .putString("avatarPath", nullToEmpty(avatarPath))
                .apply();
    }

    private String commitAvatar(String draftPath, String currentPath) throws IOException {
        if (draftPath == null) return currentPath;
        File destination = avatarFile();
        File draft = new File(draftPath);
        try (FileInputStream input = new FileInputStream(draft);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        if (!draft.delete()) throw new IOException("Không xóa được ảnh tạm sau khi lưu");
        return destination.getAbsolutePath();
    }

    private File avatarFile() { return new File(application.getFilesDir(), imageFileName); }

    private ProfileModels.Profile fromRequest(ProfileModels.UpdateRequest request, int userId) {
        ProfileModels.Profile profile = new ProfileModels.Profile();
        profile.userId = userId;
        profile.fullName = request.fullName == null ? "" : request.fullName.trim();
        profile.email = request.email == null ? "" : request.email.trim();
        profile.phoneNumber = request.phoneNumber == null ? "" : request.phoneNumber.trim();
        profile.birthDate = request.birthDate == null ? "" : request.birthDate;
        profile.gender = request.gender == null ? "" : request.gender;
        return profile;
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }

    private State value() {
        State current = state.getValue();
        return current == null ? new State(readSavedProfile(), savedAvatarPath(), false, false, false, "", 0) : current;
    }

    private void publish(ProfileModels.Profile profile, String avatarPath, boolean loading,
                         boolean saving, boolean avatarLoading, String status, int savedEvent) {
        State next = new State(profile, avatarPath, loading, saving, avatarLoading, status, savedEvent);
        if (Looper.myLooper() == Looper.getMainLooper()) state.setValue(next);
        else mainHandler.post(() -> state.setValue(next));
    }

    @Override protected void onCleared() {
        imageExecutor.shutdown();
        super.onCleared();
    }
}
