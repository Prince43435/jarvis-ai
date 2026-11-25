package com.jarvisai.app.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jarvisai.app.MainActivity;
import com.jarvisai.app.Prefs;

public class OrbActionReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP = "com.jarvisai.app.action.ORB_STOP";
    public static final String ACTION_OPEN = "com.jarvisai.app.action.ORB_OPEN";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        if (ACTION_STOP.equals(a)) {
            Prefs.get(c).setOverlayEnabled(false);
            c.stopService(new Intent(c, OverlayService.class));
        } else if (ACTION_OPEN.equals(a)) {
            c.startActivity(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
    }
}
