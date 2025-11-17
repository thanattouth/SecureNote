package com.example.securenote;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher; // Import เพิ่ม
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private NotesAdapter adapter;
    private NoteManager manager;

    // UI References
    private RecyclerView rvNotes;
    private ImageButton btnAdd;
    private TextView tvEmpty;
    private View searchContainer;
    private EditText etSearch;

    private String currentSessionPin = null;

    // [NEW] ตัวแปรเก็บโน้ตทั้งหมดไว้กรอง
    private List<NoteManager.Note> allNotes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_ios);

        NoteManager.init(this);
        manager = NoteManager.get();

        // Bind Views
        rvNotes = findViewById(R.id.rvNotes);
        btnAdd = findViewById(R.id.btnAdd);
        etSearch = findViewById(R.id.etSearch);

        // หา Parent ของ Search เพื่อซ่อน
        if (etSearch != null && etSearch.getParent() instanceof View) {
            searchContainer = (View) etSearch.getParent();
        }
        // ถ้ามี tvEmpty ให้ uncomment
        // tvEmpty = findViewById(R.id.tvEmpty);

        // Security: ซ่อนก่อน
        lockUI();

        // Setup RecyclerView
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

        // [NEW] Setup Search Logic
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    // เมื่อพิมพ์ข้อความ ให้เรียกฟังก์ชันกรอง
                    filterNotes(s.toString());
                }
            });
        }

        btnAdd.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, NoteDetailActivity.class);
            i.putExtra("USER_PIN", currentSessionPin);
            startActivity(i);
        });

        performAppLock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentSessionPin != null) {
            refreshList();
        }
    }

    private void lockUI() {
        rvNotes.setVisibility(View.INVISIBLE);
        btnAdd.setVisibility(View.INVISIBLE);
        if (searchContainer != null) searchContainer.setVisibility(View.INVISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
    }

    private void unlockUI() {
        rvNotes.setVisibility(View.VISIBLE);
        btnAdd.setVisibility(View.VISIBLE);
        if (searchContainer != null) searchContainer.setVisibility(View.VISIBLE);
        refreshList();
    }

    private void performAppLock() {
        DialogHelper.showPinDialog(this, new DialogHelper.PinCallback() {
            @Override
            public void onAuthSuccess(String pin) {
                currentSessionPin = pin;
                unlockUI();
                // [Modified] ใช้ getString
                Toast.makeText(MainActivity.this, getString(R.string.msg_login_success), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled() {
                finishAffinity();
            }
        });
    }

    private void openNoteDetail(NoteManager.Note n) {
        String decryptedContent = SecurityManager.decrypt(currentSessionPin, n.content);
        Intent i = new Intent(MainActivity.this, NoteDetailActivity.class);
        i.putExtra(NoteDetailActivity.EXTRA_ID, n.id);
        i.putExtra(NoteDetailActivity.EXTRA_TITLE, n.title);
        i.putExtra(NoteDetailActivity.EXTRA_CONTENT, decryptedContent != null ? decryptedContent : n.content);
        i.putExtra("USER_PIN", currentSessionPin);
        startActivity(i);
    }

    // [Modified] โหลดข้อมูลแล้วเก็บเข้า allNotes ด้วย
    private void refreshList() {
        allNotes = manager.getAll(); // เก็บตัวแม่

        // ถ้ามีคำค้นหาค้างอยู่ ให้กรองตามคำค้นหานั้น (เผื่อกลับมาจากหน้าอื่นแล้วยังพิมพ์ค้างไว้)
        if (etSearch != null && etSearch.getText().length() > 0) {
            filterNotes(etSearch.getText().toString());
        } else {
            // ถ้าไม่มีคำค้น ก็โชว์ทั้งหมด
            adapter.setItems(allNotes);
        }

        updateEmptyView();
    }

    // [NEW] ฟังก์ชันกรองโน้ต
    private void filterNotes(String query) {
        if (query.isEmpty()) {
            adapter.setItems(allNotes);
        } else {
            List<NoteManager.Note> filtered = new ArrayList<>();
            String lowerQuery = query.toLowerCase();
            for (NoteManager.Note n : allNotes) {
                // ค้นหาจาก Title เท่านั้น (เพราะ Content เข้ารหัสอยู่)
                if (n.title.toLowerCase().contains(lowerQuery)) {
                    filtered.add(n);
                }
            }
            adapter.setItems(filtered);
        }
        // อัปเดต empty view ถ้าค้นแล้วไม่เจอ
        // updateEmptyView(); // (optional)
    }

    private void updateEmptyView() {
        if (tvEmpty != null) {
            // เช็คจาก adapter ว่าตอนนี้โชว์กี่ตัว
            tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void showEditDeleteDialog(NoteManager.Note n) {
        // [Modified] ใช้ getString ทั้งหมด
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_manage_title))
                .setItems(new CharSequence[]{getString(R.string.btn_delete)}, (dialog, which) -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage(getString(R.string.dialog_confirm_delete))
                            .setPositiveButton(getString(R.string.btn_delete), (d, w) -> {
                                manager.deleteNote(n.id);
                                refreshList();
                            })
                            .setNegativeButton(getString(R.string.btn_cancel), null)
                            .show();
                })
                .show();
    }
}