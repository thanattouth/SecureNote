package com.example.securenote;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
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

    public static void generateSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                createKey();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);

        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true) // บังคับสแกนนิ้ว

                // ✅ [สำคัญ] เพิ่มบรรทัดนี้: อนุญาตให้ใช้ Key ได้ 30 วินาทีหลังสแกน
                // เพื่อให้เรา Save/Load ทั้ง Text และ Image ได้ในการสแกนครั้งเดียว
                .setUserAuthenticationValidityDurationSeconds(60)

                .setInvalidatedByBiometricEnrollment(true)
                .build());

        keyGenerator.generateKey();
    }

    public static Cipher getEncryptCipher() {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            return cipher;
        } catch (KeyPermanentlyInvalidatedException e) {
            // ถ้าคีย์พัง (มีการเพิ่มนิ้วใหม่) ให้สร้างใหม่
            try {
                KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
                keyStore.load(null);
                keyStore.deleteEntry(KEY_ALIAS);
                createKey();
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
                return cipher;
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static Cipher getDecryptCipher(byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(128, iv));
            return cipher;
        } catch (Exception e) {
            return null;
        }
    }

    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }
}