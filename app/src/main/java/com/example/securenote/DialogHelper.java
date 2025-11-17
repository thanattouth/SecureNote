package com.example.securenote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class DialogHelper {

    public interface PinCallback {
        void onAuthSuccess(String pin); // เปลี่ยนชื่อให้ชัดเจนว่า Auth ผ่านแล้ว
        void onCancelled();
    }

    public static void showPinDialog(Context ctx, PinCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_pin, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        final EditText p1 = view.findViewById(R.id.pin1);
        final EditText p2 = view.findViewById(R.id.pin2);
        final EditText p3 = view.findViewById(R.id.pin3);
        final EditText p4 = view.findViewById(R.id.pin4);
        Button btnOk = view.findViewById(R.id.btnOk);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        // ensure each field max length = 1 (use InputFilter)
        InputFilter[] oneChar = new InputFilter[] { new InputFilter.LengthFilter(1) };
        p1.setFilters(oneChar); p2.setFilters(oneChar); p3.setFilters(oneChar); p4.setFilters(oneChar);

        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int b, int c) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                // move forward when typed
                if (s.length() == 1) {
                    if (p1.hasFocus()) p2.requestFocus();
                    else if (p2.hasFocus()) p3.requestFocus();
                    else if (p3.hasFocus()) p4.requestFocus();
                }
                // move backward when emptied (user pressed backspace)
                if (s.length() == 0) {
                    if (p4.hasFocus()) p3.requestFocus();
                    else if (p3.hasFocus()) p2.requestFocus();
                    else if (p2.hasFocus()) p1.requestFocus();
                }
            }
        };
        p1.addTextChangedListener(tw);
        p2.addTextChangedListener(tw);
        p3.addTextChangedListener(tw);
        p4.addTextChangedListener(tw);

        // show dialog first then request keyboard
        dialog.show();

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onCancelled();
        });

        btnOk.setOnClickListener(v -> {
            String pin = p1.getText().toString() + p2.getText().toString() + p3.getText().toString() + p4.getText().toString();
            if (pin.length() < 4) {
                // [Modified] ใช้ ctx.getString
                Toast.makeText(ctx, ctx.getString(R.string.msg_pin_length_error), Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();

            if (ctx instanceof FragmentActivity) {
                authenticateBiometric((FragmentActivity) ctx, pin, callback);
            } else {
                callback.onAuthSuccess(pin);
            }
        });
    }

    private static void authenticateBiometric(FragmentActivity activity, String pin, PinCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // [Modified] ใช้ getString with format arguments สำหรับ error message
                String msg = activity.getString(R.string.msg_auth_error_prefix, errString);
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                callback.onCancelled();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                callback.onAuthSuccess(pin);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                // [Modified]
                Toast.makeText(activity, activity.getString(R.string.msg_auth_failed), Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.bio_title))
                .setSubtitle(activity.getString(R.string.bio_subtitle))
                .setNegativeButtonText(activity.getString(R.string.bio_negative))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}
