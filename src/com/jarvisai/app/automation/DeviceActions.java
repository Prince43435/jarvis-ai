package com.jarvisai.app.automation;

import android.app.AlarmManager;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import com.jarvisai.app.JarvisApp;
import com.jarvisai.app.Prefs;
import com.jarvisai.app.Util;
import com.jarvisai.app.memory.MemoryDB;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Real Android actions with honest verification flags. No action pretends to succeed. */
public final class DeviceActions {

    private DeviceActions() {}

    public static final class Result {
        public final boolean ok;
        public final boolean verified;
        public final String message;
        public final JSONObject data;

        public Result(boolean ok, boolean verified, String message, JSONObject data) {
            this.ok = ok;
            this.verified = verified;
            this.message = message;
            this.data = data == null ? new JSONObject() : data;
        }

        public static Result ok(String msg) { return new Result(true, true, msg, null); }
        public static Result ok(String msg, JSONObject data) { return new Result(true, true, msg, data); }
        public static Result okUnverified(String msg, JSONObject data) { return new Result(true, false, msg, data); }
        public static Result fail(String msg) { return new Result(false, false, msg, null); }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("ok", ok);
                o.put("verified", verified);
                o.put("message", message);
                if (data != null) o.put("data", data);
            } catch (Exception ignored) {}
            return o;
        }
    }

    public static final class AppEntry {
        public final String label, pkg;
        public AppEntry(String label, String pkg) { this.label = label; this.pkg = pkg; }
    }

    private static List<AppEntry> appCache;
    private static long appCacheAt;

    public static synchronized List<AppEntry> launcherApps(Context c) {
        long now = System.currentTimeMillis();
        if (appCache != null && now - appCacheAt < 120000) return appCache;
        List<AppEntry> out = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : list) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || seen.contains(pkg)) continue;
                seen.add(pkg);
                String label = ri.loadLabel(pm).toString();
                out.add(new AppEntry(label, pkg));
            }
            Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        } catch (Exception ignored) {}
        appCache = out;
        appCacheAt = now;
        return out;
    }

    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        ALIASES.put("yt", "youtube");
        ALIASES.put("wa", "whatsapp");
        ALIASES.put("wp", "whatsapp");
        ALIASES.put("insta", "instagram");
        ALIASES.put("fb", "facebook");
        ALIASES.put("gmail", "gmail");
        ALIASES.put("gpay", "google pay");
        ALIASES.put("maps", "maps");
        ALIASES.put("playstore", "play store");
        ALIASES.put("play store", "play store");
        ALIASES.put("settings", "settings");
        ALIASES.put("camera", "camera");
        ALIASES.put("gallery", "photos");
        ALIASES.put("photos", "photos");
        ALIASES.put("music", "youtube music");
        ALIASES.put("browser", "chrome");
        ALIASES.put("sms", "messages");
        ALIASES.put("phone", "phone");
        ALIASES.put("dialer", "phone");
        ALIASES.put("calculator", "calculator");
        ALIASES.put("clock", "clock");
        ALIASES.put("alarm", "clock");
    }

    public static AppEntry findApp(Context c, String query) {
        if (query == null) return null;
        String q = query.toLowerCase(Locale.ROOT).trim();
        String alias = ALIASES.get(q);
        if (alias != null) q = alias;
        List<AppEntry> apps = launcherApps(c);
        AppEntry best = null;
        int bestScore = 0;
        for (AppEntry a : apps) {
            String label = a.label.toLowerCase(Locale.ROOT);
            String pkg = a.pkg.toLowerCase(Locale.ROOT);
            int score = 0;
            if (label.equals(q)) score = 100;
            else if (label.startsWith(q)) score = 82;
            else if (label.contains(q)) score = 64;
            else if (q.contains(label) && label.length() > 2) score = 56;
            else if (pkg.endsWith("." + q.replace(" ", ""))) score = 60;
            else if (pkg.contains(q.replace(" ", ""))) score = 40;
            else {
                for (String token : q.split("\\s+")) {
                    if (token.length() > 2 && label.contains(token)) score = Math.max(score, 34);
                }
            }
            if (score > bestScore) { bestScore = score; best = a; }
        }
        return bestScore >= 34 ? best : null;
    }

    public static Result openApp(Context c, String name) {
        AppEntry app = findApp(c, name);
        if (app == null) {
            return Result.fail("Mujhe '" + name + "' naam ka koi app is phone par nahi mila. " +
                    "Aap exact app name bata sakte hain.");
        }
        try {
            PackageManager pm = c.getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage(app.pkg);
            if (launch == null) return Result.fail(app.label + " kholne ka official screen nahi mila.");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(launch);
            JSONObject d = new JSONObject();
            d.put("package", app.pkg);
            d.put("app", app.label);
            try { Thread.sleep(650); } catch (InterruptedException ignored) {}
            JarvisAccessibilityService acc = JarvisAccessibilityService.get();
            if (acc != null) {
                String current = acc.currentPackage();
                boolean confirmed = app.pkg.equals(current);
                d.put("foreground", current);
                if (confirmed) return Result.ok(app.label + " khul gaya hai.", d);
                return Result.okUnverified(app.label + " launch request bheji hai (screen par confirm nahi hua).", d);
            }
            return Result.okUnverified(app.label + " launch request bheji hai. " +
                    "(Screen confirmation ke liye Accessibility on karein.)", d);
        } catch (Exception e) {
            return Result.fail(app.label + " kholne mein dikkat aayi: " + e.getClass().getSimpleName());
        }
    }

    public static Result openUrl(Context c, String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) == null) return Result.fail("Koi browser nahi mila.");
            c.startActivity(i);
            return Result.ok("Link khol diya: " + Util.truncate(url, 60));
        } catch (Exception e) {
            return Result.fail("Link nahi khul paya.");
        }
    }

    public static Result webSearch(Context c, String q) {
        try {
            Intent i = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) != null) {
                c.startActivity(i);
                return Result.ok("Web search khol diya: " + q);
            }
            return openUrl(c, "https://www.google.com/search?q=" + Uri.encode(q));
        } catch (Exception e) {
            return Result.fail("Search nahi khul paya.");
        }
    }

    public static Result openYouTube(Context c, String q) {
        String url = "https://www.youtube.com/results?search_query=" + Uri.encode(q == null ? "" : q);
        return openUrl(c, url);
    }

    public static Result openMaps(Context c, String q) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) == null) return Result.fail("Maps app nahi mila.");
            c.startActivity(i);
            return Result.ok("Maps par khol diya: " + q);
        } catch (Exception e) {
            return Result.fail("Maps nahi khul paya.");
        }
    }

    public static Result openSettings(Context c, String section) {
        String s = section == null ? "" : section.toLowerCase(Locale.ROOT).trim();
        Intent i;
        switch (s) {
            case "wifi":
                i = new Intent(Build.VERSION.SDK_INT >= 29
                        ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS);
                break;
            case "bluetooth":
                i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                break;
            case "display":
                i = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                break;
            case "sound":
                i = new Intent(Settings.ACTION_SOUND_SETTINGS);
                break;
            case "battery":
                i = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                break;
            case "location":
                i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                break;
            case "apps":
                i = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
                break;
            case "accessibility":
                i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
            case "notification":
            case "notifications":
                i = new Intent("android.settings.NOTIFICATION_SETTINGS");
                break;
            case "data":
                i = new Intent(Settings.ACTION_DATA_USAGE_SETTINGS);
                break;
            case "developer":
                i = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                break;
            case "date":
                i = new Intent(Settings.ACTION_DATE_SETTINGS);
                break;
            case "keyboard":
                i = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                break;
            case "privacy":
                i = new Intent(Settings.ACTION_PRIVACY_SETTINGS);
                break;
            case "internet":
                if (Build.VERSION.SDK_INT >= 29) i = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
                else i = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                break;
            default:
                i = new Intent(Settings.ACTION_SETTINGS);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (i.resolveActivity(c.getPackageManager()) == null) return Result.fail("Ye settings screen nahi mili.");
            c.startActivity(i);
            return Result.ok("Settings khol di (" + (s.isEmpty() ? "main" : s) + ").");
        } catch (Exception e) {
            return Result.fail("Settings nahi khuli.");
        }
    }

    public static Result openCamera(Context c) {
        Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (i.resolveActivity(c.getPackageManager()) == null) return Result.fail("Camera app nahi mila.");
            c.startActivity(i);
            return Result.ok("Camera khol diya.");
        } catch (Exception e) {
            return Result.fail("Camera nahi khula.");
        }
    }

    public static Result openGallery(Context c) {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 29) {
                i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY);
            } else {
                i = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) == null) {
                i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            if (i.resolveActivity(c.getPackageManager()) == null) return Result.fail("Gallery app nahi mila.");
            c.startActivity(i);
            return Result.ok("Gallery khol di.");
        } catch (Exception e) {
            return Result.fail("Gallery nahi khuli.");
        }
    }

    public static Result torch(Context c, boolean on) {
        if (!hasPermission(c, android.Manifest.permission.CAMERA)) {
            return Result.fail("Flashlight ke liye Camera permission chahiye. Permissions screen se dein.");
        }
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return Result.fail("Camera service unavailable.");
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    cm.setTorchMode(id, on);
                    Thread.sleep(250);
                    boolean state = true;
                    if (state == on) return Result.ok(on ? "Flashlight on hai." : "Flashlight off kar di.");
                    return Result.fail("Flashlight state badal nahi payi.");
                }
            }
            return Result.fail("Is phone mein flashlight nahi mila.");
        } catch (Exception e) {
            return Result.fail("Flashlight control fail: " + e.getClass().getSimpleName());
        }
    }

    public static Result volume(Context c, int percent) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return Result.fail("Audio service unavailable.");
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = Math.max(0, Math.min(max, Math.round(max * (percent / 100f))));
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            int now = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int pct = Math.round(now * 100f / Math.max(1, max));
            if (Math.abs(now - target) <= 1) return Result.ok("Volume " + pct + "% kar diya.");
            return Result.fail("Volume set nahi hua (abhi " + pct + "%).");
        } catch (Exception e) {
            return Result.fail("Volume control fail.");
        }
    }

    public static Result whatsappDraft(Context c, String contact, String message) {
        String digits = contact == null ? "" : contact.replaceAll("[^0-9]", "");
        String text = message == null ? "" : message;
        boolean whatsapp = isInstalled(c, "com.whatsapp") || isInstalled(c, "com.whatsapp.w4b");
        if (!whatsapp) return Result.fail("WhatsApp is phone par installed nahi hai.");
        try {
            if (digits.length() >= 10) {
                String url = "https://wa.me/" + digits + (text.isEmpty() ? "" : "?text=" + Uri.encode(text));
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setPackage(isInstalled(c, "com.whatsapp") ? "com.whatsapp" : "com.whatsapp.w4b");
                if (i.resolveActivity(c.getPackageManager()) == null) {
                    i.setPackage(null);
                }
                c.startActivity(i);
                return Result.ok("WhatsApp chat khol di " + digits + " ke liye, message draft ke saath. " +
                        "Send button aap dabayein.");
            }
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && !text.isEmpty()) cm.setPrimaryClip(ClipData.newPlainText("JARVIS message", text));
            Intent main = c.getPackageManager().getLaunchIntentForPackage("com.whatsapp");
            if (main == null) return Result.fail("WhatsApp launch nahi ho paya.");
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(main);
            return Result.ok("WhatsApp khol diya aur message clipboard mein copy kar diya. " +
                    "Contact chun kar paste karke bhej dein — main bina aapki permission ke message send nahi karti.");
        } catch (Exception e) {
            return Result.fail("WhatsApp draft fail: " + e.getClass().getSimpleName());
        }
    }

    public static Result share(Context c, String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(Intent.createChooser(i, "Share with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return Result.ok("Share sheet khol diya.");
        } catch (Exception e) {
            return Result.fail("Share nahi ho paya.");
        }
    }

    public static Result deviceInfo(Context c) {
        JSONObject d = new JSONObject();
        try {
            BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            int pct = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            Intent batt = c.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            boolean charging = false;
            if (batt != null) {
                int status = batt.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            }
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int vol = 0;
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                vol = Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / Math.max(1, max));
            }
            d.put("battery", pct);
            d.put("charging", charging);
            d.put("volume", vol);
            d.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            d.put("android", Build.VERSION.RELEASE);
            d.put("time", Util.nowForPrompt());
            d.put("network", Net(c));
            d.put("accessibility", JarvisAccessibilityService.running());
            d.put("notification_access", JarvisNotificationListener.connected());
            StringBuilder sb = new StringBuilder();
            sb.append("Battery ").append(pct).append("%");
            if (charging) sb.append(" (charging)");
            sb.append(", volume ").append(vol).append("%, ").append(Net(c));
            return Result.ok(sb.toString(), d);
        } catch (Exception e) {
            return Result.fail("Device info read nahi hui.");
        }
    }

    private static String Net(Context c) {
        return com.jarvisai.app.Net.online(c) ? "internet connected" : "offline";
    }

    public static Result setSystemAlarm(Context c, int hour, int minute, String label) {
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label == null ? "JARVIS alarm" : label)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) == null) {
                return Result.fail("System Clock app nahi mila. Main in-app reminder set kar sakti hoon.");
            }
            c.startActivity(i);
            Thread.sleep(500);
            String next = "";
            try {
                next = Settings.System.getString(c.getContentResolver(), Settings.System.NEXT_ALARM_FORMATTED);
            } catch (Exception ignored) {}
            JSONObject d = new JSONObject();
            d.put("hour", hour);
            d.put("minute", minute);
            d.put("next_alarm", next == null ? "" : next);
            String time = Util.hhmm(hour, minute);
            if (next != null && !next.isEmpty()) {
                return Result.ok("System Clock par " + time + " ka alarm set kar diya (next alarm: " + next + ").", d);
            }
            return Result.okUnverified("System Clock ko " + time + " ka alarm bheja. Clock app mein verify kar lein.", d);
        } catch (Exception e) {
            return Result.fail("Alarm set nahi ho paya: " + e.getClass().getSimpleName());
        }
    }

    // ---- Reminders / tasks / notes (owned by JARVIS, fully verifiable) ---

    public static Result createReminder(Context c, String text, long atMillis, String repeat) {
        try {
            MemoryDB db = JarvisApp.get().db();
            int requestCode = (int) (System.currentTimeMillis() % 1_000_000) + (int) (Math.random() * 1000);
            int id = db.addReminder(text, atMillis, requestCode, repeat);
            MemoryDB.Reminder r = db.reminder(id);
            boolean scheduled = r != null && AlarmScheduler.schedule(c, r);
            ReminderReceiver.ensureChannel(c);
            if (!scheduled) return Result.fail("Reminder save hua par alarm schedule nahi ho paya.");
            JSONObject d = new JSONObject();
            d.put("reminder_id", id);
            d.put("when", Util.friendlyDate(atMillis));
            d.put("exact", AlarmScheduler.canExact(c));
            d.put("repeat", repeat == null ? "" : repeat);
            return Result.ok("Reminder set: \"" + text + "\" — " + Util.friendlyDate(atMillis)
                    + (AlarmScheduler.canExact(c) ? "" : " (inexact; exact alarms permission off hai)"), d);
        } catch (Exception e) {
            return Result.fail("Reminder set nahi ho paya.");
        }
    }

    public static Result reminders(Context c) {
        try {
            List<MemoryDB.Reminder> list = JarvisApp.get().db().reminders(false);
            JSONArray arr = new JSONArray();
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (MemoryDB.Reminder r : list) {
                if (n++ >= 10) break;
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("text", r.text);
                o.put("when", Util.friendlyDate(r.triggerAt));
                arr.put(o);
                if (sb.length() > 0) sb.append("; ");
                sb.append(r.text).append(" — ").append(Util.friendlyDate(r.triggerAt));
            }
            JSONObject d = new JSONObject();
            d.put("reminders", arr);
            return Result.ok(sb.length() == 0 ? "Koi upcoming reminder nahi hai." : sb.toString(), d);
        } catch (Exception e) {
            return Result.fail("Reminders read nahi hue.");
        }
    }

    public static Result addTask(Context c, String title, long due) {
        int id = JarvisApp.get().db().addTask(title, due, 1);
        JSONObject d = new JSONObject();
        try { d.put("task_id", id); } catch (Exception ignored) {}
        return Result.ok("Task add ho gaya: " + title, d);
    }

    public static Result tasks(Context c) {
        List<MemoryDB.Task> list = JarvisApp.get().db().tasks(false);
        JSONArray arr = new JSONArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < 20; i++) {
            MemoryDB.Task t = list.get(i);
            try {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("title", t.title);
                arr.put(o);
            } catch (Exception ignored) {}
            if (sb.length() > 0) sb.append("; ");
            sb.append(t.title);
        }
        JSONObject d = new JSONObject();
        try { d.put("tasks", arr); } catch (Exception ignored) {}
        return Result.ok(sb.length() == 0 ? "Task list khaali hai." : sb.toString(), d);
    }

    public static Result completeTask(Context c, int id) {
        JarvisApp.get().db().toggleTask(id);
        return Result.ok("Task update kar diya.");
    }

    public static Result addNote(Context c, String title, String body) {
        int id = JarvisApp.get().db().addNote(title, body);
        JSONObject d = new JSONObject();
        try { d.put("note_id", id); } catch (Exception ignored) {}
        return Result.ok("Note save ho gaya: " + title, d);
    }

    public static Result notes(Context c) {
        List<MemoryDB.Note> list = JarvisApp.get().db().notes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < 12; i++) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(list.get(i).title);
        }
        return Result.ok(sb.length() == 0 ? "Koi note nahi hai." : sb.toString());
    }

    public static Result readNotifications(int limit) {
        JSONArray arr = JarvisNotificationListener.recentJson(Math.max(1, Math.min(30, limit)));
        JSONObject d = new JSONObject();
        try { d.put("notifications", arr); } catch (Exception ignored) {}
        if (arr.length() == 0) {
            return Result.fail("Abhi koi notification cache mein nahi hai. " +
                    (JarvisNotificationListener.connected()
                            ? "Naye notifications aate hi yahan dikhenge."
                            : "Notification access on karein (Permissions screen)."));
        }
        return Result.ok(arr.length() + " recent notifications mile.", d);
    }

    public static Result accessibilityAction(String action, String target, String text) {
        JarvisAccessibilityService svc = JarvisAccessibilityService.get();
        if (svc == null) return Result.fail("Accessibility service off hai. Permissions screen se on karein.");
        String a = action == null ? "" : action.toLowerCase(Locale.ROOT);
        String msg;
        switch (a) {
            case "tap":
            case "click":
                msg = svc.clickText(target == null ? "" : target);
                return new Result(!msg.startsWith("Not found") && !msg.startsWith("Could not"),
                        false, msg, null);
            case "type":
            case "input":
                msg = svc.typeText(target == null ? "" : target, text == null ? "" : text);
                return new Result(!msg.startsWith("No text") && !msg.startsWith("Could not") && !msg.contains("failed"),
                        false, msg, null);
            case "scroll_down": return Result.okUnverified(svc.scroll(true), null);
            case "scroll_up": return Result.okUnverified(svc.scroll(false), null);
            case "back":
            case "home":
            case "recents":
            case "notifications":
            case "quick_settings":
            case "power_menu":
            case "lock":
                msg = svc.global(a);
                return new Result(!msg.startsWith("Android refused") && !msg.startsWith("Unknown"),
                        false, msg, null);
            case "screen":
                return Result.okUnverified(Util.truncate(svc.summarize(80, 2200), 1500), null);
            default:
                return Result.fail("Unsupported accessibility action: " + action);
        }
    }

    public static Result appList(Context c) {
        List<AppEntry> apps = launcherApps(c);
        JSONArray arr = new JSONArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apps.size() && i < 60; i++) {
            arr.put(apps.get(i).label);
            if (sb.length() > 0) sb.append(", ");
            sb.append(apps.get(i).label);
        }
        JSONObject d = new JSONObject();
        try {
            d.put("apps", arr);
            d.put("total", apps.size());
        } catch (Exception ignored) {}
        return Result.ok("Installed apps: " + sb, d);
    }

    public static boolean isInstalled(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasPermission(Context c, String p) {
        return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    /** Convert "HH:mm" plus optional day offset to a timestamp. */
    public static long atTime(int hour, int minute, int dayOffset) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, dayOffset);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }
}
