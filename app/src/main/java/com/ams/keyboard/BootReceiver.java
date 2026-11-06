package com.ams.keyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Ensures the AMS Keyboard notification panel shortcut and Quick Settings tile state are restored
 * after device reboot.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (KeyboardPreferences.isNotificationShortcutsEnabled(context)
                    && NotificationHelper.isAmsKeyboardSelected(context)) {
                NotificationHelper.showNotification(context);
            }
            QuickToolsTileService.requestListeningState(context);
        }
    }
}
