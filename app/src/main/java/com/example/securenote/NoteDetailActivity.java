package com.example.securenote;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast; // Import Toast

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.UUID;

public class NoteDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";

    private EditText etTitle;
    private EditText etContent;
    private ImageButton btnSave; // [NEW] เพิ่มตัวแปรปุ่ม Save

    private NoteManager manager;
    private String noteId;
    private String currentPin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        // init manager
        NoteManager.init(this);
        manager = NoteManager.get();

        // views
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnSave = findViewById(R.id.btnSave); // [NEW] เชื่อมปุ่ม
        etTitle = findViewById(R.id.etDetailTitle);
        etContent = findViewById(R.id.etDetailContent);

        // รับข้อมูล
        noteId = getIntent().getStringExtra(EXTRA_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String content = getIntent().getStringExtra(EXTRA_CONTENT);
        currentPin = getIntent().getStringExtra("USER_PIN");

        if (title != null) etTitle.setText(title);
        if (content != null) etContent.setText(content);

        // 1. ปุ่มย้อนกลับ: ทำหน้าที่เหมือนเดิม (Auto save via onPause) หรือจะสั่ง finish เลยก็ได้
        btnBack.setOnClickListener(v -> {
            // saveNoteIfPossible(); // ไม่ต้องเรียกตรงนี้ก็ได้ เพราะ onPause จะทำงานให้อยู่แล้ว
            finish();
        });

        // 2. [NEW] ปุ่ม Save (Explicit Save): ผู้ใช้กดเพื่อยืนยัน
        btnSave.setOnClickListener(v -> {
            saveNoteIfPossible();
            // [Modified] ใช้ getString
            Toast.makeText(this, getString(R.string.msg_save_success), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    // ฟังก์ชันเซฟ (Logic เดิมที่ปรับปรุงแล้ว)
    private void saveNoteIfPossible() {
        String newTitle = etTitle.getText().toString();
        String rawContent = etContent.getText().toString();

        if (newTitle.isEmpty() && rawContent.isEmpty()) return;

        String contentToSave = rawContent;
        if (currentPin != null) {
            String encrypted = SecurityManager.encrypt(currentPin, rawContent);
            if (encrypted != null) {
                contentToSave = encrypted;
            }
        }

        if (noteId == null) {
            noteId = UUID.randomUUID().toString();
            manager.addNote(noteId, newTitle, contentToSave);
        } else {
            manager.updateNote(noteId, newTitle, contentToSave);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // ยังคง Auto-save ไว้เหมือนเดิมเพื่อความปลอดภัย (กันแอปเด้ง หรือลืมกด save)
        saveNoteIfPossible();
    }
}