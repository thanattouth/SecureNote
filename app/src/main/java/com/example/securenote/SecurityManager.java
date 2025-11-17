package com.example.securenote;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class SecurityManager {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    // Salt ควรจะสุ่มและเก็บแยกแต่ละ User แต่เพื่อความง่ายในขั้นนี้เราจะ Hardcode หรือใช้ PackageName ผสม
    private static final String SALT = "SecureNote_Production_Salt_#99";

    // สร้าง Secret Key จาก PIN (Concept: PIN -> Encoder)
    private static SecretKey getKeyFromPin(String pin) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        // Iteration 10,000 รอบเพื่อป้องกัน Brute force
        KeySpec spec = new PBEKeySpec(pin.toCharArray(), SALT.getBytes(), 10000, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    // เข้ารหัส (ใช้ตอน Save)
    public static String encrypt(String pin, String plainText) {
        try {
            SecretKey key = getKeyFromPin(pin);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] iv = cipher.getIV(); // Initialization Vector
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // รวม IV และ Content เข้าด้วยกันแล้วแปลงเป็น String
            String ivString = Base64.encodeToString(iv, Base64.DEFAULT);
            String contentString = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);

            return ivString + ":" + contentString; // Format: IV:Content
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ถอดรหัส (ใช้ตอน Read - ต้องผ่าน Biometric ก่อนถึงจะเรียกฟังก์ชันนี้)
    public static String decrypt(String pin, String encryptedFullString) {
        try {
            String[] parts = encryptedFullString.split(":");
            if (parts.length != 2) return null;

            String ivString = parts[0];
            String contentString = parts[1];

            byte[] iv = Base64.decode(ivString, Base64.DEFAULT);
            byte[] encryptedBytes = Base64.decode(contentString, Base64.DEFAULT);

            SecretKey key = getKeyFromPin(pin);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            byte[] decodedBytes = cipher.doFinal(encryptedBytes);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null; // รหัสผิด หรือไฟล์เสียหาย
        }
    }
}