package com.example.securenote;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;

public class MainActivity extends AppCompatActivity {

    private NotesAdapter adapter;
    private NoteManager manager;
    private RecyclerView rvNotes;
    private ImageButton btnAdd;
    private EditText etSearch;
    private TextView tvEmpty;

    // Constants for Internal Logic (ไม่ต้องแปล)
    private static final String SECURITY_BREACH_TAG = "SECURITY BREACH";
    private static final String PREFS_NAME = "notes_prefs";

    private List<NoteManager.ListItem> allNotes = new ArrayList<>();

    private boolean isDeviceRooted() {
        String[] paths = {
                "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
                "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"
        };
        for (String path : paths) {
            if (new java.io.File(path).exists()) return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Security Check: Anti-Screenshot
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // 2. Security Check: Root Detection
        if (isDeviceRooted()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.title_security_risk)
                    .setMessage(R.string.msg_device_rooted)
                    .setCancelable(false)
                    .setPositiveButton(R.string.btn_close_app, (d, w) -> finishAffinity())
                    .show();
            return;
        }

        // 3. Init Hardware Key
        // ถ้า KeyStore มีปัญหา (เช่น เครื่องไม่รองรับ) จะแจ้งเตือน User
        try {
            KeyStoreManager.generateSecretKey();
        } catch (Exception e) {
            Toast.makeText(this, R.string.msg_keystore_error, Toast.LENGTH_LONG).show();
        }

        setContentView(R.layout.activity_main_ios);

        NoteManager.init(this);
        manager = NoteManager.get();

        // UI Bindings
        rvNotes = findViewById(R.id.rvNotes);
        btnAdd = findViewById(R.id.btnAdd);
        etSearch = findViewById(R.id.etSearch);
        // tvEmpty = findViewById(R.id.tvEmpty); // เปิดใช้ถ้ามี View นี้ใน XML

        lockUI();

        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotesAdapter(new NotesAdapter.Listener() {
            @Override
            public void onClick(NoteManager.Note n) {
                openNoteDetail(n);
            }

            @Override
            public void onLongClick(NoteManager.Note n) {
                showEditDeleteDialog(n);
            }
        });
        rvNotes.setAdapter(adapter);

        // Search Logic
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    filterNotes(s.toString());
                }
            });
        }

        btnAdd.setOnClickListener(v -> authenticateAndCreate());

        performAppLock();
    }

    // ✅ ฟังก์ชันชูโรง: Zero Trust Check (เช็คทุกครั้งที่กลับมาหน้า Main)
    @Override
    protected void onResume() {
        super.onResume();

        // เช็คสถานะระเบิด
        try {
            // ลองเรียกใช้ Cipher ดู ถ้า Key พัง (เพราะนิ้วเปลี่ยน) มันจะโยน Exception
            Cipher cipher = KeyStoreManager.getEncryptCipher();
            if (cipher == null) {
                // กรณีเป็น null อาจจะเกิดจาก KeyStore Error ทั่วไป หรือ Key Invalidated
                // แต่ถ้าโค้ด KeyStoreManager ของคุณ throw RuntimeException ออกมา จะเข้า catch ข้างล่าง
            }

        } catch (RuntimeException e) {
            // จับข้อความ "SECURITY BREACH" ที่เราตั้งไว้ใน KeyStoreManager
            if (e.getMessage() != null && e.getMessage().contains(SECURITY_BREACH_TAG)) {
                performSelfDestructSequence(); // 💥 เรียกฟังก์ชันทำลายล้าง
                return; // จบการทำงาน ไม่ต้องโหลด List
            }
        }

        refreshList();
    }

    // 💥 ฟังก์ชันระเบิดแอพ (ลบข้อมูลเกลี้ยง)
    private void performSelfDestructSequence() {
        // 1. ลบไฟล์ทั้งหมดใน Folder ของแอป
        File dir = getFilesDir();
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String i : children) {
                    new File(dir, i).delete();
                }
            }
        }

        // 2. ลบ Database (SharedPreferences)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().commit();

        // 3. แจ้งเตือนครั้งสุดท้าย (ใช้ Resource String แล้ว!)
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_self_destruct) // ✅ แก้ไข: ไม่ Hardcode
                .setMessage(R.string.msg_self_destruct)   // ✅ แก้ไข: ไม่ Hardcode
                .setCancelable(false)
                .setPositiveButton(R.string.btn_bye, (d, w) -> { // ✅ แก้ไข: ไม่ Hardcode
                    finishAffinity(); // ปิดแอป
                    System.exit(0);   // ฆ่า Process
                })
                .show();
    }

    private void lockUI() {
        if (rvNotes != null) rvNotes.setVisibility(View.INVISIBLE);
        if (btnAdd != null) btnAdd.setVisibility(View.INVISIBLE);
        if (etSearch != null) etSearch.setVisibility(View.INVISIBLE);
    }

    private void unlockUI() {
        if (rvNotes != null) rvNotes.setVisibility(View.VISIBLE);
        if (btnAdd != null) btnAdd.setVisibility(View.VISIBLE);
        if (etSearch != null) etSearch.setVisibility(View.VISIBLE);
        refreshList();
    }

    private void performAppLock() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, R.string.msg_bio_unavailable, Toast.LENGTH_SHORT).show();
            unlockUI();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(MainActivity.this, getString(R.string.msg_auth_error_prefix, errString), Toast.LENGTH_SHORT).show();
                        }
                        finishAffinity();
                    }

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        unlockUI();
                        Toast.makeText(MainActivity.this, R.string.msg_unlocked, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle(getString(R.string.bio_subtitle))
                .setNegativeButtonText(getString(R.string.btn_cancel))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void authenticateAndCreate() {
        Intent i = new Intent(MainActivity.this, NoteDetailActivity.class);
        startActivity(i);
    }

    private void openNoteDetail(NoteManager.Note n) {
        if (n.content.startsWith("FILE:")) {
            Intent i = new Intent(MainActivity.this, NoteDetailActivity.class);
            i.putExtra(NoteDetailActivity.EXTRA_ID, n.id);
            startActivity(i);
            return;
        }

        // Legacy Logic (รองรับ Note แบบเก่า)
        try {
            String realContent = n.content;
            String imagePath = null;

            if (n.content.contains("|")) {
                String[] split = n.content.split("\\|", 2);
                realContent = split[0];
                if (split.length > 1 && !split[1].isEmpty()) {
                    imagePath = split[1];
                }
            }

            String[] parts = realContent.split(":");
            if (parts.length != 2) {
                startActivityForPlaintext(n, n.content, null);
                return;
            }

            byte[] iv = Base64.decode(parts[0], Base64.DEFAULT);
            byte[] enc = Base64.decode(parts[1], Base64.DEFAULT);

            Cipher decryptCipher = KeyStoreManager.getDecryptCipher(iv);
            String finalImagePath = imagePath;

            DialogHelper.showAuthDialog(this, decryptCipher, new DialogHelper.AuthCallback() {
                @Override
                public void onAuthSuccess(Cipher c) {
                    try {
                        byte[] decoded = c.doFinal(enc);
                        String plainText = new String(decoded, StandardCharsets.UTF_8);
                        startActivityForPlaintext(n, plainText, finalImagePath);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, R.string.msg_decrypt_failed, Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onCancelled() {}
            });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, R.string.msg_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void startActivityForPlaintext(NoteManager.Note n, String content, String imagePath) {
        Intent i = new Intent(MainActivity.this, NoteDetailActivity.class);
        i.putExtra(NoteDetailActivity.EXTRA_ID, n.id);
        i.putExtra(NoteDetailActivity.EXTRA_TITLE, n.title);
        i.putExtra(NoteDetailActivity.EXTRA_CONTENT, content);
        i.putExtra(NoteDetailActivity.EXTRA_PINNED, n.pinned);
        if (imagePath != null) {
            i.putExtra(NoteDetailActivity.EXTRA_IMAGE_PATH, imagePath);
        }
        startActivity(i);
    }

    private void refreshList() {
        allNotes = manager.getAll();
        if (etSearch != null && etSearch.getText().length() > 0) {
            filterNotes(etSearch.getText().toString());
        } else {
            adapter.setItems(allNotes);
        }
        updateEmptyView();
    }

    private void filterNotes(String query) {
        if (query.isEmpty()) {
            adapter.setItems(allNotes);
        } else {
            List<NoteManager.ListItem> filtered = new ArrayList<>();
            String lowerQuery = query.toLowerCase();
            for (NoteManager.ListItem item : allNotes) {
                if (item instanceof NoteManager.Note) {
                    NoteManager.Note note = (NoteManager.Note) item;
                    if (note.title.toLowerCase().contains(lowerQuery)) {
                        filtered.add(note);
                    }
                }
            }
            adapter.setItems(filtered);
        }
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void showEditDeleteDialog(NoteManager.Note n) {
        String pinAction = n.pinned ? getString(R.string.menu_unpin) : getString(R.string.menu_pin);
        String deleteAction = getString(R.string.btn_delete);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_manage_title)
                .setItems(new CharSequence[]{pinAction, deleteAction}, (dialog, which) -> {
                    if (which == 0) {
                        boolean newPinState = !n.pinned;
                        manager.setPinned(n.id, newPinState);
                        refreshList();
                        String msg = newPinState ? getString(R.string.msg_note_pinned) : getString(R.string.msg_note_unpinned);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(R.string.dialog_confirm_delete)
                                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                                    manager.deleteNote(n.id);
                                    refreshList();
                                })
                                .setNegativeButton(R.string.btn_cancel, null)
                                .show();
                    }
                })
                .show();
    }
}