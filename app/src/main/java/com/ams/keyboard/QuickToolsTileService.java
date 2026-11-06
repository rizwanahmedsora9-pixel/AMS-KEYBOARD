package com.ams.keyboard;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile in the Android notification panel shade for fast access to AMS Keyboard tools.
 *
 * <p>Acts as an active/inactive toggle showing current AMS Keyboard status, and allows users
 * to summon shortcuts, clipboard history, or quick typing globally from any screen.
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

        boolean isSelected = isAmsSelected();
        tile.setState(isSelected ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_keyboard));
        tile.setLabel(getString(R.string.quick_tile_label));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(isSelected
                    ? getString(R.string.quick_tile_active)
                    : getString(R.string.quick_tile_inactive));
        }

        tile.updateTile();
    }

    private boolean isAmsSelected() {
        try {
            String selectedId = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            return selectedId != null && selectedId.startsWith(getPackageName() + "/");
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void onClick() {
        super.onClick();

        Runnable action = this::launchQuickTools;
        if (isLocked()) {
            unlockAndRun(action);
        } else {
            action.run();
        }
    }

    private void launchQuickTools() {
        Intent intent = new Intent(this, QuickToolsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, intent,
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
                        new ComponentName(context.getApplicationContext(), QuickToolsTileService.class));
            } catch (Exception ignored) {
            }
        }
    }
}
