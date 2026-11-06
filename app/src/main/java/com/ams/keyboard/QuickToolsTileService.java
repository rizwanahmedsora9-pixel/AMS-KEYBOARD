package com.ams.keyboard;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile in the Android notification panel shade for fast access to AMS Keyboard.
 *
 * <p>Shows current AMS Keyboard active status and collapses the notification shade to force-open
 * AMS Keyboard anywhere (when enabled) or open Quick Tools.
 */
@TargetApi(Build.VERSION_CODES.N)
public class QuickToolsTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean isSelected = NotificationHelper.isAmsKeyboardSelected(this);
        boolean forceAnytime = KeyboardPreferences.isForceKeyboardAnytime(this);
        tile.setState(isSelected ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_keyboard));
        tile.setLabel(getString(R.string.quick_tile_label));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isSelected) {
                tile.setSubtitle(getString(forceAnytime
                        ? R.string.quick_tile_show_keyboard
                        : R.string.quick_tile_active));
            } else {
                tile.setSubtitle(getString(R.string.quick_tile_inactive));
            }
        }

        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        Runnable action = this::handleTileAction;
        if (isLocked()) {
            unlockAndRun(action);
        } else {
            action.run();
        }
    }

    private void handleTileAction() {
        Intent intent;
        if (KeyboardPreferences.isForceKeyboardAnytime(this)) {
            intent = new Intent(this, OpenKeyboardActivity.class);
            intent.setAction(OpenKeyboardActivity.ACTION_FORCE_SHOW_KEYBOARD);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        } else {
            intent = new Intent(this, QuickToolsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pi = PendingIntent.getActivity(
                    this, 10, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    /**
     * Notifies the system to update the AMS Keyboard Quick Settings tile.
     */
    public static void requestListeningState(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(context.getApplicationContext(),
                        new ComponentName(context.getApplicationContext(),
                                QuickToolsTileService.class));
            } catch (Exception ignored) {
            }
        }
    }
}
