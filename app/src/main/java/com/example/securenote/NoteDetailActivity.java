package com.example.securenote;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.graphics.PorterDuff;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;

public class NoteDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";
    public static final String EXTRA_PINNED = "extra_pinned";
    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    private EditText etTitle, etContent;
    private ImageView ivNoteImage;
    private ImageButton btnPin;

    private NoteManager.Note currentNote;
    private NoteManager manager;

    private String noteId;
    private boolean isPinned = false;
    private Uri selectedImageUri = null;

    private String originalTitle = "";
    private String originalContent = "";
    private boolean isImageChanged = false;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    ivNoteImage.setImageURI(uri);
                    ivNoteImage.setVisibility(View.VISIBLE);
                    isImageChanged = true;
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_note_detail);

        NoteManager.init(this);
        manager = NoteManager.get();

        etTitle = findViewById(R.id.etDetailTitle);
        etContent = findViewById(R.id.etDetailContent);
        ivNoteImage = findViewById(R.id.ivNoteImage);
        btnPin = findViewById(R.id.btnPin);
        ImageButton btnSave = findViewById(R.id.btnSave);
        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnUpload = findViewById(R.id.btnUploadImage);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExitRequest();
            }
        });

        noteId = getIntent().getStringExtra(EXTRA_ID);
        String passedContent = getIntent().getStringExtra(EXTRA_CONTENT);

        if (noteId != null) {
            List<NoteManager.ListItem> all = manager.getAll();
            for (NoteManager.ListItem item : all) {
                if (item instanceof NoteManager.Note) {
                    NoteManager.Note n = (NoteManager.Note) item;
                    if (n.id.equals(noteId)) {
                        currentNote = n;
                        break;
                    }
                }
            }

            if (currentNote != null) {
                etTitle.setText(currentNote.title);
                originalTitle = currentNote.title;
                isPinned = currentNote.pinned;

                if (passedContent != null) {
                    etContent.setText(passedContent);
                    originalContent = passedContent;

                    String imgPath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
                    if (imgPath != null) {
                        Bitmap legacyBmp = ImageCryptoManager.loadEncryptedImage(this, imgPath);
                        if(legacyBmp != null) {
                            ivNoteImage.setImageBitmap(legacyBmp);
                            ivNoteImage.setVisibility(View.VISIBLE);
                        }
                    }
                } else {
                    decryptNoteContent();
                }
            }
        } else {
            originalTitle = "";
            originalContent = "";
        }

        updatePinButton();

        btnBack.setOnClickListener(v -> handleExitRequest());
        btnUpload.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnPin.setOnClickListener(v -> {
            isPinned = !isPinned;
            updatePinButton();
        });

        btnSave.setOnClickListener(v -> saveNoteWithSecurity());
    }

    private void handleExitRequest() {
        if (hasUnsavedChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_unsaved_title) // ✅
                    .setMessage(R.string.dialog_unsaved_msg) // ✅
                    .setPositiveButton(R.string.btn_discard, (dialog, which) -> finish()) // ✅
                    .setNegativeButton(R.string.btn_keep_editing, null) // ✅
                    .show();
        } else {
            finish();
        }
    }

    private boolean hasUnsavedChanges() {
        String currentTitle = etTitle.getText().toString();
        String currentContent = etContent.getText().toString();
        if (isImageChanged) return true;
        if (!currentTitle.equals(originalTitle)) return true;
        if (!currentContent.equals(originalContent)) return true;
        return false;
    }

    private void decryptNoteContent() {
        if (currentNote == null || currentNote.content == null) return;

        if (currentNote.content.startsWith("FILE:")) {
            String fileName = currentNote.content.substring(5);
            File file = new File(getFilesDir(), fileName);
            if (!file.exists()) return;

            try (FileInputStream fis = new FileInputStream(file)) {
                int ivSize = fis.read();
                if(ivSize <= 0 || ivSize > 256) return;
                byte[] iv = new byte[ivSize];
                fis.read(iv);

                Cipher decryptCipher = KeyStoreManager.getDecryptCipher(iv);

                DialogHelper.showAuthDialog(this, decryptCipher, new DialogHelper.AuthCallback() {
                    @Override
                    public void onAuthSuccess(Cipher cipher) {
                        SecureStorageManager.NoteData data = SecureStorageManager.loadNote(
                                NoteDetailActivity.this, cipher, fileName);

                        if (data != null) {
                            etContent.setText(data.text);
                            originalContent = data.text != null ? data.text : "";

                            if (data.image != null) {
                                ivNoteImage.setImageBitmap(data.image);
                                ivNoteImage.setVisibility(View.VISIBLE);
                            }
                        } else {
                            Toast.makeText(NoteDetailActivity.this, R.string.msg_load_error, Toast.LENGTH_SHORT).show(); // ✅
                        }
                    }
                    @Override
                    public void onCancelled() {
                        finish();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.msg_file_error, Toast.LENGTH_SHORT).show(); // ✅
            }
        }
    }

    private void saveNoteWithSecurity() {
        String title = etTitle.getText().toString();
        String content = etContent.getText().toString();
        if (title.isEmpty() && content.isEmpty() && selectedImageUri == null) return;

        Cipher encryptCipher = KeyStoreManager.getEncryptCipher();

        DialogHelper.showAuthDialog(this, encryptCipher, new DialogHelper.AuthCallback() {
            @Override
            public void onAuthSuccess(Cipher cipher) {
                String fileName = SecureStorageManager.saveNote(
                        NoteDetailActivity.this, cipher, content, selectedImageUri);

                if (fileName != null) {
                    saveToDatabase(title, "FILE:" + fileName);

                    originalTitle = title;
                    originalContent = content;
                    isImageChanged = false;
                    selectedImageUri = null;

                    Toast.makeText(NoteDetailActivity.this, R.string.msg_save_success, Toast.LENGTH_SHORT).show(); // ✅
                } else {
                    Toast.makeText(NoteDetailActivity.this, R.string.msg_save_failed, Toast.LENGTH_SHORT).show(); // ✅
                }
            }
            @Override
            public void onCancelled() {}
        });
    }

    private void saveToDatabase(String title, String contentData) {
        if (noteId == null) {
            noteId = UUID.randomUUID().toString();
            manager.addNote(noteId, title, contentData);
            manager.setPinned(noteId, isPinned);
        } else {
            manager.updateNote(noteId, title, contentData);
            manager.setPinned(noteId, isPinned);
        }
    }

    private void updatePinButton() {
        int color = isPinned ? android.R.color.holo_blue_dark : android.R.color.darker_gray;
        btnPin.setColorFilter(ContextCompat.getColor(this, color), PorterDuff.Mode.SRC_IN);
    }
}