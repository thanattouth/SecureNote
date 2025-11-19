package com.example.securenote;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyStoreManager {

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "SecureNoteMasterKey";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    // สร้าง Key ฝังลง Hardware (ทำแค่ครั้งเดียวตอนเริ่มแอป)
    public static void generateSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);

            // ถ้ายังไม่มี Key ให้สร้างใหม่
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);

                keyGenerator.init(new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256) // ใช้ AES-256
                        .setUserAuthenticationRequired(true) // บังคับสแกนนิ้ว
                        // ❌ ลบบรรทัดนี้ออก: .setUserAuthenticationValidityDurationSeconds(30)
                        // เพื่อให้ Cipher.init ทำงานได้โดยไม่ต้องสแกนนิ้วล่วงหน้า
                        .setInvalidatedByBiometricEnrollment(true) // ให้คีย์พังถ้านิ้วเปลี่ยน (ปลอดภัยสูง)
                        .build());

                keyGenerator.generateKey();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ขอ Cipher สำหรับเข้ารหัส (Encrypt)
    public static Cipher getEncryptCipher() {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            return cipher;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ขอ Cipher สำหรับถอดรหัส (Decrypt) - ต้องใช้ IV เดิมจากตอนเซฟ
    public static Cipher getDecryptCipher(byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(128, iv));
            return cipher;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }
}