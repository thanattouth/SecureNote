package com.example.securenote;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_NOTE = 0;
    private static final int TYPE_HEADER = 1;

    public interface Listener {
        void onClick(NoteManager.Note n);
        void onLongClick(NoteManager.Note n);
    }

    private final List<NoteManager.ListItem> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public NotesAdapter(Listener listener) { this.listener = listener; }

    public void setItems(List<NoteManager.ListItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof NoteManager.Note) {
            return TYPE_NOTE;
        }
        return TYPE_HEADER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_NOTE) {
            View v = inflater.inflate(R.layout.item_note_ios, parent, false);
            return new NoteVH(v);
        }
        View v = inflater.inflate(R.layout.item_header, parent, false);
        return new HeaderVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_NOTE) {
            NoteManager.Note n = (NoteManager.Note) items.get(position);
            NoteVH vh = (NoteVH) holder;

            vh.tvTitle.setText(n.title);

            String excerpt = n.content.length() > 120 ? n.content.substring(0,120) + "…" : n.content;
            vh.tvExcerpt.setText(excerpt);

            if (n.createdAt > 0) {
                vh.tvNoteDate.setText(sdf.format(new Date(n.createdAt)));
            } else {
                vh.tvNoteDate.setText("");
            }

            vh.ivPinIndicator.setVisibility(n.pinned ? View.VISIBLE : View.GONE);

            vh.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(n); });
            vh.itemView.setOnLongClickListener(v -> { if (listener != null) listener.onLongClick(n); return true; });
        } else {
            NoteManager.Header h = (NoteManager.Header) items.get(position);
            HeaderVH vh = (HeaderVH) holder;
            vh.tvHeader.setText(h.title);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    // ViewHolder for Note items
    static class NoteVH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvExcerpt, tvNoteDate;
        ImageView ivPinIndicator;

        NoteVH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNoteTitle);
            tvExcerpt = itemView.findViewById(R.id.tvNoteExcerpt);
            tvNoteDate = itemView.findViewById(R.id.tvNoteDate);
            ivPinIndicator = itemView.findViewById(R.id.ivPinIndicator);
        }
    }

    // ViewHolder for Header items
    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderVH(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvHeader);
        }
    }
}
