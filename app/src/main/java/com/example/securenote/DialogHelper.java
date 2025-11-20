package com.example.securenote;

import android.content.Context;
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

    public static void showAuthDialog(FragmentActivity activity, Cipher cipher, AuthCallback callback) {
        if (cipher == null) {
            Toast.makeText(activity, R.string.msg_keystore_error, Toast.LENGTH_SHORT).show(); // ✅
            callback.onCancelled();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    // ✅ Use getString from activity
                    Toast.makeText(activity, activity.getString(R.string.msg_auth_error_prefix, errString), Toast.LENGTH_SHORT).show();
                }
                callback.onCancelled();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
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
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.bio_title)) // ✅
                .setSubtitle(activity.getString(R.string.bio_subtitle)) // ✅
                .setNegativeButtonText(activity.getString(R.string.btn_cancel)) // ✅
                .build();

        biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
    }
}