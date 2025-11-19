package com.example.securenote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

public class ImageCryptoManager {

    // 1. ฟังก์ชันเข้ารหัสรูป: รับ Uri -> เข้ารหัส -> เซฟลงไฟล์ .enc
    public static String saveEncryptedImage(Context context, Uri imageUri, Cipher cipher) {
        String fileName = UUID.randomUUID().toString() + ".enc"; // ตั้งชื่อไฟล์มั่วๆ
        File file = new File(context.getFilesDir(), fileName);

        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
             FileOutputStream fos = new FileOutputStream(file);
             CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {

            // เขียน IV ไปที่หัวไฟล์ก่อน (สำคัญมาก ต้องใช้ตอนถอดรหัส)
            byte[] iv = cipher.getIV();
            fos.write(iv.length); // บอกขนาด IV (1 byte)
            fos.write(iv);        // เขียนตัว IV

            // อ่านไฟล์รูปทีละ 4KB แล้วเข้ารหัสไหลลงไฟล์
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                cos.write(buffer, 0, bytesRead);
            }
            return fileName; // ส่งคืนชื่อไฟล์ไปเก็บใน DB

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 2. ฟังก์ชันถอดรหัสรูป: รับชื่อไฟล์ -> ถอดรหัส -> คืนค่าเป็น Bitmap
    public static Bitmap loadEncryptedImage(Context context, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {

            // อ่าน IV จากหัวไฟล์ออกมาเตรียมไว้
            int ivSize = fis.read();
            byte[] iv = new byte[ivSize];
            fis.read(iv);

            // ขอ Cipher สำหรับถอดรหัส โดยใช้ IV ที่อ่านได้
            // (หมายเหตุ: ตรงนี้ต้องแน่ใจว่า User Auth ผ่านแล้วในช่วง 30 วิ)
            Cipher cipher = KeyStoreManager.getDecryptCipher(iv);
            if (cipher == null) return null;

            // สตรีมถอดรหัส
            try (CipherInputStream cis = new CipherInputStream(fis, cipher)) {
                // แปลง Stream เป็น Bitmap โดยตรง (ไม่ต้องโหลด byte ทั้งหมด)
                return BitmapFactory.decodeStream(cis);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}