package com.ams.keyboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

/**
 * Manages the ongoing permanent notification for AMS Keyboard in the notification drawer.
 *
 * <p>Allows users to force the keyboard to open anywhere (even without a text field, like Hacker's
 * Keyboard), summon shortcuts, and access clipboard history from any screen.
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "ams_keyboard_shortcuts_channel";
    public static final int NOTIFICATION_ID = 2024;

    private NotificationHelper() {
    }

    /** Returns true if AMS Keyboard is enabled in Android's Input Method settings. */
    public static boolean isAmsKeyboardEnabled(Context context) {
        try {
            InputMethodManager imm =
                    (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) {
                return false;
            }
            List<InputMethodInfo> enabled = imm.getEnabledInputMethodList();
            if (enabled == null) {
                return false;
            }
            String pkg = context.getPackageName();
            for (InputMethodInfo info : enabled) {
                if (pkg.equals(info.getPackageName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Returns true if AMS Keyboard is currently selected as the active input method in Android. */
    public static boolean isAmsKeyboardSelected(Context context) {
        try {
            String currentIme = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            return currentIme != null && currentIme.startsWith(context.getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Posts the ongoing notification only if it is enabled in preferences and not already active
     * in the status bar, avoiding redundant NotificationManager updates on every input bind.
     */
    public static void ensureNotificationShown(Context context) {
        if (!KeyboardPreferences.isNotificationShortcutsEnabled(context)) {
            cancelNotification(context);
            return;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            StatusBarNotification[] active = manager.getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    if (sbn != null && sbn.getId() == NOTIFICATION_ID) {
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        showNotification(context);
    }

    public static void showNotification(Context context) {
        if (!KeyboardPreferences.isNotificationShortcutsEnabled(context)) {
            cancelNotification(context);
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(context.getString(R.string.notification_channel_desc));
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // Action 1: Show Keyboard anywhere (Hacker's Keyboard mode)
        Intent showKbIntent = new Intent(context, OpenKeyboardActivity.class);
        showKbIntent.setAction(OpenKeyboardActivity.ACTION_FORCE_SHOW_KEYBOARD);
        showKbIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        PendingIntent piShowKeyboard = PendingIntent.getActivity(context, 1, showKbIntent, flags);

        // Action 2: Shortcuts
        Intent shortcutsIntent = new Intent(context, QuickToolsActivity.class);
        shortcutsIntent.putExtra(QuickToolsActivity.EXTRA_TAB, QuickToolsActivity.TAB_SHORTCUTS);
        shortcutsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piShortcuts = PendingIntent.getActivity(context, 2, shortcutsIntent, flags);

        // Action 3: Clipboard
        Intent clipboardIntent = new Intent(context, QuickToolsActivity.class);
        clipboardIntent.putExtra(QuickToolsActivity.EXTRA_TAB, QuickToolsActivity.TAB_CLIPBOARD);
        clipboardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piClipboard = PendingIntent.getActivity(context, 3, clipboardIntent, flags);

        boolean forceAnytime = KeyboardPreferences.isForceKeyboardAnytime(context);
        PendingIntent piContent = forceAnytime ? piShowKeyboard : piShortcuts;
        String contentText = context.getString(
                forceAnytime ? R.string.notification_text_force_open : R.string.notification_text);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setSmallIcon(R.drawable.ic_keyboard)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(contentText)
                .setContentIntent(piContent)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            builder.setCategory(Notification.CATEGORY_SERVICE);
        }

        // Standard Android notifications render up to 3 action buttons cleanly.
        builder.addAction(new Notification.Action.Builder(
                R.drawable.ic_keyboard,
                context.getString(R.string.notification_action_show_keyboard),
                piShowKeyboard).build());
        builder.addAction(new Notification.Action.Builder(
                R.drawable.ic_shortcut,
                context.getString(R.string.notification_action_shortcuts),
                piShortcuts).build());
        builder.addAction(new Notification.Action.Builder(
                R.drawable.ic_clipboard,
                context.getString(R.string.notification_action_clipboard),
                piClipboard).build());

        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Android 13+ if POST_NOTIFICATIONS has not been granted yet
        }
    }

    public static void cancelNotification(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }
}
