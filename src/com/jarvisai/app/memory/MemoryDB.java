package com.jarvisai.app.memory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local SQLite store: memories, conversations, reminders, tasks, notes. */
public class MemoryDB extends SQLiteOpenHelper {

    private static final String NAME = "jarvis.db";
    private static final int VERSION = 1;

    public MemoryDB(Context c) {
        super(c.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "mkey TEXT," +
                "mvalue TEXT NOT NULL," +
                "category TEXT," +
                "importance INTEGER DEFAULT 2," +
                "created INTEGER," +
                "updated INTEGER)");
        db.execSQL("CREATE INDEX idx_mem_cat ON memories(category)");
        db.execSQL("CREATE TABLE conversations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "started INTEGER," +
                "updated INTEGER)");
        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "conv INTEGER," +
                "role TEXT," +
                "body TEXT," +
                "ts INTEGER," +
                "meta TEXT)");
        db.execSQL("CREATE INDEX idx_msg_conv ON messages(conv)");
        db.execSQL("CREATE TABLE reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "body TEXT," +
                "trigger_at INTEGER," +
                "request_code INTEGER," +
                "done INTEGER DEFAULT 0," +
                "repeat_rule TEXT," +
                "created INTEGER)");
        db.execSQL("CREATE TABLE tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "done INTEGER DEFAULT 0," +
                "due INTEGER," +
                "priority INTEGER DEFAULT 1," +
                "created INTEGER)");
        db.execSQL("CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "body TEXT," +
                "created INTEGER," +
                "updated INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Forward-compatible: keep user data, add new tables when the schema grows.
    }

    // ---- Models ---------------------------------------------------------

    public static class Memory {
        public int id;
        public String key, value, category;
        public int importance;
        public long created, updated;

        public String display() {
            if (key != null && !key.isEmpty()) return key + ": " + value;
            return value;
        }
    }

    public static class Message {
        public int id, conv;
        public String role, text, meta;
        public long ts;
    }

    public static class Reminder {
        public int id, requestCode;
        public String text, repeat;
        public long triggerAt, created;
        public boolean done;
    }

    public static class Task {
        public int id, priority;
        public String title;
        public boolean done;
        public long due, created;
    }

    public static class Note {
        public int id;
        public String title, body;
        public long created, updated;
    }

    public static class Conversation {
        public int id, count;
        public String title;
        public long started, updated;
    }

    // ---- Memories -------------------------------------------------------

    public synchronized int saveMemory(String key, String value, String category, int importance) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        if (key != null && !key.trim().isEmpty()) {
            Cursor c = db.rawQuery("SELECT id FROM memories WHERE mkey=? COLLATE NOCASE LIMIT 1", new String[]{key.trim()});
            int existing = -1;
            if (c.moveToFirst()) existing = c.getInt(0);
            c.close();
            ContentValues v = new ContentValues();
            v.put("mkey", key.trim());
            v.put("mvalue", value);
            v.put("category", category == null ? "general" : category);
            v.put("importance", Math.max(1, Math.min(5, importance)));
            v.put("updated", now);
            if (existing > 0) {
                db.update("memories", v, "id=?", new String[]{String.valueOf(existing)});
                return existing;
            }
            v.put("created", now);
            return (int) db.insert("memories", null, v);
        }
        ContentValues v = new ContentValues();
        v.put("mkey", "");
        v.put("mvalue", value);
        v.put("category", category == null ? "general" : category);
        v.put("importance", Math.max(1, Math.min(5, importance)));
        v.put("created", now);
        v.put("updated", now);
        return (int) db.insert("memories", null, v);
    }

    public synchronized List<Memory> memories() {
        return queryMemories("SELECT * FROM memories ORDER BY importance DESC, updated DESC", null);
    }

    public synchronized List<Memory> memories(String category) {
        return queryMemories("SELECT * FROM memories WHERE category=? ORDER BY updated DESC", new String[]{category});
    }

    private List<Memory> queryMemories(String sql, String[] args) {
        List<Memory> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            Memory m = new Memory();
            m.id = c.getInt(c.getColumnIndexOrThrow("id"));
            m.key = c.getString(c.getColumnIndexOrThrow("mkey"));
            m.value = c.getString(c.getColumnIndexOrThrow("mvalue"));
            m.category = c.getString(c.getColumnIndexOrThrow("category"));
            m.importance = c.getInt(c.getColumnIndexOrThrow("importance"));
            m.created = c.getLong(c.getColumnIndexOrThrow("created"));
            m.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
            out.add(m);
        }
        c.close();
        return out;
    }

    public synchronized void deleteMemory(int id) {
        getWritableDatabase().delete("memories", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearMemories() {
        getWritableDatabase().delete("memories", null, null);
    }

    public synchronized int memoryCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memories", null);
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    // ---- Conversations / messages --------------------------------------

    public synchronized int ensureTodayConversation() {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();
        Cursor c = db.rawQuery("SELECT id FROM conversations WHERE started>=? ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(dayStart)});
        if (c.moveToFirst()) {
            int id = c.getInt(0);
            c.close();
            return id;
        }
        c.close();
        ContentValues v = new ContentValues();
        v.put("title", new java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault())
                .format(new java.util.Date(now)));
        v.put("started", now);
        v.put("updated", now);
        return (int) db.insert("conversations", null, v);
    }

    public synchronized int addMessage(int conv, String role, String text, String meta) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("conv", conv);
        v.put("role", role);
        v.put("body", text);
        v.put("ts", now);
        v.put("meta", meta == null ? "" : meta);
        int id = (int) db.insert("messages", null, v);
        ContentValues u = new ContentValues();
        u.put("updated", now);
        db.update("conversations", u, "id=?", new String[]{String.valueOf(conv)});
        return id;
    }

    public synchronized List<Message> messages(int conv, int limit) {
        List<Message> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM (SELECT * FROM messages WHERE conv=? ORDER BY id DESC LIMIT ?) ORDER BY id ASC",
                new String[]{String.valueOf(conv), String.valueOf(limit)});
        while (c.moveToNext()) {
            Message m = new Message();
            m.id = c.getInt(c.getColumnIndexOrThrow("id"));
            m.conv = c.getInt(c.getColumnIndexOrThrow("conv"));
            m.role = c.getString(c.getColumnIndexOrThrow("role"));
            m.text = c.getString(c.getColumnIndexOrThrow("body"));
            m.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
            m.meta = c.getString(c.getColumnIndexOrThrow("meta"));
            out.add(m);
        }
        c.close();
        return out;
    }

    public synchronized List<Message> recentMessages(int limit) {
        List<Message> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM (SELECT * FROM messages ORDER BY id DESC LIMIT ?) ORDER BY id ASC",
                new String[]{String.valueOf(limit)});
        while (c.moveToNext()) {
            Message m = new Message();
            m.id = c.getInt(c.getColumnIndexOrThrow("id"));
            m.conv = c.getInt(c.getColumnIndexOrThrow("conv"));
            m.role = c.getString(c.getColumnIndexOrThrow("role"));
            m.text = c.getString(c.getColumnIndexOrThrow("body"));
            m.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
            m.meta = c.getString(c.getColumnIndexOrThrow("meta"));
            out.add(m);
        }
        c.close();
        return out;
    }

    public synchronized List<Conversation> conversations() {
        List<Conversation> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT c.id,c.title,c.started,c.updated,(SELECT COUNT(*) FROM messages m WHERE m.conv=c.id) AS n " +
                        "FROM conversations c ORDER BY c.updated DESC", null);
        while (c.moveToNext()) {
            Conversation x = new Conversation();
            x.id = c.getInt(0);
            x.title = c.getString(1);
            x.started = c.getLong(2);
            x.updated = c.getLong(3);
            x.count = c.getInt(4);
            out.add(x);
        }
        c.close();
        return out;
    }

    public synchronized void deleteConversation(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "conv=?", new String[]{String.valueOf(id)});
        db.delete("conversations", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearMessages() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", null, null);
        db.delete("conversations", null, null);
    }

    public synchronized int messageCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages", null);
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    // ---- Reminders ------------------------------------------------------

    public synchronized int addReminder(String text, long triggerAt, int requestCode, String repeat) {
        ContentValues v = new ContentValues();
        v.put("body", text);
        v.put("trigger_at", triggerAt);
        v.put("request_code", requestCode);
        v.put("done", 0);
        v.put("repeat_rule", repeat == null ? "" : repeat);
        v.put("created", System.currentTimeMillis());
        return (int) getWritableDatabase().insert("reminders", null, v);
    }

    public synchronized List<Reminder> reminders(boolean includeDone) {
        List<Reminder> out = new ArrayList<>();
        String sql = "SELECT * FROM reminders" + (includeDone ? "" : " WHERE done=0") + " ORDER BY trigger_at ASC";
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        while (c.moveToNext()) {
            Reminder r = new Reminder();
            r.id = c.getInt(c.getColumnIndexOrThrow("id"));
            r.text = c.getString(c.getColumnIndexOrThrow("body"));
            r.triggerAt = c.getLong(c.getColumnIndexOrThrow("trigger_at"));
            r.requestCode = c.getInt(c.getColumnIndexOrThrow("request_code"));
            r.done = c.getInt(c.getColumnIndexOrThrow("done")) == 1;
            r.repeat = c.getString(c.getColumnIndexOrThrow("repeat_rule"));
            r.created = c.getLong(c.getColumnIndexOrThrow("created"));
            out.add(r);
        }
        c.close();
        return out;
    }

    public synchronized Reminder reminder(int id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM reminders WHERE id=?", new String[]{String.valueOf(id)});
        Reminder r = null;
        if (c.moveToFirst()) {
            r = new Reminder();
            r.id = c.getInt(c.getColumnIndexOrThrow("id"));
            r.text = c.getString(c.getColumnIndexOrThrow("body"));
            r.triggerAt = c.getLong(c.getColumnIndexOrThrow("trigger_at"));
            r.requestCode = c.getInt(c.getColumnIndexOrThrow("request_code"));
            r.done = c.getInt(c.getColumnIndexOrThrow("done")) == 1;
            r.repeat = c.getString(c.getColumnIndexOrThrow("repeat_rule"));
            r.created = c.getLong(c.getColumnIndexOrThrow("created"));
        }
        c.close();
        return r;
    }

    public synchronized void markReminderDone(int id) {
        ContentValues v = new ContentValues();
        v.put("done", 1);
        getWritableDatabase().update("reminders", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void updateReminderTrigger(int id, long triggerAt) {
        ContentValues v = new ContentValues();
        v.put("trigger_at", triggerAt);
        v.put("done", 0);
        getWritableDatabase().update("reminders", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void deleteReminder(int id) {
        getWritableDatabase().delete("reminders", "id=?", new String[]{String.valueOf(id)});
    }

    // ---- Tasks ----------------------------------------------------------

    public synchronized int addTask(String title, long due, int priority) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("done", 0);
        v.put("due", due);
        v.put("priority", priority);
        v.put("created", System.currentTimeMillis());
        return (int) getWritableDatabase().insert("tasks", null, v);
    }

    public synchronized List<Task> tasks(boolean includeDone) {
        List<Task> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM tasks" + (includeDone ? "" : " WHERE done=0") +
                        " ORDER BY done ASC, priority DESC, created DESC", null);
        while (c.moveToNext()) {
            Task t = new Task();
            t.id = c.getInt(c.getColumnIndexOrThrow("id"));
            t.title = c.getString(c.getColumnIndexOrThrow("title"));
            t.done = c.getInt(c.getColumnIndexOrThrow("done")) == 1;
            t.due = c.getLong(c.getColumnIndexOrThrow("due"));
            t.priority = c.getInt(c.getColumnIndexOrThrow("priority"));
            t.created = c.getLong(c.getColumnIndexOrThrow("created"));
            out.add(t);
        }
        c.close();
        return out;
    }

    public synchronized void toggleTask(int id) {
        getWritableDatabase().execSQL("UPDATE tasks SET done = CASE done WHEN 1 THEN 0 ELSE 1 END WHERE id=?",
                new Object[]{id});
    }

    public synchronized void deleteTask(int id) {
        getWritableDatabase().delete("tasks", "id=?", new String[]{String.valueOf(id)});
    }

    // ---- Notes ----------------------------------------------------------

    public synchronized int addNote(String title, String body) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("created", now);
        v.put("updated", now);
        return (int) getWritableDatabase().insert("notes", null, v);
    }

    public synchronized List<Note> notes() {
        List<Note> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM notes ORDER BY updated DESC", null);
        while (c.moveToNext()) {
            Note n = new Note();
            n.id = c.getInt(c.getColumnIndexOrThrow("id"));
            n.title = c.getString(c.getColumnIndexOrThrow("title"));
            n.body = c.getString(c.getColumnIndexOrThrow("body"));
            n.created = c.getLong(c.getColumnIndexOrThrow("created"));
            n.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
            out.add(n);
        }
        c.close();
        return out;
    }

    public synchronized void deleteNote(int id) {
        getWritableDatabase().delete("notes", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("memories", null, null);
        db.delete("messages", null, null);
        db.delete("conversations", null, null);
        db.delete("reminders", null, null);
        db.delete("tasks", null, null);
        db.delete("notes", null, null);
    }
}
