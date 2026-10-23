package vn.edu.taskmanager;

import android.app.DatePickerDialog;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;

import vn.edu.taskmanager.auth.PersonalProfileViewModel;
import vn.edu.taskmanager.auth.ProfileModels;
import vn.edu.taskmanager.auth.ProfileValidator;

public final class PersonalProfileActivity extends AppCompatActivity {
    private static final String STATE_BIRTH_DATE = "profile_birth_date";
    private static final String STATE_USER_EDITED = "profile_user_edited";
    private static final String STATE_AVATAR_DRAFT = "profile_avatar_draft";
    private final DateTimeFormatter displayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("vi", "VN"));
    private PersonalProfileViewModel viewModel;
    private EditText fullName;
    private EditText email;
    private EditText phone;
    private Button birthDate;
    private RadioGroup gender;
    private Button save;
    private ProgressBar progress;
    private TextView status;
    private ImageView avatar;
    private TextView avatarFallback;
    private String birthDateValue = "";
    private boolean suppressChanges;
    private boolean userEdited;
    private int lastSavedEvent;

    private final ActivityResultLauncher<String> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::avatarSelected);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_profile);
        fullName = findViewById(R.id.profileFullName);
        email = findViewById(R.id.profileEmail);
        phone = findViewById(R.id.profilePhone);
        birthDate = findViewById(R.id.profileBirthDate);
        gender = findViewById(R.id.profileGender);
        save = findViewById(R.id.profileSave);
        progress = findViewById(R.id.profileProgress);
        status = findViewById(R.id.profileStatus);
        avatar = findViewById(R.id.profileAvatar);
        avatarFallback = findViewById(R.id.profileAvatarFallback);
        viewModel = new ViewModelProvider(this).get(PersonalProfileViewModel.class);

        if (savedInstanceState != null) {
            birthDateValue = savedInstanceState.getString(STATE_BIRTH_DATE, "");
            userEdited = savedInstanceState.getBoolean(STATE_USER_EDITED, false);
        }

        findViewById(R.id.profileBack).setOnClickListener(view -> finish());
        findViewById(R.id.profileChooseAvatar).setOnClickListener(view -> avatarPicker.launch("image/*"));
        birthDate.setOnClickListener(view -> chooseBirthDate());
        findViewById(R.id.profileClearBirthDate).setOnClickListener(view -> {
            birthDateValue = "";
            renderBirthDate();
            userEdited = true;
        });
        save.setOnClickListener(view -> saveProfile());
        attachChangeListeners();
        viewModel.state().observe(this, this::renderState);
        if (savedInstanceState != null) {
            viewModel.restorePendingAvatar(savedInstanceState.getString(STATE_AVATAR_DRAFT));
        }
        viewModel.load();
    }

    private void attachChangeListeners() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressChanges) userEdited = true;
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        };
        fullName.addTextChangedListener(watcher);
        email.addTextChangedListener(watcher);
        phone.addTextChangedListener(watcher);
        gender.setOnCheckedChangeListener((group, checkedId) -> {
            if (!suppressChanges) userEdited = true;
        });
    }

    private void renderState(PersonalProfileViewModel.State state) {
        if (state.profile != null && (!userEdited || lastSavedEvent != state.savedEvent)) {
            if (!userEdited || state.savedEvent != lastSavedEvent) fillProfile(state.profile);
        }
        if (state.savedEvent != lastSavedEvent) {
            lastSavedEvent = state.savedEvent;
            if (state.savedEvent > 0) userEdited = false;
        }
        status.setText(state.status);
        progress.setVisibility(state.loading || state.saving || state.avatarLoading ? View.VISIBLE : View.GONE);
        save.setEnabled(!state.loading && !state.saving && !state.avatarLoading);
        save.setText(state.saving ? R.string.saving : R.string.save_changes);
        showAvatar(state.avatarPath);
    }

    private void fillProfile(ProfileModels.Profile profile) {
        suppressChanges = true;
        fullName.setText(profile.fullName == null ? "" : profile.fullName);
        email.setText(profile.email == null ? "" : profile.email);
        phone.setText(profile.phoneNumber == null ? "" : profile.phoneNumber);
        birthDateValue = profile.birthDate == null ? "" : profile.birthDate;
        renderBirthDate();
        gender.clearCheck();
        if ("MALE".equals(profile.gender)) gender.check(R.id.profileGenderMale);
        else if ("FEMALE".equals(profile.gender)) gender.check(R.id.profileGenderFemale);
        else if ("OTHER".equals(profile.gender)) gender.check(R.id.profileGenderOther);
        suppressChanges = false;
    }

    private void showAvatar(String path) {
        if (path == null || path.isBlank()) {
            avatar.setImageDrawable(null);
            avatar.setVisibility(View.GONE);
            avatarFallback.setVisibility(View.VISIBLE);
            return;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        if (options.outWidth < 1 || options.outHeight < 1) {
            avatar.setVisibility(View.GONE);
            avatarFallback.setVisibility(View.VISIBLE);
            return;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = Math.max(1, Math.min(options.outWidth, options.outHeight) / 256);
        avatar.setImageBitmap(BitmapFactory.decodeFile(path, options));
        avatarFallback.setVisibility(View.GONE);
        avatar.setVisibility(View.VISIBLE);
    }

    private void avatarSelected(Uri uri) {
        if (uri != null) viewModel.selectAvatar(uri);
    }

    private void chooseBirthDate() {
        Calendar selected = Calendar.getInstance();
        if (!birthDateValue.isEmpty()) {
            try {
                LocalDate current = LocalDate.parse(birthDateValue);
                selected.set(current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth());
            } catch (java.time.format.DateTimeParseException ignored) {
                selected = Calendar.getInstance();
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            birthDateValue = LocalDate.of(year, month + 1, day).toString();
            renderBirthDate();
            userEdited = true;
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void renderBirthDate() {
        if (birthDateValue.isEmpty()) {
            birthDate.setText(R.string.select_birth_date);
            return;
        }
        try {
            birthDate.setText(displayDate.format(LocalDate.parse(birthDateValue)));
        } catch (java.time.format.DateTimeParseException error) {
            birthDate.setText(R.string.select_birth_date);
            birthDateValue = "";
        }
    }

    private void saveProfile() {
        String selectedGender = "";
        int checkedId = gender.getCheckedRadioButtonId();
        if (checkedId == R.id.profileGenderMale) selectedGender = "MALE";
        else if (checkedId == R.id.profileGenderFemale) selectedGender = "FEMALE";
        else if (checkedId == R.id.profileGenderOther) selectedGender = "OTHER";

        ProfileModels.UpdateRequest request = new ProfileModels.UpdateRequest(
                fullName.getText().toString(),
                email.getText().toString(),
                phone.getText().toString(),
                birthDateValue,
                selectedGender);
        String error = ProfileValidator.validate(request);
        if (error != null) {
            status.setText(error);
            if (error.startsWith("Họ và tên")) fullName.requestFocus();
            else if (error.startsWith("Email")) email.requestFocus();
            else if (error.startsWith("Số điện thoại")) phone.requestFocus();
            return;
        }
        viewModel.save(request);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_BIRTH_DATE, birthDateValue);
        outState.putBoolean(STATE_USER_EDITED, userEdited);
        outState.putString(STATE_AVATAR_DRAFT, viewModel.pendingAvatarPath());
        super.onSaveInstanceState(outState);
    }
}
