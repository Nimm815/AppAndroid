package vn.edu.taskmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersonalProfileActivityTest {
    @Test public void opensValidatesAndKeepsLocallySavedProfileAfterRecreation() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences("personal_profile_local", Context.MODE_PRIVATE);
        Map<String, ?> original = preferences.getAll();
        try (ActivityScenario<PersonalProfileActivity> scenario =
                     ActivityScenario.launch(PersonalProfileActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("Thông tin cá nhân",
                        ((TextView) activity.findViewById(R.id.profileTitle)).getText().toString());
                ((EditText) activity.findViewById(R.id.profileFullName)).setText("Nguyễn An");
                ((EditText) activity.findViewById(R.id.profileEmail)).setText("invalid");
                activity.findViewById(R.id.profileSave).performClick();
                assertTrue(((TextView) activity.findViewById(R.id.profileStatus))
                        .getText().toString().contains("Email"));
                ((EditText) activity.findViewById(R.id.profileEmail)).setText("an@example.com");
                ((EditText) activity.findViewById(R.id.profilePhone)).setText("0912345678");
                activity.findViewById(R.id.profileGenderFemale).performClick();
                activity.findViewById(R.id.profileSave).performClick();
                assertTrue(((TextView) activity.findViewById(R.id.profileStatus))
                        .getText().toString().contains("Chưa đồng bộ MySQL"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("Nguyễn An",
                        ((EditText) activity.findViewById(R.id.profileFullName)).getText().toString());
                assertEquals("an@example.com",
                        ((EditText) activity.findViewById(R.id.profileEmail)).getText().toString());
                assertEquals("0912345678",
                        ((EditText) activity.findViewById(R.id.profilePhone)).getText().toString());
                assertTrue(((android.widget.RadioButton) activity.findViewById(R.id.profileGenderFemale)).isChecked());
            });
        } finally {
            SharedPreferences.Editor editor = preferences.edit().clear();
            for (Map.Entry<String, ?> entry : original.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) editor.putString(entry.getKey(), (String) value);
                else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
                else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            }
            editor.commit();
        }
    }

    @Test public void settingsContainsPersonalInfoEntryAndOpensProfileScreen() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(PersonalProfileActivity.class.getName(), null, false);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectWorkspaceTab(3);
                android.widget.Button edit = activity.findViewById(R.id.settingsProfile);
                assertNotNull(edit);
                assertEquals("Chỉnh sửa ›", edit.getText().toString());
                edit.performClick();
            });
            android.app.Activity profile = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull(profile);
            assertEquals("Thông tin cá nhân",
                    ((TextView) profile.findViewById(R.id.profileTitle)).getText().toString());
            profile.finish();
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }
}
