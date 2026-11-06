package com.ams.keyboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Manages the ongoing notification for AMS Keyboard in the notification drawer.
 *
 * <p>Allows users to summon shortcuts, view clipboard history, and access quick typing
 * from any screen on their device.
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "ams_keyboard_shortcuts_channel";
    public static final int NOTIFICATION_ID = 2024;

    private NotificationHelper() {
    }

    public static void showNotification(Context context) {
        if (!KeyboardPreferences.isNotificationShortcutsEnabled(context)) {
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

        // Tap on notification body opens QuickToolsActivity with Shortcuts tab
        Intent contentIntent = new Intent(context, QuickToolsActivity.class);
        contentIntent.putExtra(QuickToolsActivity.EXTRA_TAB, QuickToolsActivity.TAB_SHORTCUTS);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piContent = PendingIntent.getActivity(context, 1, contentIntent, flags);

        // Action: Shortcuts
        Intent shortcutsIntent = new Intent(context, QuickToolsActivity.class);
        shortcutsIntent.putExtra(QuickToolsActivity.EXTRA_TAB, QuickToolsActivity.TAB_SHORTCUTS);
        shortcutsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piShortcuts = PendingIntent.getActivity(context, 2, shortcutsIntent, flags);

        // Action: Clipboard
        Intent clipboardIntent = new Intent(context, QuickToolsActivity.class);
        clipboardIntent.putExtra(QuickToolsActivity.EXTRA_TAB, QuickToolsActivity.TAB_CLIPBOARD);
        clipboardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piClipboard = PendingIntent.getActivity(context, 3, clipboardIntent, flags);

        // Action: Quick Type
        Intent typeIntent = new Intent(context, QuickToolsActivity.class);
        typeIntent.putExtra(QuickToolsActivity.EXTRA_TAB, QuickToolsActivity.TAB_TYPE);
        typeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piType = PendingIntent.getActivity(context, 4, typeIntent, flags);

        // Action: Settings
        Intent settingsIntent = new Intent(context, MainActivity.class);
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piSettings = PendingIntent.getActivity(context, 5, settingsIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setSmallIcon(R.drawable.ic_keyboard)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_text))
                .setContentIntent(piContent)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            builder.addAction(new Notification.Action.Builder(
                    R.drawable.ic_shortcut,
                    context.getString(R.string.notification_action_shortcuts),
                    piShortcuts).build());
            builder.addAction(new Notification.Action.Builder(
                    R.drawable.ic_clipboard,
                    context.getString(R.string.notification_action_clipboard),
                    piClipboard).build());
            builder.addAction(new Notification.Action.Builder(
                    R.drawable.ic_keyboard,
                    context.getString(R.string.notification_action_quick_type),
                    piType).build());
            builder.addAction(new Notification.Action.Builder(
                    R.drawable.ic_settings,
                    context.getString(R.string.notification_action_settings),
                    piSettings).build());
        } else {
            builder.addAction(R.drawable.ic_shortcut,
                    context.getString(R.string.notification_action_shortcuts), piShortcuts);
            builder.addAction(R.drawable.ic_clipboard,
                    context.getString(R.string.notification_action_clipboard), piClipboard);
            builder.addAction(R.drawable.ic_keyboard,
                    context.getString(R.string.notification_action_quick_type), piType);
            builder.addAction(R.drawable.ic_settings,
                    context.getString(R.string.notification_action_settings), piSettings);
        }

        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Android 13+ if permission not granted yet
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
