package com.jarvisai.app.memory;

import android.content.Context;

import com.jarvisai.app.Prefs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Recall, ranking and light-weight fact extraction over the memory table. */
public class MemoryEngine {

    private final Context ctx;
    private final MemoryDB db;

    public MemoryEngine(Context c, MemoryDB db) {
        this.ctx = c.getApplicationContext();
        this.db = db;
    }

    public MemoryDB db() { return db; }

    public int remember(String key, String value, String category, int importance) {
        if (!Prefs.get(ctx).memoryEnabled()) return -1;
        if (value == null || value.trim().isEmpty()) return -1;
        return db.saveMemory(key == null ? "" : key.trim(), value.trim(),
                category == null ? "general" : category, importance);
    }

    /** Ranked recall used by the model context. */
    public List<MemoryDB.Memory> search(String query, int limit) {
        List<MemoryDB.Memory> all = db.memories();
        if (query == null || query.trim().isEmpty()) {
            if (all.size() > limit) return new ArrayList<>(all.subList(0, limit));
            return all;
        }
        Set<String> q = tokens(query);
        long now = System.currentTimeMillis();
        List<Scored> scored = new ArrayList<>();
        for (MemoryDB.Memory m : all) {
            double score = m.importance * 0.35;
            String key = m.key == null ? "" : m.key.toLowerCase(Locale.ROOT);
            String val = m.value == null ? "" : m.value.toLowerCase(Locale.ROOT);
            for (String t : q) {
                if (key.contains(t)) score += 3.0;
                if (val.contains(t)) score += 1.6;
                if (m.category != null && m.category.toLowerCase(Locale.ROOT).contains(t)) score += 0.8;
            }
            if (val.contains(query.toLowerCase(Locale.ROOT).trim())) score += 2.0;
            double ageDays = Math.max(0, now - m.updated) / 86400000.0;
            score += Math.max(0, 1.2 - ageDays * 0.02);
            if (score > 0.8) scored.add(new Scored(m, score));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<MemoryDB.Memory> out = new ArrayList<>();
        for (int i = 0; i < scored.size() && i < limit; i++) out.add(scored.get(i).m);
        return out;
    }

    private static class Scored {
        final MemoryDB.Memory m;
        final double score;
        Scored(MemoryDB.Memory m, double s) { this.m = m; this.score = s; }
    }

    public String contextBlock(int maxChars) {
        Prefs p = Prefs.get(ctx);
        StringBuilder sb = new StringBuilder();
        String name = p.userName();
        if (!name.isEmpty()) sb.append("- User's name: ").append(name).append('\n');
        sb.append("- Preferred language: ").append(p.language()).append('\n');
        List<MemoryDB.Memory> mems = db.memories();
        if (!mems.isEmpty()) {
            sb.append("- Remembered facts (newest/most important first):\n");
            for (MemoryDB.Memory m : mems) {
                String line = "  • " + (m.key != null && !m.key.isEmpty() ? m.key + " = " : "") + m.value
                        + " [" + m.category + "]\n";
                if (sb.length() + line.length() > maxChars) break;
                sb.append(line);
            }
        }
        List<MemoryDB.Task> tasks = db.tasks(false);
        if (!tasks.isEmpty()) {
            sb.append("- Open tasks: ");
            for (int i = 0; i < tasks.size() && i < 8; i++) {
                if (i > 0) sb.append("; ");
                sb.append(tasks.get(i).title);
            }
            sb.append('\n');
        }
        List<MemoryDB.Reminder> reminders = db.reminders(false);
        if (!reminders.isEmpty()) {
            sb.append("- Upcoming reminders: ");
            SimpleDateFormat f = new SimpleDateFormat("d MMM h:mm a", Locale.getDefault());
            for (int i = 0; i < reminders.size() && i < 6; i++) {
                if (i > 0) sb.append("; ");
                sb.append(reminders.get(i).text).append(" @ ").append(f.format(new Date(reminders.get(i).triggerAt)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Cheap, high-precision extraction. The model also saves facts explicitly via tools. */
    public List<String[]> extract(String text) {
        List<String[]> facts = new ArrayList<>();
        if (text == null) return facts;
        String t = text.trim();
        String low = t.toLowerCase(Locale.ROOT);

        String name = grab(low, "my name is ", "mera naam ", "main ");
        if (name != null && (low.contains("my name is") || low.contains("mera naam"))) {
            String cleaned = cleanName(name);
            if (cleaned != null) {
                facts.add(new String[]{"name", cleaned, "identity", "5"});
                Prefs.get(ctx).setUserName(cleaned);
            }
        }

        if (low.contains("remember") || low.contains("yaad rakho") || low.contains("yaad rakhna")) {
            String body = t.replaceAll("(?i)^(please\\s+)?(remember( that)?|yaad rakho( ki)?|yaad rakhna( ki)?)[:, ]*", "").trim();
            if (body.length() > 2) facts.add(new String[]{"", body, "note", "3"});
        }

        if (low.contains("mujhe") && (low.contains("pasand") || low.contains("prefer"))) {
            facts.add(new String[]{"preference", t, "preference", "3"});
        } else if (low.contains("i like") || low.contains("i prefer") || low.contains("i love")) {
            facts.add(new String[]{"preference", t, "preference", "3"});
        }

        if (low.contains("i live in") || low.contains("main rehta") || low.contains("main rehti")
                || low.contains("mera ghar")) {
            facts.add(new String[]{"location", t, "identity", "4"});
        }

        if (low.contains("my birthday") || low.contains("mera birthday") || low.contains("meri birthday")) {
            facts.add(new String[]{"birthday", t, "identity", "5"});
        }

        if (low.contains("speak hindi") || low.contains("hindi mein baat") || low.contains("english mein baat")) {
            facts.add(new String[]{"language_preference", t, "preference", "4"});
        }

        if (low.contains("i work") || low.contains("main kaam karta") || low.contains("main kaam karti")
                || low.contains("my job") || low.contains("mera kaam")) {
            facts.add(new String[]{"work", t, "identity", "4"});
        }

        return facts;
    }

    public void captureAuto(String userText) {
        if (!Prefs.get(ctx).autoMemory() || !Prefs.get(ctx).memoryEnabled()) return;
        for (String[] f : extract(userText)) {
            int imp = 3;
            try { imp = Integer.parseInt(f[3]); } catch (Exception ignored) {}
            remember(f[0], f[1], f[2], imp);
        }
    }

    private static String grab(String low, String... prefixes) {
        for (String p : prefixes) {
            int i = low.indexOf(p);
            if (i >= 0) {
                String rest = low.substring(i + p.length()).trim();
                int stop = rest.length();
                for (String end : new String[]{",", ".", "!", "?", " but ", " and ", " aur ", "\n"}) {
                    int j = rest.indexOf(end);
                    if (j > 0 && j < stop) stop = j;
                }
                rest = rest.substring(0, stop).trim();
                if (!rest.isEmpty()) return rest;
            }
        }
        return null;
    }

    private static String cleanName(String raw) {
        String s = raw.trim();
        String[] stop = {"hai", "hoon", "hain", "and", "aur", "but", "from", "se"};
        for (String w : stop) {
            int i = s.indexOf(" " + w + " ");
            if (i > 0) s = s.substring(0, i);
        }
        s = s.replaceAll("[^\\p{L}\\s.'-]", "").trim();
        if (s.isEmpty() || s.length() > 40) return null;
        String[] words = s.split("\\s+");
        if (words.length > 3) return null;
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    public static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        for (String t : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (t.length() >= 2 && !STOP.contains(t)) out.add(t);
        }
        return out;
    }

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "the", "and", "for", "you", "your", "mera", "meri", "mujhe", "hai", "hoon", "kya",
            "what", "with", "this", "that", "from", "about", "please", "kar", "karo", "ka", "ki", "mein"));
}
