package com.example.securenote;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NoteManager {
    private static final String PREFS = "notes_prefs";
    private static final String KEY_NOTES = "notes_v1";
    private final SharedPreferences prefs;
    private static NoteManager sInstance;

    private NoteManager(@NonNull Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureArray();
    }

    public static synchronized NoteManager init(@NonNull Context ctx) {
        if (sInstance == null) sInstance = new NoteManager(ctx);
        return sInstance;
    }
    public static NoteManager get() {
        if (sInstance == null) throw new IllegalStateException("Call init(context) first");
        return sInstance;
    }

    private void ensureArray() {
        if (!prefs.contains(KEY_NOTES)) prefs.edit().putString(KEY_NOTES, new JSONArray().toString()).apply();
    }

    private JSONArray getArray() {
        String raw = prefs.getString(KEY_NOTES, null);
        if (TextUtils.isEmpty(raw)) return new JSONArray();
        try { return new JSONArray(raw); }
        catch (JSONException e) {
            JSONArray arr = new JSONArray();
            prefs.edit().putString(KEY_NOTES, arr.toString()).apply();
            return arr;
        }
    }

    private void saveArray(JSONArray arr) {
        prefs.edit().putString(KEY_NOTES, arr.toString()).apply();
    }

    public static class Note {
        public final String id;
        public String title;
        public String content;
        public long createdAt;
        public long updatedAt;
        public boolean pinned;   // ✅ เพิ่ม

        public Note(String id, String title, String content,
                    long createdAt, long updatedAt, boolean pinned) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.pinned = pinned;
        }
    }

    @NonNull
    public Note addNote(@NonNull String title, @NonNull String content) {
        JSONArray arr = getArray();
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title);
            o.put("content", content);
            o.put("createdAt", now);
            o.put("updatedAt", now);
            o.put("pinned", false);           // ✅ โน้ตใหม่ยังไม่ถูกปักหมุด
            arr.put(o);
            saveArray(arr);
        } catch (JSONException e) { /* ignore */ }
        return new Note(id, title, content, now, now, false);
    }

    public boolean updateNote(@NonNull String id, @NonNull String title, @NonNull String content) {
        JSONArray arr = getArray();
        boolean changed = false;
        for (int i=0;i<arr.length();i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(o.optString("id"))) {
                    o.put("title", title);
                    o.put("content", content);
                    o.put("updatedAt", System.currentTimeMillis());
                    arr.put(i, o);
                    changed = true;
                    break;
                }
            } catch (JSONException e) { /* ignore */ }
        }
        if (changed) saveArray(arr);
        return changed;
    }

    public void addNote(String id, String title, String content) {
        JSONArray arr = getArray();
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title);
            o.put("content", content);
            o.put("createdAt", System.currentTimeMillis());
            o.put("updatedAt", System.currentTimeMillis());

            // เพิ่มลงใน Array
            arr.put(o);
            saveArray(arr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean setPinned(@NonNull String id, boolean pinned) {
        JSONArray arr = getArray();
        boolean changed = false;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(o.optString("id"))) {
                    o.put("pinned", pinned);
                    o.put("updatedAt", System.currentTimeMillis());
                    arr.put(i, o);
                    changed = true;
                    break;
                }
            } catch (JSONException e) { /* ignore */ }
        }
        if (changed) saveArray(arr);
        return changed;
    }

    public boolean deleteNote(@NonNull String id) {
        JSONArray arr = getArray();
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i=0;i<arr.length();i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(o.optString("id"))) removed = true;
                else next.put(o);
            } catch (JSONException e) {}
        }
        if (removed) saveArray(next);
        return removed;
    }

    @NonNull
    public List<Note> getAll() {
        JSONArray arr = getArray();
        List<Note> out = new ArrayList<>();
        for (int i=0;i<arr.length();i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                boolean pinned = o.optBoolean("pinned", false);  // ✅ เผื่อโน้ตเก่าไม่มีฟิลด์นี้
                out.add(new Note(
                        o.optString("id"),
                        o.optString("title"),
                        o.optString("content"),
                        o.optLong("createdAt",0),
                        o.optLong("updatedAt", o.optLong("createdAt",0)),
                        pinned
                ));
            } catch (JSONException e) {}
        }
        return out;
    }
}
