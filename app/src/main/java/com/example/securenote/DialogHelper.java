package com.example.securenote;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;
import javax.crypto.Cipher;

public class DialogHelper {

    public interface AuthCallback {
        void onAuthSuccess(Cipher cipher);
        void onCancelled();
    }

    // ฟังก์ชันเรียก Biometric พร้อม CryptoObject
    public static void showAuthDialog(FragmentActivity activity, Cipher cipher, AuthCallback callback) {
        if (cipher == null) {
            Toast.makeText(activity, "KeyStore Error: Cipher initialization failed.", Toast.LENGTH_SHORT).show();
            callback.onCancelled();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(activity, "Authentication Error: " + errString, Toast.LENGTH_SHORT).show();
                }
                callback.onCancelled();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // *** สำคัญ: รับ Cipher ที่ปลดล็อคจาก Hardware แล้ว ***
                try {
                    Cipher authenticatedCipher = result.getCryptoObject().getCipher();
                    callback.onAuthSuccess(authenticatedCipher);
                } catch (Exception e) {
                    callback.onCancelled();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                // ไม่ต้องทำอะไร ปล่อยให้ระบบจัดการ (สั่นเตือน)
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.bio_title))
                .setSubtitle(activity.getString(R.string.bio_subtitle))
                .setNegativeButtonText(activity.getString(R.string.cancel))
                .build();

        // ส่ง Cipher เข้าไปยืนยัน
        biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
    }
}