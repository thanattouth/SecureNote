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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;

public class MainActivity extends AppCompatActivity {

    private NotesAdapter adapter;
    private NoteManager manager;
    private RecyclerView rvNotes;
    private ImageButton btnAdd;
    private EditText etSearch;
    private TextView tvEmpty; // เพิ่ม reference (ถ้ามีใน xml)

    // เก็บรายการทั้งหมดไว้เป็น Master Data เพื่อใช้กรอง
    private List<NoteManager.ListItem> allNotes = new ArrayList<>();

    // ฟังก์ชันเช็ค Root อย่างง่าย
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
            // [FIXED] ใช้ getString แทนข้อความ Hardcode
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.title_security_risk))
                    .setMessage(getString(R.string.msg_device_rooted))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.btn_close_app), (d, w) -> finishAffinity())
                    .show();
            return; // หยุดการทำงาน
        }

        // 3. Init Hardware Key
        KeyStoreManager.generateSecretKey();

        setContentView(R.layout.activity_main_ios);

        NoteManager.init(this);
        manager = NoteManager.get();

        // UI Bindings
        rvNotes = findViewById(R.id.rvNotes);
        btnAdd = findViewById(R.id.btnAdd);
        etSearch = findViewById(R.id.etSearch);
        // tvEmpty = findViewById(R.id.tvEmpty); // ถ้ามี

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

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
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
        Cipher cipher = KeyStoreManager.getEncryptCipher();
        if (cipher != null) {
            DialogHelper.showAuthDialog(this, cipher, new DialogHelper.AuthCallback() {
                @Override
                public void onAuthSuccess(Cipher c) {
                    unlockUI();
                    // [FIXED] ใช้ Resource string
                    Toast.makeText(MainActivity.this, getString(R.string.msg_login_success), Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onCancelled() {
                    finishAffinity();
                }
            });
        } else {
            // [FIXED] แจ้งเตือน Error แบบดึงจาก strings.xml
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.title_security_error))
                    .setMessage(getString(R.string.msg_keystore_error))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.btn_close_app), (d, w) -> finish())
                    .show();
        }
    }

    private void authenticateAndCreate() {
        Cipher cipher = KeyStoreManager.getEncryptCipher();
        if (cipher == null) return;

        DialogHelper.showAuthDialog(this, cipher, new DialogHelper.AuthCallback() {
            @Override
            public void onAuthSuccess(Cipher c) {
                Intent i = new Intent(MainActivity.this, NoteDetailActivity.class);
                startActivity(i);
            }
            @Override
            public void onCancelled() {}
        });
    }

    private void openNoteDetail(NoteManager.Note n) {
        try {
            String realContent = n.content;
            String imagePath = null;

            int pipeIndex = n.content.lastIndexOf("|");

            if (pipeIndex != -1) {
                // เจอตัวคั่น! แยก Text กับ Image Path ออกจากกัน
                realContent = n.content.substring(0, pipeIndex);
                imagePath = n.content.substring(pipeIndex + 1);

                // Debug: เช็คว่าตัดออกมาได้จริงไหม
                System.out.println("Found Image: " + imagePath);
            }

            if (n.content.contains("|")) {
                String[] split = n.content.split("\\|");
                realContent = split[0];
                if (split.length > 1) imagePath = split[1];
            }

            String[] parts = realContent.split(":");
            if (parts.length != 2) {
                // Fallback for plain text / legacy notes
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
                        // [FIXED]
                        Toast.makeText(MainActivity.this, getString(R.string.msg_decrypt_failed), Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onCancelled() {}
            });

        } catch (Exception e) {
            e.printStackTrace();
            // [FIXED]
            Toast.makeText(MainActivity.this, getString(R.string.msg_open_error), Toast.LENGTH_SHORT).show();
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
        updateEmptyView(); // ต้องอัปเดต view ว่างด้วยถ้าค้นแล้วไม่เจอ
    }

    private void updateEmptyView() {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void showEditDeleteDialog(NoteManager.Note n) {
        // [FIXED] ใช้ getString ทั้งหมด
        String pinAction = n.pinned ? getString(R.string.menu_unpin) : getString(R.string.menu_pin);
        String deleteAction = getString(R.string.btn_delete);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_manage_title))
                .setItems(new CharSequence[]{pinAction, deleteAction}, (dialog, which) -> {
                    if (which == 0) { // Pin
                        boolean newPinState = !n.pinned;
                        manager.setPinned(n.id, newPinState);
                        refreshList();
                        // [FIXED] Toast message
                        String msg = newPinState ? getString(R.string.menu_pin) : getString(R.string.menu_unpin);
                        // (ปรับ: อาจจะทำ string แยก msg_pinned/unpinned ก็ได้ แต่ใช้เมนูแก้ขัดไปก่อนก็เข้าใจได้)
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();

                    } else if (which == 1) { // Delete
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(getString(R.string.dialog_confirm_delete))
                                .setPositiveButton(getString(R.string.btn_delete), (d, w) -> {
                                    manager.deleteNote(n.id);
                                    refreshList();
                                })
                                .setNegativeButton(getString(R.string.btn_cancel), null)
                                .show();
                    }
                })
                .show();
    }
}