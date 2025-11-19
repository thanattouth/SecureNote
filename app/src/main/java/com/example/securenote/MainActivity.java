package com.example.securenote;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher; // Import สำคัญ
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
import java.util.Collections;
import java.util.List;
import javax.crypto.Cipher;

public class MainActivity extends AppCompatActivity {

    private NotesAdapter adapter;
    private NoteManager manager;
    private RecyclerView rvNotes;
    private ImageButton btnAdd;
    private EditText etSearch;

    // เก็บรายการทั้งหมดไว้เป็น Master Data เพื่อใช้กรอง
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

        if (isDeviceRooted()) {
            new AlertDialog.Builder(this)
                    .setTitle("Security Risk")
                    .setMessage("อุปกรณ์นี้ผ่านการ Root ไม่ปลอดภัยในการใช้งาน")
                    .setCancelable(false)
                    .setPositiveButton("ปิดแอป", (d, w) -> finishAffinity())
                    .show();
            return; // หยุดทำงาน
        }

        super.onCreate(savedInstanceState);

        // [SECURITY] 1. Anti-Screenshot
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // [SECURITY] 2. Init Hardware Key
        KeyStoreManager.generateSecretKey();

        setContentView(R.layout.activity_main_ios);

        NoteManager.init(this);
        manager = NoteManager.get();

        rvNotes = findViewById(R.id.rvNotes);
        btnAdd = findViewById(R.id.btnAdd);
        etSearch = findViewById(R.id.etSearch);

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

        // [FIXED] เพิ่ม Logic การค้นหา
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterNotes(s.toString());
            }
        });

        btnAdd.setOnClickListener(v -> authenticateAndCreate());

        performAppLock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void lockUI() {
        rvNotes.setVisibility(View.INVISIBLE);
        btnAdd.setVisibility(View.INVISIBLE);
        if (etSearch != null) etSearch.setVisibility(View.INVISIBLE);
    }

    private void unlockUI() {
        rvNotes.setVisibility(View.VISIBLE);
        btnAdd.setVisibility(View.VISIBLE);
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
                    Toast.makeText(MainActivity.this, getString(R.string.msg_login_success), Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onCancelled() {
                    finishAffinity();
                }
            });
        } else {
            // [เพิ่มส่วนนี้] ถ้าสร้าง Key ไม่ได้ ให้แจ้งเตือนและปิดแอป
            Toast.makeText(this, "Error: ไม่พบ KeyStore หรือยังไม่ได้ตั้งค่า Lock Screen/Fingerprint", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void authenticateAndCreate() {
        Cipher cipher = KeyStoreManager.getEncryptCipher();
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

    // ใน MainActivity.java

    private void openNoteDetail(NoteManager.Note n) {
        try {
            // 1. แยก Image Path ออกจาก Content (ถ้ามี)
            String realContent = n.content;
            String imagePath = null;

            if (n.content.contains("|")) {
                String[] split = n.content.split("\\|");
                realContent = split[0]; // ส่วนข้อความ (IV:Text)
                if (split.length > 1) {
                    imagePath = split[1];   // ส่วนชื่อไฟล์รูป
                }
            }

            // 2. เช็ค Format Text (IV:Cipher)
            String[] parts = realContent.split(":");
            if (parts.length != 2) {
                startActivityForPlaintext(n, n.content, null);
                return;
            }

            byte[] iv = Base64.decode(parts[0], Base64.DEFAULT);
            byte[] enc = Base64.decode(parts[1], Base64.DEFAULT);

            Cipher decryptCipher = KeyStoreManager.getDecryptCipher(iv);

            // ตัวแปร final เพื่อใช้ใน lambda
            String finalImagePath = imagePath;

            DialogHelper.showAuthDialog(this, decryptCipher, new DialogHelper.AuthCallback() {
                @Override
                public void onAuthSuccess(Cipher c) {
                    try {
                        byte[] decoded = c.doFinal(enc);
                        String plainText = new String(decoded, StandardCharsets.UTF_8);

                        // ส่ง Text ที่ถอดรหัสแล้ว + Image Path ไปหน้า Detail
                        startActivityForPlaintext(n, plainText, finalImagePath);

                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Decryption Failed", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onCancelled() {}
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ปรับฟังก์ชันนี้ให้รับ imagePath ด้วย
    private void startActivityForPlaintext(NoteManager.Note n, String content, String imagePath) {
        Intent i = new Intent(MainActivity.this, NoteDetailActivity.class);
        i.putExtra(NoteDetailActivity.EXTRA_ID, n.id);
        i.putExtra(NoteDetailActivity.EXTRA_TITLE, n.title);
        i.putExtra(NoteDetailActivity.EXTRA_CONTENT, content);
        i.putExtra(NoteDetailActivity.EXTRA_PINNED, n.pinned);
        // ส่งชื่อไฟล์รูปไปด้วย (ถ้ามี)
        if (imagePath != null) {
            i.putExtra(NoteDetailActivity.EXTRA_IMAGE_PATH, imagePath);
        }
        startActivity(i);
    }

    private void refreshList() {
        allNotes = manager.getAll();

        if (etSearch.getText().length() > 0) {
            filterNotes(etSearch.getText().toString());
        } else {
            adapter.setItems(allNotes);
        }
    }

    // [FIXED] ฟังก์ชันกรองข้อมูล
    private void filterNotes(String query) {
        if (query.isEmpty()) {
            // ถ้าไม่มีคำค้นหา ให้แสดงทั้งหมด
            adapter.setItems(allNotes);
        } else {
            String lowerQuery = query.toLowerCase();

            // [แก้ไข] ใช้ new ArrayList() แทน Collections.emptyList()
            List<NoteManager.ListItem> filtered = new ArrayList<>();

            for (NoteManager.ListItem item : allNotes) {
                // เช็คว่าเป็น Note หรือไม่ (Header ค้นไม่ได้)
                if (item instanceof NoteManager.Note) {
                    NoteManager.Note note = (NoteManager.Note) item;

                    // ค้นหาจาก Title (เพราะ Content เข้ารหัสอยู่)
                    if (note.title.toLowerCase().contains(lowerQuery)) {
                        filtered.add(note);
                    }
                }
            }
            adapter.setItems(filtered);
        }
    }

    private void showEditDeleteDialog(NoteManager.Note n) {
        String pinAction = n.pinned ? "เลิกปักหมุด" : "ปักหมุด";
        CharSequence[] items = { pinAction, "ลบ" };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_manage_title))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) { // Pin/Unpin
                        boolean newPinState = !n.pinned;
                        manager.setPinned(n.id, newPinState);
                        refreshList(); // โหลดรายการใหม่ (มันจะเด้งไปอยู่บนสุดเองเพราะ logic sort เดิม)
                    } else if (which == 1) { // Delete
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(getString(R.string.dialog_confirm_delete))
                                .setPositiveButton(getString(R.string.btn_delete), (d, w) -> {
                                    manager.deleteNote(n.id);
                                    refreshList();
                                })
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    }
                })
                .show();
    }
}