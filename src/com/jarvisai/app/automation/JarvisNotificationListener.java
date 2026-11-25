package com.jarvisai.app.automation;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedList;

/** Optional, user-authorised notification reader. Keeps only an in-memory recent list. */
public class JarvisNotificationListener extends NotificationListenerService {

    public static final class Item {
        public String pkg, app, title, text;
        public long time;
    }

    private static final LinkedList<Item> RECENT = new LinkedList<>();
    private static final int MAX = 40;
    private static volatile JarvisNotificationListener instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        if (instance == this) instance = null;
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;
        try {
            Notification n = sbn.getNotification();
            Bundle x = n.extras;
            String title = str(x.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(x.getCharSequence(Notification.EXTRA_TEXT));
            if (title.isEmpty() && text.isEmpty()) return;
            Item i = new Item();
            i.pkg = sbn.getPackageName();
            i.app = label(i.pkg);
            i.title = title;
            i.text = text;
            i.time = sbn.getPostTime();
            synchronized (RECENT) {
                RECENT.addFirst(i);
                while (RECENT.size() > MAX) RECENT.removeLast();
            }
        } catch (Exception ignored) {}
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    public static boolean connected() {
        return instance != null;
    }

    public static JSONArray recentJson(int limit) {
        JSONArray arr = new JSONArray();
        synchronized (RECENT) {
            int n = 0;
            for (Item i : RECENT) {
                if (n++ >= limit) break;
                try {
                    JSONObject o = new JSONObject();
                    o.put("app", i.app);
                    o.put("title", i.title);
                    o.put("text", i.text);
                    o.put("time", i.time);
                    arr.put(o);
                } catch (Exception ignored) {}
            }
        }
        return arr;
    }

    public static int count() {
        synchronized (RECENT) { return RECENT.size(); }
    }
}
