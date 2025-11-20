package com.example.securenote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

public class SecureStorageManager {

    public static class NoteData {
        public String text;
        public Bitmap image;
        public NoteData(String text, Bitmap image) {
            this.text = text;
            this.image = image;
        }
    }

    // ✅ SAVE: แก้ไขให้รองรับรูปภาพขนาดใหญ่ (ใช้ writeInt + write แทน writeUTF)
    public static String saveNote(Context context, Cipher cipher, String text, Uri imageUri) {
        String fileName = "secure_" + UUID.randomUUID().toString() + ".dat";
        File file = new File(context.getFilesDir(), fileName);

        try (FileOutputStream fos = new FileOutputStream(file);
             CipherOutputStream cos = new CipherOutputStream(fos, cipher);
             DataOutputStream dos = new DataOutputStream(cos)) {

            // 1. เขียน IV (Init Vector) ไว้ที่หัวไฟล์ (ไม่ได้เข้ารหัส)
            byte[] iv = cipher.getIV();
            fos.write(iv.length);
            fos.write(iv);

            // --- ส่วนที่ถูกเข้ารหัส ---

            // 2. เขียน Text
            // (ถ้า Text ยาวมากๆ เกิน 64KB อาจจะต้องแก้ตรงนี้ด้วย แต่สำหรับ Note ทั่วไป writeUTF พอใช้ได้)
            dos.writeUTF(text != null ? text : "");

            // 3. เขียน Image (แก้ไขใหม่)
            if (imageUri != null) {
                dos.writeBoolean(true); // Flag ว่ามีรูป

                // อ่านไฟล์รูปเป็น byte[]
                try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
                    byte[] imageBytes = getBytes(is);

                    // เขียนขนาดไฟล์ (int) ตามด้วยข้อมูลไฟล์ (bytes)
                    // วิธีนี้รองรับไฟล์ใหญ่ได้ และไม่ต้องแปลงเป็น Base64
                    dos.writeInt(imageBytes.length);
                    dos.write(imageBytes);
                }
            } else {
                dos.writeBoolean(false);
            }

            return fileName;

        } catch (Exception e) {
            e.printStackTrace(); // ดู Logcat ถ้ายัง error: "UTFDataFormatException" หรือ "OOM"
            return null;
        }
    }

    // ✅ LOAD: แก้ไขให้อ่านแบบ byte[] ตามวิธี Save ใหม่
    public static NoteData loadNote(Context context, Cipher cipher, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {

            // 1. ข้าม IV (เพราะเราอ่านไปแล้วใน Activity เพื่อสร้าง Cipher)
            int ivSize = fis.read();
            byte[] tempIv = new byte[ivSize]; // อ่านทิ้งไปเพื่อเลื่อน cursor
            fis.read(tempIv);

            // 2. ถอดรหัส
            try (CipherInputStream cis = new CipherInputStream(fis, cipher);
                 DataInputStream dis = new DataInputStream(cis)) {

                String text = dis.readUTF();
                boolean hasImage = dis.readBoolean();
                Bitmap bitmap = null;

                if (hasImage) {
                    // อ่านขนาดไฟล์รูป
                    int size = dis.readInt();
                    if (size > 0) {
                        byte[] imgBytes = new byte[size];
                        // สำคัญ: ต้องใช้ readFully เพื่อให้แน่ใจว่าอ่านครบทุก byte
                        dis.readFully(imgBytes);
                        // แปลงกลับเป็น Bitmap
                        bitmap = BitmapFactory.decodeByteArray(imgBytes, 0, size);
                    }
                }

                return new NoteData(text, bitmap);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] getBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }
}