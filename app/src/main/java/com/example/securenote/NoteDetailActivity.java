package com.example.securenote;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.UUID;

public class NoteDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";
    public static final String EXTRA_PINNED = "extra_pinned";

    private EditText etTitle;
    private EditText etContent;
    private ImageButton btnSave;
    private ImageButton btnPin;
    private ImageButton btnUploadImage;
    private ImageView ivNoteImage;

    private NoteManager manager;
    private String noteId;
    private String currentPin;
    private boolean isPinned;
    private Uri selectedImageUri;

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        // init manager
        NoteManager.init(this);
        manager = NoteManager.get();

        // views
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnSave = findViewById(R.id.btnSave);
        btnPin = findViewById(R.id.btnPin);
        btnUploadImage = findViewById(R.id.btnUploadImage);
        etTitle = findViewById(R.id.etDetailTitle);
        etContent = findViewById(R.id.etDetailContent);
        ivNoteImage = findViewById(R.id.ivNoteImage);

        // Get data from intent
        noteId = getIntent().getStringExtra(EXTRA_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String content = getIntent().getStringExtra(EXTRA_CONTENT);
        currentPin = getIntent().getStringExtra("USER_PIN");
        isPinned = getIntent().getBooleanExtra(EXTRA_PINNED, false);

        if (title != null) etTitle.setText(title);
        if (content != null) etContent.setText(content);

        updatePinButton();

        // Initialize the ActivityResultLauncher
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        ivNoteImage.setImageURI(selectedImageUri);
                        ivNoteImage.setVisibility(View.VISIBLE);
                    }
                });

        btnBack.setOnClickListener(v -> {
            finish();
        });

        btnSave.setOnClickListener(v -> {
            saveNoteIfPossible();
            Toast.makeText(this, getString(R.string.msg_save_success), Toast.LENGTH_SHORT).show();
            finish();
        });

        btnPin.setOnClickListener(v -> {
            isPinned = !isPinned;
            if (noteId != null) {
                manager.setPinned(noteId, isPinned);
            }
            updatePinButton();
            String message = isPinned ? "Note pinned" : "Note unpinned";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });

        btnUploadImage.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });
    }

    private void updatePinButton() {
        if (isPinned) {
            btnPin.setColorFilter(ContextCompat.getColor(this, R.color.ios_accent), PorterDuff.Mode.SRC_IN);
        } else {
            btnPin.setColorFilter(ContextCompat.getColor(this, R.color.text_gray), PorterDuff.Mode.SRC_IN);
        }
    }

    private void saveNoteIfPossible() {
        String newTitle = etTitle.getText().toString();
        String rawContent = etContent.getText().toString();

        if (newTitle.isEmpty() && rawContent.isEmpty()) return;

        String contentToSave = rawContent;
        if (currentPin != null) {
            String encrypted = SecurityManager.encrypt(currentPin, rawContent);
            if (encrypted != null) {
                contentToSave = encrypted;
            } else {
                // Encryption failed, abort the save
                return;
            }
        }

        // TODO: Save the selectedImageUri to the NoteManager

        if (noteId == null) {
            noteId = UUID.randomUUID().toString();
            manager.addNote(noteId, newTitle, contentToSave);
            manager.setPinned(noteId, isPinned);
        } else {
            manager.updateNote(noteId, newTitle, contentToSave);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNoteIfPossible();
    }
}
