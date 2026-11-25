package com.jarvisai.app.ai;

import android.content.Context;

import com.jarvisai.app.Prefs;
import com.jarvisai.app.Util;
import com.jarvisai.app.automation.Confirm;
import com.jarvisai.app.automation.DeviceActions;
import com.jarvisai.app.automation.JarvisAccessibilityService;
import com.jarvisai.app.memory.MemoryDB;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Declares the phone-control tools and executes them with verification. */
public final class Tools {

    private Tools() {}

    // ---- Schema helpers -------------------------------------------------

    private static JSONObject prop(String type, String desc) {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type);
            o.put("description", desc);
        } catch (Exception ignored) {}
        return o;
    }

    private static JSONObject props(Object... triples) {
        JSONObject p = new JSONObject();
        try {
            for (int i = 0; i + 2 < triples.length; i += 3) {
                p.put((String) triples[i], prop((String) triples[i + 1], (String) triples[i + 2]));
            }
        } catch (Exception ignored) {}
        return p;
    }

    private static JSONObject fn(String name, String desc, JSONObject properties, String... required) {
        JSONObject f = new JSONObject();
        try {
            f.put("name", name);
            f.put("description", desc);
            JSONObject params = new JSONObject();
            params.put("type", "OBJECT");
            params.put("properties", properties == null ? new JSONObject() : properties);
            JSONArray req = new JSONArray();
            for (String r : required) req.put(r);
            if (req.length() > 0) params.put("required", req);
            f.put("parameters", params);
        } catch (Exception ignored) {}
        return f;
    }

    public static JSONArray declarations() {
        JSONArray arr = new JSONArray();
        arr.put(fn("open_app", "Open an installed Android app by name (fuzzy match). Returns verified=true only if the app was confirmed in the foreground.",
                props("name", "STRING", "App name, e.g. YouTube, WhatsApp, Settings, Instagram"), "name"));
        arr.put(fn("list_apps", "List installed launchable apps. Use when an app name is ambiguous or not found.", props()));
        arr.put(fn("open_url", "Open a web page in the browser.", props("url", "STRING", "Full http(s) URL"), "url"));
        arr.put(fn("web_search", "Open a web search for a query (uses the device browser/search app).",
                props("query", "STRING", "Search query"), "query"));
        arr.put(fn("youtube_search", "Open YouTube search results.", props("query", "STRING", "Search query"), "query"));
        arr.put(fn("maps_search", "Open a place or address in Maps.", props("query", "STRING", "Place or address"), "query"));
        arr.put(fn("open_settings", "Open a system settings screen.",
                props("section", "STRING", "One of: wifi, bluetooth, display, sound, battery, location, apps, accessibility, notification, data, developer, date, keyboard, privacy, internet, main"), "section"));
        arr.put(fn("open_camera", "Open the device camera app.", props()));
        arr.put(fn("open_gallery", "Open the photo gallery.", props()));
        arr.put(fn("flashlight", "Turn the camera flashlight on or off. Verifies the torch state.",
                props("on", "BOOLEAN", "true to turn on, false to turn off"), "on"));
        arr.put(fn("set_volume", "Set media volume as a percentage and verify it.",
                props("percent", "INTEGER", "0-100"), "percent"));
        arr.put(fn("whatsapp_draft", "Open WhatsApp with a prepared message. With a phone number it opens that exact chat with a draft; with a name it copies the text and opens WhatsApp. Never sends automatically.",
                props("contact", "STRING", "Phone number with country code, or contact name",
                        "message", "STRING", "Message text to prepare"), "contact", "message"));
        arr.put(fn("share_text", "Open the Android share sheet with text.",
                props("text", "STRING", "Text to share"), "text"));
        arr.put(fn("set_alarm", "Create an alarm in the system Clock app.",
                props("hour", "INTEGER", "Hour 0-23", "minute", "INTEGER", "Minute 0-59",
                        "label", "STRING", "Alarm label"), "hour", "minute"));
        arr.put(fn("set_reminder", "Create a JARVIS reminder that fires a notification. Prefer this over set_alarm for 'remind me' requests. Provide either minutes_from_now or hour+minute.",
                props("text", "STRING", "What to remind about",
                        "minutes_from_now", "INTEGER", "Delay in minutes from now (optional)",
                        "hour", "INTEGER", "Clock hour 0-23 (optional)",
                        "minute", "INTEGER", "Clock minute 0-59 (optional)",
                        "day_offset", "INTEGER", "0=today, 1=tomorrow (optional)",
                        "repeat", "STRING", "daily, weekdays, weekly, or empty"), "text"));
        arr.put(fn("list_reminders", "List upcoming JARVIS reminders.", props()));
        arr.put(fn("add_task", "Add a to-do task.", props("title", "STRING", "Task title"), "title"));
        arr.put(fn("list_tasks", "List open tasks.", props()));
        arr.put(fn("complete_task", "Mark a task complete by id.", props("task_id", "INTEGER", "Task id"), "task_id"));
        arr.put(fn("add_note", "Save a note.", props("title", "STRING", "Note title", "body", "STRING", "Note body"), "title", "body"));
        arr.put(fn("list_notes", "List saved notes.", props()));
        arr.put(fn("save_memory", "Remember a stable fact about the user. Never store secrets, passwords, OTPs or card numbers.",
                props("key", "STRING", "Short label, e.g. name, favorite_food, project",
                        "value", "STRING", "The fact",
                        "category", "STRING", "identity, preference, project, routine, general",
                        "importance", "INTEGER", "1-5"), "value"));
        arr.put(fn("search_memory", "Search what JARVIS remembers.",
                props("query", "STRING", "Keywords"), "query"));
        arr.put(fn("forget_memory", "Delete remembered facts matching a query. Requires user confirmation.",
                props("query", "STRING", "Keywords of the memory to delete"), "query"));
        arr.put(fn("device_info", "Read battery, charging, volume, network, model and permission status.", props()));
        arr.put(fn("read_notifications", "Read recent notifications (requires notification access).",
                props("limit", "INTEGER", "How many, max 30")));
        arr.put(fn("screen_text", "Read the visible text and buttons of the current screen via accessibility.", props()));
        arr.put(fn("phone_control", "Perform an approved screen action via accessibility: tap an element by text, type into a field, scroll, or press a global button. Sensitive actions ask the user first.",
                props("action", "STRING", "tap, type, scroll_down, scroll_up, back, home, recents, notifications, quick_settings, power_menu, lock",
                        "target", "STRING", "Text of the element to tap, or the field label for type (optional)",
                        "text", "STRING", "Text to type when action=type (optional)"), "action"));
        return arr;
    }

    // ---- Execution ------------------------------------------------------

    public static DeviceActions.Result execute(Context c, String name, JSONObject a) {
        if (a == null) a = new JSONObject();
        Prefs p = Prefs.get(c);
        try {
            switch (name) {
                case "open_app": return DeviceActions.openApp(c, str(a, "name"));
                case "list_apps": return DeviceActions.appList(c);
                case "open_url": return DeviceActions.openUrl(c, str(a, "url"));
                case "web_search": return DeviceActions.webSearch(c, str(a, "query"));
                case "youtube_search": return DeviceActions.openYouTube(c, str(a, "query"));
                case "maps_search": return DeviceActions.openMaps(c, str(a, "query"));
                case "open_settings": return DeviceActions.openSettings(c, str(a, "section"));
                case "open_camera": return DeviceActions.openCamera(c);
                case "open_gallery": return DeviceActions.openGallery(c);
                case "flashlight": return DeviceActions.torch(c, bool(a, "on", true));
                case "set_volume": return DeviceActions.volume(c, num(a, "percent", 50));
                case "whatsapp_draft": {
                    String contact = str(a, "contact");
                    String message = str(a, "message");
                    if (p.confirmActions() && !confirm("WhatsApp draft",
                            "WhatsApp khol kar \"" + Util.truncate(message, 80) + "\" ka draft ready karun? Message aap bhejenge.")) {
                        return DeviceActions.Result.fail("User ne WhatsApp draft cancel kar diya.");
                    }
                    return DeviceActions.whatsappDraft(c, contact, message);
                }
                case "share_text": {
                    if (p.confirmActions() && !confirm("Share text", "Ye text share karne ke liye share sheet kholun?")) {
                        return DeviceActions.Result.fail("User ne share cancel kar diya.");
                    }
                    return DeviceActions.share(c, str(a, "text"));
                }
                case "set_alarm":
                    return DeviceActions.setSystemAlarm(c, num(a, "hour", 7), num(a, "minute", 0), str(a, "label"));
                case "set_reminder": {
                    String text = str(a, "text");
                    int minutes = num(a, "minutes_from_now", -1);
                    long when;
                    if (minutes > 0) {
                        when = System.currentTimeMillis() + minutes * 60000L;
                    } else if (a.has("hour")) {
                        when = DeviceActions.atTime(num(a, "hour", 9), num(a, "minute", 0), num(a, "day_offset", 0));
                    } else {
                        return DeviceActions.Result.fail("Reminder ka time clear nahi hai. Kitne minute baad ya kis baje?");
                    }
                    if (when <= System.currentTimeMillis() + 5000) {
                        return DeviceActions.Result.fail("Ye time nikal chuka hai. Naya time bataiye.");
                    }
                    return DeviceActions.createReminder(c, text, when, str(a, "repeat"));
                }
                case "list_reminders": return DeviceActions.reminders(c);
                case "add_task": return DeviceActions.addTask(c, str(a, "title"), 0);
                case "list_tasks": return DeviceActions.tasks(c);
                case "complete_task": return DeviceActions.completeTask(c, num(a, "task_id", -1));
                case "add_note": return DeviceActions.addNote(c, str(a, "title"), str(a, "body"));
                case "list_notes": return DeviceActions.notes(c);
                case "save_memory": {
                    String value = str(a, "value");
                    if (value.isEmpty()) return DeviceActions.Result.fail("Memory value khaali hai.");
                    if (looksSecret(value)) return DeviceActions.Result.fail("Ye sensitive lagta hai (password/OTP/card). Main ise save nahi karungi.");
                    int id = com.jarvisai.app.JarvisApp.get().memory().remember(
                            str(a, "key"), value, str(a, "category"), Math.max(1, Math.min(5, num(a, "importance", 3))));
                    JSONObject d = new JSONObject();
                    d.put("memory_id", id);
                    return id > 0
                            ? DeviceActions.Result.ok("Yaad rakh liya: " + Util.truncate(value, 120), d)
                            : DeviceActions.Result.fail("Memory save nahi hui (memory off hai ya value khaali).");
                }
                case "search_memory": {
                    String q = str(a, "query");
                    List<MemoryDB.Memory> hits = com.jarvisai.app.JarvisApp.get().memory().search(q, 8);
                    JSONArray arr = new JSONArray();
                    StringBuilder sb = new StringBuilder();
                    for (MemoryDB.Memory m : hits) {
                        JSONObject o = new JSONObject();
                        o.put("id", m.id);
                        o.put("key", m.key);
                        o.put("value", m.value);
                        o.put("category", m.category);
                        arr.put(o);
                        if (sb.length() > 0) sb.append("; ");
                        sb.append(m.display());
                    }
                    JSONObject d = new JSONObject();
                    d.put("memories", arr);
                    return DeviceActions.Result.ok(sb.length() == 0 ? "Is baare mein kuch yaad nahi hai." : sb.toString(), d);
                }
                case "forget_memory": {
                    String q = str(a, "query");
                    List<MemoryDB.Memory> hits = com.jarvisai.app.JarvisApp.get().memory().search(q, 5);
                    if (hits.isEmpty()) return DeviceActions.Result.fail("Is query se koi memory nahi mili.");
                    StringBuilder sb = new StringBuilder();
                    for (MemoryDB.Memory m : hits) sb.append("• ").append(m.display()).append('\n');
                    if (!confirm("Forget memories", "Ye delete kar dun?\n\n" + sb)) {
                        return DeviceActions.Result.fail("User ne delete cancel kiya.");
                    }
                    for (MemoryDB.Memory m : hits) com.jarvisai.app.JarvisApp.get().db().deleteMemory(m.id);
                    return DeviceActions.Result.ok(hits.size() + " memories delete kar di.");
                }
                case "device_info": return DeviceActions.deviceInfo(c);
                case "read_notifications": return DeviceActions.readNotifications(num(a, "limit", 10));
                case "screen_text": {
                    JarvisAccessibilityService svc = JarvisAccessibilityService.get();
                    if (svc == null) return DeviceActions.Result.fail("Accessibility off hai. Permissions screen se on karein.");
                    String s = svc.summarize(80, 2200);
                    if (s.isEmpty()) return DeviceActions.Result.fail("Screen par koi readable text nahi mila.");
                    return DeviceActions.Result.ok(Util.truncate(s, 1800));
                }
                case "phone_control": {
                    String action = str(a, "action");
                    String target = str(a, "target");
                    String text = str(a, "text");
                    if (JarvisAccessibilityService.get() == null) {
                        return DeviceActions.Result.fail("Accessibility service off hai. Permissions screen se on karein.");
                    }
                    boolean sensitive = action.equals("tap") || action.equals("type") || action.equals("click");
                    if (sensitive && p.confirmActions()) {
                        String what = action.equals("type")
                                ? "text likhun" : "screen par tap karun";
                        String detail = action.equals("type")
                                ? "Field: " + (target.isEmpty() ? "(focused field)" : target) + "\nText: " + text
                                : "Target: " + target;
                        if (!confirm("Phone control", what + "?\n\n" + detail)) {
                            return DeviceActions.Result.fail("User ne screen action cancel kar diya.");
                        }
                    }
                    return DeviceActions.accessibilityAction(action, target, text);
                }
                default:
                    return DeviceActions.Result.fail("Unknown tool: " + name);
            }
        } catch (Exception e) {
            return DeviceActions.Result.fail("Tool '" + name + "' fail hua: " + e.getClass().getSimpleName());
        }
    }

    private static boolean confirm(String title, String msg) {
        if (!Confirm.hasUi()) return false;
        return Confirm.ask(title, msg);
    }

    private static String str(JSONObject o, String key) {
        if (o == null) return "";
        Object v = o.opt(key);
        return v == null || v == JSONObject.NULL ? "" : String.valueOf(v).trim();
    }

    private static int num(JSONObject o, String key, int def) {
        if (o == null) return def;
        Object v = o.opt(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return def; }
    }

    private static boolean bool(JSONObject o, String key, boolean def) {
        if (o == null) return def;
        Object v = o.opt(key);
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static boolean looksSecret(String v) {
        String s = v.toLowerCase();
        return s.contains("password") || s.contains("otp") || s.contains("pin ")
                || s.contains("cvv") || s.matches(".*\\b\\d{12,19}\\b.*")
                || s.contains("api key") || s.contains("secret");
    }
}
