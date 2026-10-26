
package vn.edu.taskmanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import vn.edu.taskmanager.auth.AccountSession;
import vn.edu.taskmanager.auth.AuthApi;

public class AuthActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "auth_mode";

    private boolean registering;

    private EditText fullName, email, password, confirmPassword;
    private View nameBox, confirmBox, progress;
    private TextView subtitle, switchMode;
    private Button submit;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_auth);

        registering = "register".equals(
                getIntent().getStringExtra(EXTRA_MODE)
        );

        fullName = findViewById(R.id.authFullName);
        email = findViewById(R.id.authEmail);
        password = findViewById(R.id.authPassword);
        confirmPassword = findViewById(R.id.authConfirmPassword);

        nameBox = findViewById(R.id.registerNameBox);
        confirmBox = findViewById(R.id.registerConfirmBox);
        subtitle = findViewById(R.id.authSubtitle);
        switchMode = findViewById(R.id.authSwitch);
        submit = findViewById(R.id.authSubmit);
        progress = findViewById(R.id.authProgress);

        renderMode();

        submit.setOnClickListener(v -> submit());

        switchMode.setOnClickListener(v -> {
            registering = !registering;
            renderMode();
        });

        findViewById(R.id.authCancel).setOnClickListener(v -> finish());
    }

    private void renderMode() {
        nameBox.setVisibility(registering ? View.VISIBLE : View.GONE);
        confirmBox.setVisibility(registering ? View.VISIBLE : View.GONE);

        subtitle.setText(registering
                ? "Tạo tài khoản TaskMind của bạn"
                : "Đăng nhập để tiếp tục sử dụng TaskMind");

        submit.setText(registering ? "Tạo tài khoản" : "Đăng nhập");

        switchMode.setText(registering
                ? "Đã có tài khoản? Đăng nhập"
                : "Chưa có tài khoản? Đăng ký");
    }

    private void submit() {
        String mail = email.getText().toString()
                .trim()
                .toLowerCase(java.util.Locale.ROOT);

        String pass = password.getText().toString();

        if (registering &&
                TextUtils.isEmpty(fullName.getText().toString().trim())) {
            fullName.setError("Vui lòng nhập họ tên");
            fullName.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            email.setError("Email không hợp lệ");
            email.requestFocus();
            return;
        }

        if (pass.length() < 8) {
            password.setError("Mật khẩu cần ít nhất 8 ký tự");
            password.requestFocus();
            return;
        }

        if (registering &&
                !pass.equals(confirmPassword.getText().toString())) {
            confirmPassword.setError("Mật khẩu xác nhận không khớp");
            confirmPassword.requestFocus();
            return;
        }

        setLoading(true);

        if (registering) {
            AuthApi.register(
                    fullName.getText().toString().trim(),
                    mail,
                    pass,
                    (ok, data, message) -> {
                        setLoading(false);

                        if (ok) {
                            Toast.makeText(
                                    this,
                                    "Đăng ký thành công. Hãy đăng nhập.",
                                    Toast.LENGTH_LONG
                            ).show();

                            registering = false;
                            renderMode();
                            password.setText("");
                            confirmPassword.setText("");
                        } else {
                            Toast.makeText(
                                    this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );
        } else {
            AuthApi.login(mail, pass, (ok, data, message) -> {
                setLoading(false);

                if (ok && data != null &&
                        !data.optString("token").isEmpty()) {

                    AccountSession.save(this, data);

                    Toast.makeText(
                            this,
                            "Xin chào " + AccountSession.fullName(this),
                            Toast.LENGTH_SHORT
                    ).show();

                    Intent result = new Intent();
                    result.putExtra("logged_in", true);
                    setResult(RESULT_OK, result);
                    finish();

                } else {
                    Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        submit.setEnabled(!loading);
        switchMode.setEnabled(!loading);
    }
}
