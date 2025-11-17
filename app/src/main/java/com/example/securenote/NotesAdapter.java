package com.example.securenote;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.VH> {

    public interface Listener {
        void onClick(NoteManager.Note n);
        void onLongClick(NoteManager.Note n);
        // ❌ ลบบรรทัดนี้ออกครับ: void onCreate(Bundle savedInstanceState);
    }

    private final List<NoteManager.Note> items = new ArrayList<>();
    private final Listener listener;

    public NotesAdapter(Listener listener) { this.listener = listener; }

    public void setItems(List<NoteManager.Note> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_ios, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NoteManager.Note n = items.get(position);
        holder.tvTitle.setText(n.title);

        // ตัดคำถ้าเนื้อหายาวเกิน
        String excerpt = n.content.length() > 120 ? n.content.substring(0,120)+"…" : n.content;
        holder.tvExcerpt.setText(excerpt);

        holder.itemView.setOnClickListener(v -> { if (listener!=null) listener.onClick(n); });
        holder.itemView.setOnLongClickListener(v -> { if (listener!=null) listener.onLongClick(n); return true; });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvExcerpt;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNoteTitle);
            tvExcerpt = itemView.findViewById(R.id.tvNoteExcerpt);
        }
    }
}