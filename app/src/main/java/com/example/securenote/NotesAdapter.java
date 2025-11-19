package com.example.securenote;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.VH> {

    public interface Listener {
        void onClick(NoteManager.Note n);
        void onLongClick(NoteManager.Note n);
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

        String excerpt = n.content.length() > 120 ? n.content.substring(0,120) + "…" : n.content;
        holder.tvExcerpt.setText(excerpt);

        if (n.createdAt > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            holder.tvNoteDate.setText(sdf.format(new Date(n.createdAt)));
        } else {
            holder.tvNoteDate.setText("");
        }

        holder.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(n); });
        holder.itemView.setOnLongClickListener(v -> { if (listener != null) listener.onLongClick(n); return true; });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvExcerpt, tvNoteDate;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNoteTitle);
            tvExcerpt = itemView.findViewById(R.id.tvNoteExcerpt);
            tvNoteDate = itemView.findViewById(R.id.tvNoteDate);
        }
    }
}
