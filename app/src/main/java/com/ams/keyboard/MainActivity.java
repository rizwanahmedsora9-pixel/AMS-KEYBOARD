package com.ams.keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import java.util.List;

/**
 * Launcher screen that doubles as the AMS Keyboard settings panel.
 *
 * <p>Everything the keyboard does is controlled from here: auto-correct, the suggestions bar,
 * key feedback, keyboard height, row spacing, clipboard behaviour and the shortcut toolbar. The
 * IME reads the same {@link KeyboardPreferences} file, so a change applies the next time the
 * keyboard is shown - no restart required.
 */
public class MainActivity extends Activity {

    public static final String EXTRA_OPEN_CLIPBOARD = "open_clipboard";

    private TextView mStatusPill;
    private TextView mVersionLabel;
    private TextView mHeightValue;
    private TextView mRowGapValue;
    private TextView mClipboardLimitValue;
    private SeekBar mHeightSeek;
    private SeekBar mRowGapSeek;
    private SeekBar mClipboardLimitSeek;
    private Button mEnableButton;
    private Button mSelectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStatusPill = findViewById(R.id.status_pill);
        mVersionLabel = findViewById(R.id.version_label);
        mHeightValue = findViewById(R.id.height_value);
        mRowGapValue = findViewById(R.id.row_gap_value);
        mClipboardLimitValue = findViewById(R.id.clipboard_limit_value);
        mHeightSeek = findViewById(R.id.seek_height);
        mRowGapSeek = findViewById(R.id.seek_row_gap);
        mClipboardLimitSeek = findViewById(R.id.seek_clipboard_limit);
        mEnableButton = findViewById(R.id.button_enable);
        mSelectButton = findViewById(R.id.button_select);

        showVersion();
        bindSetupButtons();
        bindSwitches();
        bindSliders();
        bindDangerActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSetupStatus();
        syncControlsFromPreferences();
    }

    // ---------------------------------------------------------------- setup status

    private void showVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            mVersionLabel.setText(getString(R.string.version_label,
                    info.versionName, info.versionCode));
        } catch (PackageManager.NameNotFoundException ignored) {
            mVersionLabel.setText(getString(R.string.app_name));
        }
    }

    private void bindSetupButtons() {
        mEnableButton.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        mSelectButton.setOnClickListener(view -> {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                try {
                    manager.showInputMethodPicker();
                    return;
                } catch (RuntimeException ignored) {
                    // Fall through to the system settings screen.
                }
            }
            try {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, R.string.setup_needs_select, Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.button_refresh).setOnClickListener(view -> {
            refreshSetupStatus();
            Toast.makeText(this, R.string.setup_refresh, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.button_test).setOnClickListener(view -> showTestField());
    }

    private void showTestField() {
        EditText field = new EditText(this);
        field.setHint(R.string.setup_open_keyboard_button);
        field.setMinLines(4);
        field.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        field.setPadding(28, 28, 28, 28);
        new AlertDialog.Builder(this)
                .setTitle(R.string.setup_title)
                .setView(field)
                .setPositiveButton(R.string.close, null)
                .show();
        field.requestFocus();
        field.post(() -> {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /** Reads the real state of the IME: enabled in system settings, and currently selected. */
    private void refreshSetupStatus() {
        boolean enabled = false;
        boolean selected = false;
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            List<InputMethodInfo> enabledMethods = manager.getEnabledInputMethodList();
            if (enabledMethods != null) {
                for (InputMethodInfo info : enabledMethods) {
                    if (getPackageName().equals(info.getPackageName())) {
                        enabled = true;
                        break;
                    }
                }
            }
            String selectedId = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            selected = selectedId != null && selectedId.startsWith(getPackageName() + "/");
        }

        if (enabled && selected) {
            setPill(R.string.setup_active, false);
            mEnableButton.setText(R.string.setup_enable_button);
            mSelectButton.setText(R.string.setup_select_button);
        } else if (enabled) {
            setPill(R.string.setup_needs_select, true);
            mSelectButton.setText(R.string.setup_select_button);
        } else {
            setPill(R.string.setup_needs_enable, true);
        }
    }

    private void setPill(int textRes, boolean warn) {
        mStatusPill.setText(textRes);
        mStatusPill.setBackgroundResource(
                warn ? R.drawable.bg_settings_pill_warn : R.drawable.bg_settings_pill);
    }

    // ---------------------------------------------------------------- settings rows

    private void bindSwitches() {
        bindSwitch(R.id.row_auto_correct, KeyboardPreferences.KEY_AUTO_CORRECT,
                KeyboardPreferences.DEFAULT_AUTO_CORRECT,
                R.string.setting_auto_correct_title, R.string.setting_auto_correct_summary);
        bindSwitch(R.id.row_suggestions, KeyboardPreferences.KEY_SUGGESTIONS,
                KeyboardPreferences.DEFAULT_SUGGESTIONS,
                R.string.setting_suggestions_title, R.string.setting_suggestions_summary);
        bindSwitch(R.id.row_auto_capitalize, KeyboardPreferences.KEY_AUTO_CAPITALIZE,
                KeyboardPreferences.DEFAULT_AUTO_CAPITALIZE,
                R.string.setting_auto_capitalize_title, R.string.setting_auto_capitalize_summary);
        bindSwitch(R.id.row_double_space, KeyboardPreferences.KEY_DOUBLE_SPACE_PERIOD,
                KeyboardPreferences.DEFAULT_DOUBLE_SPACE_PERIOD,
                R.string.setting_double_space_period_title,
                R.string.setting_double_space_period_summary);
        bindSwitch(R.id.row_long_press, KeyboardPreferences.KEY_LONG_PRESS_SYMBOLS,
                KeyboardPreferences.DEFAULT_LONG_PRESS_SYMBOLS,
                R.string.setting_long_press_symbols_title,
                R.string.setting_long_press_symbols_summary);
        bindSwitch(R.id.row_sound, KeyboardPreferences.KEY_SOUND,
                KeyboardPreferences.DEFAULT_SOUND,
                R.string.setting_sound_title, R.string.setting_sound_summary);
        bindSwitch(R.id.row_vibrate, KeyboardPreferences.KEY_VIBRATE,
                KeyboardPreferences.DEFAULT_VIBRATE,
                R.string.setting_vibrate_title, R.string.setting_vibrate_summary);
        bindSwitch(R.id.row_preview, KeyboardPreferences.KEY_KEY_PREVIEW,
                KeyboardPreferences.DEFAULT_KEY_PREVIEW,
                R.string.setting_key_preview_title, R.string.setting_key_preview_summary);
        bindSwitch(R.id.row_alt_toolbar, KeyboardPreferences.KEY_ALT_TOOLBAR,
                KeyboardPreferences.DEFAULT_ALT_TOOLBAR,
                R.string.setting_alt_tools_title, R.string.setting_alt_tools_summary);
        bindSwitch(R.id.row_clipboard_history, KeyboardPreferences.KEY_CLIPBOARD_HISTORY,
                KeyboardPreferences.DEFAULT_CLIPBOARD_HISTORY,
                R.string.setting_clipboard_history_title,
                R.string.setting_clipboard_history_summary);
    }

    private void bindSwitch(int rowId, String key, boolean fallback, int titleRes, int summaryRes) {
        View row = findViewById(rowId);
        if (row == null) {
            return;
        }
        TextView title = row.findViewById(R.id.setting_title);
        TextView summary = row.findViewById(R.id.setting_summary);
        Switch toggle = row.findViewById(R.id.setting_switch);
        title.setText(titleRes);
        summary.setText(summaryRes);
        boolean value = KeyboardPreferences.getBoolean(this, key, fallback);
        toggle.setChecked(value);
        Runnable apply = () -> {
            KeyboardPreferences.get(this).edit().putBoolean(key, toggle.isChecked()).apply();
            if (KeyboardPreferences.KEY_CLIPBOARD_HISTORY.equals(key)) {
                ClipboardHistoryStore.applyLimit(this,
                        KeyboardPreferences.getClipboardLimit(this));
            }
        };
        toggle.setOnCheckedChangeListener((button, checked) -> apply.run());
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
    }

    private void bindSliders() {
        mHeightSeek.setMax(KeyboardPreferences.MAX_KEYBOARD_HEIGHT
                - KeyboardPreferences.MIN_KEYBOARD_HEIGHT);
        mRowGapSeek.setMax(KeyboardPreferences.MAX_ROW_GAP - KeyboardPreferences.MIN_ROW_GAP);
        int limitSteps = (KeyboardPreferences.MAX_CLIPBOARD_LIMIT
                - KeyboardPreferences.MIN_CLIPBOARD_LIMIT) / KeyboardPreferences.CLIPBOARD_LIMIT_STEP;
        mClipboardLimitSeek.setMax(Math.max(1, limitSteps));

        mHeightSeek.setOnSeekBarChangeListener(simpleListener(progress -> {
            int percent = KeyboardPreferences.MIN_KEYBOARD_HEIGHT + progress;
            KeyboardPreferences.get(this).edit()
                    .putInt(KeyboardPreferences.KEY_KEYBOARD_HEIGHT, percent).apply();
            mHeightValue.setText(getString(R.string.setting_keyboard_height_value, percent));
        }));
        mRowGapSeek.setOnSeekBarChangeListener(simpleListener(progress -> {
            int gap = KeyboardPreferences.MIN_ROW_GAP + progress;
            KeyboardPreferences.get(this).edit()
                    .putInt(KeyboardPreferences.KEY_ROW_GAP, gap).apply();
            mRowGapValue.setText(getString(R.string.setting_row_gap_value, gap));
        }));
        mClipboardLimitSeek.setOnSeekBarChangeListener(simpleListener(progress -> {
            int limit = KeyboardPreferences.MIN_CLIPBOARD_LIMIT
                    + progress * KeyboardPreferences.CLIPBOARD_LIMIT_STEP;
            limit = KeyboardPreferences.clamp(limit, KeyboardPreferences.MIN_CLIPBOARD_LIMIT,
                    KeyboardPreferences.MAX_CLIPBOARD_LIMIT);
            KeyboardPreferences.get(this).edit()
                    .putInt(KeyboardPreferences.KEY_CLIPBOARD_LIMIT, limit).apply();
            mClipboardLimitValue.setText(
                    getString(R.string.setting_clipboard_limit_value, limit));
            ClipboardHistoryStore.applyLimit(this, limit);
        }));
    }

    private interface ProgressConsumer {
        void accept(int progress);
    }

    private SeekBar.OnSeekBarChangeListener simpleListener(ProgressConsumer consumer) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                consumer.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
    }

    /** Pushes the stored values into every control; called on resume so nothing is stale. */
    private void syncControlsFromPreferences() {
        setSwitch(R.id.row_auto_correct, KeyboardPreferences.isAutoCorrectEnabled(this));
        setSwitch(R.id.row_suggestions, KeyboardPreferences.isSuggestionsEnabled(this));
        setSwitch(R.id.row_auto_capitalize, KeyboardPreferences.isAutoCapitalizeEnabled(this));
        setSwitch(R.id.row_double_space, KeyboardPreferences.isDoubleSpacePeriodEnabled(this));
        setSwitch(R.id.row_long_press, KeyboardPreferences.isLongPressSymbolsEnabled(this));
        setSwitch(R.id.row_sound, KeyboardPreferences.isSoundEnabled(this));
        setSwitch(R.id.row_vibrate, KeyboardPreferences.isVibrateEnabled(this));
        setSwitch(R.id.row_preview, KeyboardPreferences.isKeyPreviewEnabled(this));
        setSwitch(R.id.row_alt_toolbar, KeyboardPreferences.isAltToolbarEnabled(this));
        setSwitch(R.id.row_clipboard_history, KeyboardPreferences.isClipboardHistoryEnabled(this));

        int height = KeyboardPreferences.getKeyboardHeightPercent(this);
        mHeightSeek.setProgress(height - KeyboardPreferences.MIN_KEYBOARD_HEIGHT);
        mHeightValue.setText(getString(R.string.setting_keyboard_height_value, height));

        int gap = KeyboardPreferences.getRowGap(this);
        mRowGapSeek.setProgress(gap - KeyboardPreferences.MIN_ROW_GAP);
        mRowGapValue.setText(getString(R.string.setting_row_gap_value, gap));

        int limit = KeyboardPreferences.getClipboardLimit(this);
        mClipboardLimitSeek.setProgress(
                (limit - KeyboardPreferences.MIN_CLIPBOARD_LIMIT)
                        / KeyboardPreferences.CLIPBOARD_LIMIT_STEP);
        mClipboardLimitValue.setText(getString(R.string.setting_clipboard_limit_value, limit));
    }

    private void setSwitch(int rowId, boolean checked) {
        View row = findViewById(rowId);
        if (row == null) {
            return;
        }
        Switch toggle = row.findViewById(R.id.setting_switch);
        if (toggle != null && toggle.isChecked() != checked) {
            toggle.setChecked(checked);
        }
    }

    private void bindDangerActions() {
        findViewById(R.id.button_clear_all_clips).setOnClickListener(view ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.clipboard_clear)
                        .setMessage(R.string.setting_reset_confirm_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.clipboard_clear, (dialog, which) -> {
                            ClipboardHistoryStore.clearAll(this);
                            Toast.makeText(this, R.string.setting_reset_done,
                                    Toast.LENGTH_SHORT).show();
                        })
                        .show());

        findViewById(R.id.button_reset).setOnClickListener(view ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.setting_reset_confirm)
                        .setMessage(R.string.setting_reset_confirm_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.reset, (dialog, which) -> {
                            KeyboardPreferences.resetToDefaults(this);
                            syncControlsFromPreferences();
                            Toast.makeText(this, R.string.setting_reset_done,
                                    Toast.LENGTH_SHORT).show();
                        })
                        .show());
    }
}
