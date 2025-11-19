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

    // [MODIFIED] The list now holds both Notes and Headers
    private List<NoteManager.ListItem> allNotes = new ArrayList<>();

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
        i.putExtra(NoteDetailActivity.EXTRA_PINNED, n.pinned);
        i.putExtra("USER_PIN", currentSessionPin);
        startActivity(i);
    }

    private void refreshList() {
        allNotes = manager.getAll(); // This now returns List<ListItem>

        if (etSearch != null && etSearch.getText().length() > 0) {
            filterNotes(etSearch.getText().toString());
        } else {
            adapter.setItems(allNotes);
        }

        updateEmptyView();
    }

    // [MODIFIED] This method now handles a list of ListItems
    private void filterNotes(String query) {
        if (query.isEmpty()) {
            adapter.setItems(allNotes);
        } else {
            List<NoteManager.ListItem> filtered = new ArrayList<>();
            String lowerQuery = query.toLowerCase();
            for (NoteManager.ListItem item : allNotes) {
                // Only filter the Note items, ignore headers
                if (item instanceof NoteManager.Note) {
                    NoteManager.Note n = (NoteManager.Note) item;
                    if (n.title.toLowerCase().contains(lowerQuery)) {
                        filtered.add(n);
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
        String pinAction = n.pinned ? "Unpin" : "Pin";
        CharSequence[] items = { pinAction, "Delete" };

        new AlertDialog.Builder(this)
                .setTitle("Manage Note")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) { // Pin/Unpin
                        boolean newPinState = !n.pinned;
                        manager.setPinned(n.id, newPinState);
                        refreshList();
                        String message = newPinState ? "Note pinned" : "Note unpinned";
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    } else if (which == 1) { // Delete
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage("Are you sure you want to delete this note?")
                                .setPositiveButton("Delete", (d, w) -> {
                                    manager.deleteNote(n.id);
                                    refreshList();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }
}
