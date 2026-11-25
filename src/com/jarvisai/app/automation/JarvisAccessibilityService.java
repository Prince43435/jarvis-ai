package com.jarvisai.app.automation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accessibility automation. Every helper is invoked only from an approved JARVIS
 * tool call; the service itself never acts on its own.
 */
public class JarvisAccessibilityService extends AccessibilityService {

    public interface ShotCallback { void onShot(Bitmap bmp); }

    private static volatile JarvisAccessibilityService instance;

    private volatile String lastPackage = "";
    private volatile String lastScreen = "";
    private volatile long lastScreenAt = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
                setServiceInfo(info);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) lastPackage = pkg.toString();
            lastScreenAt = System.currentTimeMillis();
            try {
                lastScreen = summarize(60, 2600);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static JarvisAccessibilityService get() { return instance; }
    public static boolean running() { return instance != null; }

    public String currentPackage() { return lastPackage; }
    public String currentScreen() { return lastScreen; }

    // ---- Screen reading -------------------------------------------------

    public String summarize(int maxNodes, int maxChars) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited < maxNodes && sb.length() < maxChars) {
            AccessibilityNodeInfo n = q.poll();
            visited++;
            String t = textOf(n);
            if (!t.isEmpty()) {
                boolean interactive = n.isClickable() || n.isEditable() || n.isScrollable();
                sb.append(interactive ? "[" : "").append(t).append(interactive ? "]" : "").append('\n');
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        return sb.toString().trim();
    }

    private static String textOf(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        if (t == null || t.length() == 0) t = n.getContentDescription();
        if (t == null || t.length() == 0) t = n.getHintText();
        return t == null ? "" : t.toString().trim();
    }

    private AccessibilityNodeInfo find(String query, boolean editOnly) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        ArrayDeque<AccessibilityNodeInfo> dq = new ArrayDeque<>();
        dq.add(root);
        int visited = 0;
        AccessibilityNodeInfo fallbackEdit = null;
        while (!dq.isEmpty() && visited < 2500) {
            AccessibilityNodeInfo n = dq.poll();
            visited++;
            boolean editable = n.isEditable()
                    || (n.getClassName() != null && n.getClassName().toString().contains("EditText"));
            if (editable && fallbackEdit == null) fallbackEdit = n;
            if (editOnly && editable) return n;
            if (!editOnly) {
                String t = textOf(n).toLowerCase(Locale.ROOT);
                if (!t.isEmpty() && (q.isEmpty() || t.contains(q))) return n;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) dq.add(ch);
            }
        }
        return editOnly ? fallbackEdit : null;
    }

    // ---- Actions --------------------------------------------------------

    public String clickText(String target) {
        try {
            AccessibilityNodeInfo node = find(target, false);
            if (node == null) return "Not found on screen: " + target;
            AccessibilityNodeInfo clickable = node;
            for (int i = 0; i < 6 && clickable != null; i++) {
                if (clickable.isClickable()) break;
                clickable = clickable.getParent();
            }
            if (clickable == null) return "Found '" + target + "' but it is not clickable.";
            boolean ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!ok) return "Could not tap '" + target + "' (no click action).";
            Thread.sleep(450);
            return "Tapped '" + target + "' in " + (lastPackage.isEmpty() ? "the current app" : lastPackage) + ".";
        } catch (Exception e) {
            return "Tap failed: " + e.getMessage();
        }
    }

    public String typeText(String target, String text) {
        try {
            AccessibilityNodeInfo node = find(target, true);
            if (node == null) return "No text field found on screen.";
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            if (!ok) return "Could not set text in the field.";
            Thread.sleep(200);
            CharSequence now = node.getText();
            if (now != null && !now.toString().equals(text)) {
                return "Text was entered but the field now shows something different.";
            }
            return "Typed into the field.";
        } catch (Exception e) {
            return "Typing failed: " + e.getMessage();
        }
    }

    public String global(String action) {
        int code;
        switch (action == null ? "" : action.toLowerCase(Locale.ROOT)) {
            case "back": code = GLOBAL_ACTION_BACK; break;
            case "home": code = GLOBAL_ACTION_HOME; break;
            case "recents": code = GLOBAL_ACTION_RECENTS; break;
            case "notifications": code = GLOBAL_ACTION_NOTIFICATIONS; break;
            case "quick_settings": code = GLOBAL_ACTION_QUICK_SETTINGS; break;
            case "power_menu": code = GLOBAL_ACTION_POWER_DIALOG; break;
            case "lock": code = Build.VERSION.SDK_INT >= 28 ? GLOBAL_ACTION_LOCK_SCREEN : -1; break;
            default: return "Unknown action: " + action;
        }
        if (code < 0) return "Not supported on this Android version.";
        boolean ok = performGlobalAction(code);
        return ok ? "Performed " + action + "." : "Android refused " + action + ".";
    }

    public String scroll(boolean down) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "No screen to scroll.";
            AccessibilityNodeInfo scrollable = findScrollable(root);
            if (scrollable == null) return "Nothing scrollable on screen.";
            boolean ok = scrollable.performAction(down
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            return ok ? "Scrolled " + (down ? "down" : "up") + "." : "Scroll refused.";
        } catch (Exception e) {
            return "Scroll failed.";
        }
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int n = 0;
        while (!q.isEmpty() && n++ < 1200) {
            AccessibilityNodeInfo node = q.poll();
            if (node.isScrollable()) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo c = node.getChild(i);
                if (c != null) q.add(c);
            }
        }
        return null;
    }

    public void screenshot(ShotCallback cb) {
        if (Build.VERSION.SDK_INT < 30) {
            cb.onShot(null);
            return;
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    Bitmap bmp = null;
                    try {
                        Bitmap hw = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                        if (hw != null) bmp = hw.copy(Bitmap.Config.ARGB_8888, false);
                    } catch (Exception ignored) {
                    } finally {
                        try { result.getHardwareBuffer().close(); } catch (Exception ignored) {}
                    }
                    cb.onShot(bmp);
                }

                @Override
                public void onFailure(int errorCode) {
                    cb.onShot(null);
                }
            });
        } catch (Exception e) {
            cb.onShot(null);
        }
    }

    public List<String> clickableLabels(int limit) {
        List<String> out = new ArrayList<>();
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return out;
            ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);
            int visited = 0;
            while (!q.isEmpty() && visited++ < 2000 && out.size() < limit) {
                AccessibilityNodeInfo n = q.poll();
                if (n.isClickable()) {
                    String t = textOf(n);
                    if (!t.isEmpty() && !out.contains(t)) out.add(t);
                }
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo c = n.getChild(i);
                    if (c != null) q.add(c);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }
}
