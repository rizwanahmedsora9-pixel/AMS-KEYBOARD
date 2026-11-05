package com.ams.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single source of truth for every AMS Keyboard setting.
 *
 * <p>Both the settings screen and the IME service read and write through this class so a change
 * made in the app is visible to the keyboard the next time it is shown, and vice versa.
 */
final class KeyboardPreferences {

    static final String NAME = "ams_keyboard_preferences";

    static final String KEY_AUTO_CORRECT = "auto_correct_enabled";
    static final String KEY_SUGGESTIONS = "suggestions_enabled";
    static final String KEY_AUTO_CAPITALIZE = "auto_capitalize_enabled";
    static final String KEY_DOUBLE_SPACE_PERIOD = "double_space_period_enabled";
    static final String KEY_SOUND = "key_sound_enabled";
    static final String KEY_VIBRATE = "key_vibrate_enabled";
    static final String KEY_KEY_PREVIEW = "key_preview_enabled";
    static final String KEY_KEYBOARD_HEIGHT = "keyboard_height_percent";
    static final String KEY_ROW_GAP = "row_gap_dp";
    static final String KEY_LONG_PRESS_SYMBOLS = "long_press_symbols_enabled";
    static final String KEY_CLIPBOARD_HISTORY = "clipboard_history_enabled";
    static final String KEY_CLIPBOARD_LIMIT = "clipboard_history_limit";
    static final String KEY_ALT_TOOLBAR = "alt_shortcuts_toolbar_enabled";

    static final boolean DEFAULT_AUTO_CORRECT = true;
    static final boolean DEFAULT_SUGGESTIONS = true;
    static final boolean DEFAULT_AUTO_CAPITALIZE = true;
    static final boolean DEFAULT_DOUBLE_SPACE_PERIOD = true;
    static final boolean DEFAULT_SOUND = false;
    static final boolean DEFAULT_VIBRATE = false;
    static final boolean DEFAULT_KEY_PREVIEW = true;
    static final boolean DEFAULT_LONG_PRESS_SYMBOLS = true;
    static final boolean DEFAULT_CLIPBOARD_HISTORY = true;
    static final boolean DEFAULT_ALT_TOOLBAR = false;

    /** 100 means "the height the layout asks for". */
    static final int DEFAULT_KEYBOARD_HEIGHT = 100;
    static final int MIN_KEYBOARD_HEIGHT = 70;
    static final int MAX_KEYBOARD_HEIGHT = 150;

    static final int DEFAULT_ROW_GAP = 3;
    static final int MIN_ROW_GAP = 1;
    static final int MAX_ROW_GAP = 10;

    static final int DEFAULT_CLIPBOARD_LIMIT = 50;
    static final int MIN_CLIPBOARD_LIMIT = 10;
    static final int MAX_CLIPBOARD_LIMIT = 200;
    static final int CLIPBOARD_LIMIT_STEP = 10;

    private KeyboardPreferences() {
    }

    static SharedPreferences get(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean getBoolean(Context context, String key, boolean fallback) {
        return get(context).getBoolean(key, fallback);
    }

    static int getInt(Context context, String key, int fallback) {
        return get(context).getInt(key, fallback);
    }

    static int getKeyboardHeightPercent(Context context) {
        return clamp(getInt(context, KEY_KEYBOARD_HEIGHT, DEFAULT_KEYBOARD_HEIGHT),
                MIN_KEYBOARD_HEIGHT, MAX_KEYBOARD_HEIGHT);
    }

    static int getRowGap(Context context) {
        return clamp(getInt(context, KEY_ROW_GAP, DEFAULT_ROW_GAP), MIN_ROW_GAP, MAX_ROW_GAP);
    }

    static int getClipboardLimit(Context context) {
        return clamp(getInt(context, KEY_CLIPBOARD_LIMIT, DEFAULT_CLIPBOARD_LIMIT),
                MIN_CLIPBOARD_LIMIT, MAX_CLIPBOARD_LIMIT);
    }

    static boolean isAutoCorrectEnabled(Context context) {
        return getBoolean(context, KEY_AUTO_CORRECT, DEFAULT_AUTO_CORRECT);
    }

    static boolean isSuggestionsEnabled(Context context) {
        return getBoolean(context, KEY_SUGGESTIONS, DEFAULT_SUGGESTIONS);
    }

    static boolean isAutoCapitalizeEnabled(Context context) {
        return getBoolean(context, KEY_AUTO_CAPITALIZE, DEFAULT_AUTO_CAPITALIZE);
    }

    static boolean isDoubleSpacePeriodEnabled(Context context) {
        return getBoolean(context, KEY_DOUBLE_SPACE_PERIOD, DEFAULT_DOUBLE_SPACE_PERIOD);
    }

    static boolean isSoundEnabled(Context context) {
        return getBoolean(context, KEY_SOUND, DEFAULT_SOUND);
    }

    static boolean isVibrateEnabled(Context context) {
        return getBoolean(context, KEY_VIBRATE, DEFAULT_VIBRATE);
    }

    static boolean isKeyPreviewEnabled(Context context) {
        return getBoolean(context, KEY_KEY_PREVIEW, DEFAULT_KEY_PREVIEW);
    }

    static boolean isLongPressSymbolsEnabled(Context context) {
        return getBoolean(context, KEY_LONG_PRESS_SYMBOLS, DEFAULT_LONG_PRESS_SYMBOLS);
    }

    static boolean isClipboardHistoryEnabled(Context context) {
        return getBoolean(context, KEY_CLIPBOARD_HISTORY, DEFAULT_CLIPBOARD_HISTORY);
    }

    static boolean isAltToolbarEnabled(Context context) {
        return getBoolean(context, KEY_ALT_TOOLBAR, DEFAULT_ALT_TOOLBAR);
    }

    /** Restores every option to its default. Clipboard contents are intentionally untouched. */
    static void resetToDefaults(Context context) {
        SharedPreferences.Editor editor = get(context).edit();
        editor.putBoolean(KEY_AUTO_CORRECT, DEFAULT_AUTO_CORRECT);
        editor.putBoolean(KEY_SUGGESTIONS, DEFAULT_SUGGESTIONS);
        editor.putBoolean(KEY_AUTO_CAPITALIZE, DEFAULT_AUTO_CAPITALIZE);
        editor.putBoolean(KEY_DOUBLE_SPACE_PERIOD, DEFAULT_DOUBLE_SPACE_PERIOD);
        editor.putBoolean(KEY_SOUND, DEFAULT_SOUND);
        editor.putBoolean(KEY_VIBRATE, DEFAULT_VIBRATE);
        editor.putBoolean(KEY_KEY_PREVIEW, DEFAULT_KEY_PREVIEW);
        editor.putInt(KEY_KEYBOARD_HEIGHT, DEFAULT_KEYBOARD_HEIGHT);
        editor.putInt(KEY_ROW_GAP, DEFAULT_ROW_GAP);
        editor.putBoolean(KEY_LONG_PRESS_SYMBOLS, DEFAULT_LONG_PRESS_SYMBOLS);
        editor.putBoolean(KEY_CLIPBOARD_HISTORY, DEFAULT_CLIPBOARD_HISTORY);
        editor.putInt(KEY_CLIPBOARD_LIMIT, DEFAULT_CLIPBOARD_LIMIT);
        editor.putBoolean(KEY_ALT_TOOLBAR, DEFAULT_ALT_TOOLBAR);
        editor.apply();
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
