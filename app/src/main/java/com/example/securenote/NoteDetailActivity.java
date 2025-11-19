package com.example.securenote;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.Cipher;

public class NoteDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";
    public static final String EXTRA_PINNED = "extra_pinned";
    // [NEW] รับชื่อไฟล์รูป (ถ้ามี)
    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    private EditText etTitle;
    private EditText etContent;
    private ImageButton btnSave;
    private ImageButton btnPin;
    private ImageButton btnUploadImage;
    private ImageView ivNoteImage;

    private NoteManager manager;
    private String noteId;
    private boolean isPinned = false;

    private Uri selectedImageUri = null; // รูปที่เพิ่งเลือกใหม่
    private String currentImageFile = null; // รูปเดิมที่มีอยู่แล้ว

    // ตัวเปิด Gallery
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri; // เก็บ Uri ไว้รอเซฟ
                    ivNoteImage.setImageURI(uri); // โชว์ตัวอย่าง
                    ivNoteImage.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_note_detail);

        NoteManager.init(this);
        manager = NoteManager.get();

        // Init Views
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnSave = findViewById(R.id.btnSave);
        btnPin = findViewById(R.id.btnPin);
        btnUploadImage = findViewById(R.id.btnUploadImage);
        ivNoteImage = findViewById(R.id.ivNoteImage);
        etTitle = findViewById(R.id.etDetailTitle);
        etContent = findViewById(R.id.etDetailContent);

        // Get Intent Data
        noteId = getIntent().getStringExtra(EXTRA_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String content = getIntent().getStringExtra(EXTRA_CONTENT);
        isPinned = getIntent().getBooleanExtra(EXTRA_PINNED, false);
        currentImageFile = getIntent().getStringExtra(EXTRA_IMAGE_PATH); // รับชื่อไฟล์รูป

        if (title != null) etTitle.setText(title);
        if (content != null) etContent.setText(content);
        updatePinButton();

        // [NEW] โหลดรูปเดิมขึ้นมาโชว์ (ถอดรหัส)
        if (currentImageFile != null) {
            // ลองโหลดเลย (เพราะ User Auth มาแล้วจากหน้า Main)
            Bitmap bmp = ImageCryptoManager.loadEncryptedImage(this, currentImageFile);
            if (bmp != null) {
                ivNoteImage.setImageBitmap(bmp);
                ivNoteImage.setVisibility(View.VISIBLE);
            }
        }

        // Listeners
        btnBack.setOnClickListener(v -> finish());

        btnPin.setOnClickListener(v -> {
            isPinned = !isPinned;
            updatePinButton();
            Toast.makeText(this, isPinned ? "ปักหมุด" : "เลิกปักหมุด", Toast.LENGTH_SHORT).show();
        });

        btnUploadImage.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        btnSave.setOnClickListener(v -> {
            saveNoteWithSecurity();
        });
    }

    private void updatePinButton() {
        if (isPinned) {
            btnPin.setColorFilter(ContextCompat.getColor(this, R.color.ios_accent), PorterDuff.Mode.SRC_IN);
        } else {
            btnPin.setColorFilter(ContextCompat.getColor(this, R.color.text_gray), PorterDuff.Mode.SRC_IN);
        }
    }

    private void saveNoteWithSecurity() {
        String newTitle = etTitle.getText().toString();
        String plainContent = etContent.getText().toString();

        if (newTitle.isEmpty() && plainContent.isEmpty()) return;

        // 1. ขอ Cipher หลักสำหรับ Text
        Cipher textCipher = KeyStoreManager.getEncryptCipher();
        if (textCipher == null) return;

        // 2. ยืนยันตัวตน
        DialogHelper.showAuthDialog(this, textCipher, new DialogHelper.AuthCallback() {
            @Override
            public void onAuthSuccess(Cipher c) {
                try {
                    // --- A. เข้ารหัส Text ---
                    byte[] iv = c.getIV();
                    byte[] encrypted = c.doFinal(plainContent.getBytes(StandardCharsets.UTF_8));
                    String ivStr = Base64.encodeToString(iv, Base64.DEFAULT);
                    String bodyStr = Base64.encodeToString(encrypted, Base64.DEFAULT);
                    String finalContent = ivStr + ":" + bodyStr;

                    // --- B. เข้ารหัส Image (ถ้ามีการเลือกรูปใหม่) ---
                    if (selectedImageUri != null) {
                        // ต้องขอ Cipher ใหม่สำหรับรูป (เพราะใช้ IV ซ้ำไม่ได้)
                        // เนื่องจากอยู่ในช่วง Validity 30s มันจะไม่ถามสแกนนิ้วซ้ำ
                        Cipher imgCipher = KeyStoreManager.getEncryptCipher();
                        if (imgCipher != null) {
                            String newFileName = ImageCryptoManager.saveEncryptedImage(NoteDetailActivity.this, selectedImageUri, imgCipher);
                            if (newFileName != null) {
                                currentImageFile = newFileName; // อัปเดตชื่อไฟล์
                            }
                        }
                    }

                    // --- C. รวมร่าง Text + Image Path ---
                    // Format: "IV:TextContent|ImageFileName"
                    String dataToSave = finalContent;
                    if (currentImageFile != null) {
                        dataToSave = finalContent + "|" + currentImageFile;
                    }

                    // D. Save to DB
                    if (noteId == null) {
                        noteId = UUID.randomUUID().toString();
                        manager.addNote(noteId, newTitle, dataToSave);
                        manager.setPinned(noteId, isPinned);
                    } else {
                        manager.updateNote(noteId, newTitle, dataToSave);
                        manager.setPinned(noteId, isPinned);
                    }

                    Toast.makeText(NoteDetailActivity.this, getString(R.string.msg_save_success), Toast.LENGTH_SHORT).show();
                    finish();

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(NoteDetailActivity.this, "Error saving note", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled() {}
        });
    }
}