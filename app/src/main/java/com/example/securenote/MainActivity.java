package com.example.securenote;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private NotesAdapter adapter;
    private NoteManager manager;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_ios);

        // init manager
        NoteManager.init(this);
        manager = NoteManager.get();

        // UI
        RecyclerView rv = findViewById(R.id.rvNotes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotesAdapter(new NotesAdapter.Listener() {
            @Override
            public void onClick(NoteManager.Note n) { showViewNote(n); }
            @Override
            public void onLongClick(NoteManager.Note n) { showEditDelete(n); }
        });
        rv.setAdapter(adapter);

        tvEmpty = findViewById(R.id.tvEmpty);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showEditDialog(null));

        refreshList();
    }

    private void refreshList() {
        List<NoteManager.Note> all = manager.getAll();
        adapter.setItems(all);
        tvEmpty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showEditDialog(final NoteManager.Note edit) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_note_ios, null, false);
        EditText etTitle = view.findViewById(R.id.etTitle);
        EditText etContent = view.findViewById(R.id.etContent);
        if (edit != null) {
            etTitle.setText(edit.title);
            etContent.setText(edit.content);
        }
        b.setView(view);
        b.setPositiveButton(getString(R.string.save), (dialog, which) -> {
            String t = etTitle.getText().toString().trim();
            String c = etContent.getText().toString().trim();
            if (TextUtils.isEmpty(t) && TextUtils.isEmpty(c)) {
                Toast.makeText(this, "กรอกข้อมูลก่อนบันทึก", Toast.LENGTH_SHORT).show();
                return;
            }
            if (edit == null) manager.addNote(t, c);
            else manager.updateNote(edit.id, t, c);
            refreshList();
        });
        b.setNegativeButton(getString(R.string.cancel), null);
        b.show();
    }

    private void showViewNote(NoteManager.Note n) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(n.title);
        b.setMessage(n.content);
        b.setPositiveButton("ปิด", null);
        b.show();
    }

    private void showEditDelete(NoteManager.Note n) {
        new AlertDialog.Builder(this)
                .setItems(new CharSequence[]{"แก้ไข", "ลบ"}, (dialog, which) -> {
                    if (which==0) showEditDialog(n);
                    else {
                        new AlertDialog.Builder(this)
                                .setTitle("ยืนยัน")
                                .setMessage("ต้องการลบใช่หรือไม่")
                                .setPositiveButton("ลบ", (d,w) -> { manager.deleteNote(n.id); refreshList(); })
                                .setNegativeButton("ยกเลิก", null)
                                .show();
                    }
                }).show();
    }
}
