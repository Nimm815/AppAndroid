package vn.edu.taskmanager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/** Giới thiệu lần đầu; không liên quan đến trạng thái đăng nhập. */
public class OnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_REPLAY = "replay_onboarding";
    private static final String COMPLETED = "onboarding_completed";
    private SharedPreferences preferences;
    private boolean replay;
    private int page;

    private final int[] titles = {R.string.onboarding_home_title, R.string.onboarding_journal_title,
            R.string.onboarding_calendar_title, R.string.onboarding_settings_title};
    private final int[] descriptions = {R.string.onboarding_home_description,
            R.string.onboarding_journal_description, R.string.onboarding_calendar_description,
            R.string.onboarding_settings_description};
    private final int[] tips = {R.string.onboarding_home_tip, R.string.onboarding_journal_tip,
            R.string.onboarding_calendar_tip, R.string.onboarding_settings_tip};
    private final int[] screenshots = {R.drawable.onboarding_home, R.drawable.onboarding_journal,
            R.drawable.onboarding_calendar, R.drawable.onboarding_settings};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("app_preferences", MODE_PRIVATE);
        replay = getIntent().getBooleanExtra(EXTRA_REPLAY, false);
        if (!replay && preferences.getBoolean(COMPLETED, false)) {
            openHome();
            return;
        }
        int theme = getSharedPreferences("workspace_settings", MODE_PRIVATE).getInt("theme", 0);
        int mode = theme == 0 ? AppCompatDelegate.MODE_NIGHT_NO
                : theme == 1 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        getDelegate().setLocalNightMode(mode);
        setContentView(R.layout.activity_onboarding);
        if (state != null) page = Math.max(0, Math.min(titles.length - 1, state.getInt("page", 0)));
        findViewById(R.id.onboardingSkip).setOnClickListener(v -> complete());
        findViewById(R.id.onboardingPrevious).setOnClickListener(v -> {
            if (page > 0) { page--; render(); }
        });
        findViewById(R.id.onboardingNext).setOnClickListener(v -> {
            if (page == titles.length - 1) complete();
            else { page++; render(); }
        });
        render();
    }

    private void render() {
        ((TextView) findViewById(R.id.onboardingTitle)).setText(titles[page]);
        ((TextView) findViewById(R.id.onboardingDescription)).setText(descriptions[page]);
        ((TextView) findViewById(R.id.onboardingTip)).setText(tips[page]);
        ImageView screenshot = findViewById(R.id.onboardingScreenshot);
        screenshot.setImageResource(screenshots[page]);
        screenshot.setContentDescription(getString(R.string.onboarding_screenshot_description,
                getString(titles[page])));
        ((TextView) findViewById(R.id.onboardingProgress)).setText(
                getString(R.string.onboarding_progress, page + 1, titles.length));
        ((Button) findViewById(R.id.onboardingNext)).setText(
                page == titles.length - 1 ? R.string.onboarding_start : R.string.onboarding_next);
        findViewById(R.id.onboardingPrevious).setEnabled(page > 0);
        findViewById(R.id.onboardingScroll).scrollTo(0, 0);
    }

    private void complete() {
        preferences.edit().putBoolean(COMPLETED, true).apply();
        if (replay) finish();
        else openHome();
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }
}
