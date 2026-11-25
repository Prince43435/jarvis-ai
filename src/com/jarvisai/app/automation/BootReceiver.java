package com.jarvisai.app.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jarvisai.app.Prefs;

/** Restores alarms and the floating orb after a reboot or app update. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        try {
            AlarmScheduler.rescheduleAll(c);
        } catch (Exception ignored) {}
        try {
            if (Prefs.get(c).overlayEnabled() && android.provider.Settings.canDrawOverlays(c)) {
                Intent s = new Intent(c, OverlayService.class);
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                else c.startService(s);
            }
        } catch (Exception ignored) {}
    }
}
