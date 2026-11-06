package com.ams.keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating dialog overlay summoned from the notification panel or quick settings.
 *
 * <p>Provides immediate access from any screen to:
 * <ul>
 *   <li>PC shortcuts (Ctrl+A/C/V/X/Z/Y, Alt keys, cursor moves)</li>
 *   <li>Complete clipboard history with 1-tap copy &amp; search</li>
 *   <li>Quick typing pad with instant "Copy &amp; Exit"</li>
 * </ul>
 */
public class QuickToolsActivity extends Activity {

    public static final String EXTRA_TAB = "extra_tab";
    public static final String TAB_SHORTCUTS = "shortcuts";
    public static final String TAB_CLIPBOARD = "clipboard";
    public static final String TAB_TYPE = "type";

    private String mCurrentTab = TAB_SHORTCUTS;

    private TextView mTabShortcuts;
    private TextView mTabClipboard;
    private TextView mTabQuickType;

    private View mContainerShortcuts;
    private View mContainerClipboard;
    private View mContainerQuickType;

    private EditText mEditShortcutField;
    private EditText mEditClipboardSearch;
    private EditText mEditQuickTypeField;

    private GridView mGridShortcuts;
    private LinearLayout mListClipboardItems;
    private TextView mTextClipboardEmpty;

    private ClipboardManager mClipboardManager;
    private ClipboardHistoryStore mClipboardHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_tools);

        // Window size adjustment for floating dialog
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94f);
        int maxWidth = (int) (520 * getResources().getDisplayMetrics().density);
        if (lp.width > maxWidth) {
            lp.width = maxWidth;
        }
        getWindow().setAttributes(lp);

        mClipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        mClipboardHistory = new ClipboardHistoryStore(this);

        initViews();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_TAB)) {
            String tab = intent.getStringExtra(EXTRA_TAB);
            if (tab != null) {
                selectTab(tab);
                return;
            }
        }
        selectTab(mCurrentTab);
    }

    private void initViews() {
        mTabShortcuts = findViewById(R.id.tab_shortcuts);
        mTabClipboard = findViewById(R.id.tab_clipboard);
        mTabQuickType = findViewById(R.id.tab_quick_type);

        mContainerShortcuts = findViewById(R.id.container_shortcuts);
        mContainerClipboard = findViewById(R.id.container_clipboard);
        mContainerQuickType = findViewById(R.id.container_quick_type);

        mEditShortcutField = findViewById(R.id.edit_shortcut_field);
        mEditClipboardSearch = findViewById(R.id.edit_clipboard_search);
        mEditQuickTypeField = findViewById(R.id.edit_quick_type_field);

        mGridShortcuts = findViewById(R.id.grid_shortcuts);
        mListClipboardItems = findViewById(R.id.list_clipboard_items);
        mTextClipboardEmpty = findViewById(R.id.text_clipboard_empty);

        // Header actions
        findViewById(R.id.btn_close_dialog).setOnClickListener(v -> finish());
        findViewById(R.id.btn_switch_ime).setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });

        // Tabs
        mTabShortcuts.setOnClickListener(v -> selectTab(TAB_SHORTCUTS));
        mTabClipboard.setOnClickListener(v -> selectTab(TAB_CLIPBOARD));
        mTabQuickType.setOnClickListener(v -> selectTab(TAB_TYPE));

        // TAB 1: Shortcuts setup
        initShortcutsTab();

        // TAB 2: Clipboard setup
        initClipboardTab();

        // TAB 3: Quick Type setup
        initQuickTypeTab();
    }

    private void selectTab(String tab) {
        mCurrentTab = tab;

        mTabShortcuts.setBackgroundResource(TAB_SHORTCUTS.equals(tab)
                ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        mTabShortcuts.setTextColor(getResources().getColor(TAB_SHORTCUTS.equals(tab)
                ? R.color.settings_text_primary : R.color.settings_text_secondary));

        mTabClipboard.setBackgroundResource(TAB_CLIPBOARD.equals(tab)
                ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        mTabClipboard.setTextColor(getResources().getColor(TAB_CLIPBOARD.equals(tab)
                ? R.color.settings_text_primary : R.color.settings_text_secondary));

        mTabQuickType.setBackgroundResource(TAB_TYPE.equals(tab)
                ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        mTabQuickType.setTextColor(getResources().getColor(TAB_TYPE.equals(tab)
                ? R.color.settings_text_primary : R.color.settings_text_secondary));

        mContainerShortcuts.setVisibility(TAB_SHORTCUTS.equals(tab) ? View.VISIBLE : View.GONE);
        mContainerClipboard.setVisibility(TAB_CLIPBOARD.equals(tab) ? View.VISIBLE : View.GONE);
        mContainerQuickType.setVisibility(TAB_TYPE.equals(tab) ? View.VISIBLE : View.GONE);

        if (TAB_CLIPBOARD.equals(tab)) {
            refreshClipboardList();
        } else if (TAB_TYPE.equals(tab)) {
            focusQuickTypeField();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && TAB_TYPE.equals(mCurrentTab)) {
            focusQuickTypeField();
        }
    }

    private void focusQuickTypeField() {
        if (mEditQuickTypeField == null) {
            return;
        }
        mEditQuickTypeField.requestFocus();
        mEditQuickTypeField.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(mEditQuickTypeField, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    // ---------------------------------------------------------------- TAB 1: Shortcuts

    private void initShortcutsTab() {
        findViewById(R.id.btn_sc_copy_all).setOnClickListener(v -> copyTextToClipboard(getShortcutFieldText()));
        findViewById(R.id.btn_sc_paste).setOnClickListener(v -> pasteIntoField(mEditShortcutField));
        findViewById(R.id.btn_sc_select_all).setOnClickListener(v -> mEditShortcutField.selectAll());
        findViewById(R.id.btn_sc_clear).setOnClickListener(v -> mEditShortcutField.setText(""));

        List<ShortcutItem> list = new ArrayList<>();
        list.add(new ShortcutItem("Ctrl+A", getString(R.string.shortcut_select_all_desc), () -> mEditShortcutField.selectAll()));
        list.add(new ShortcutItem("Ctrl+C", getString(R.string.shortcut_copy_desc), () -> {
            int start = mEditShortcutField.getSelectionStart();
            int end = mEditShortcutField.getSelectionEnd();
            if (start != end) {
                String sel = mEditShortcutField.getText().subSequence(
                        Math.min(start, end), Math.max(start, end)).toString();
                copyTextToClipboard(sel);
            } else {
                copyTextToClipboard(getShortcutFieldText());
            }
        }));
        list.add(new ShortcutItem("Ctrl+X", getString(R.string.shortcut_cut_desc), () -> {
            int start = mEditShortcutField.getSelectionStart();
            int end = mEditShortcutField.getSelectionEnd();
            if (start != end) {
                int min = Math.min(start, end);
                int max = Math.max(start, end);
                String sel = mEditShortcutField.getText().subSequence(min, max).toString();
                copyTextToClipboard(sel);
                mEditShortcutField.getText().delete(min, max);
            }
        }));
        list.add(new ShortcutItem("Ctrl+V", getString(R.string.shortcut_paste_desc), () -> pasteIntoField(mEditShortcutField)));
        list.add(new ShortcutItem("Ctrl+Z", getString(R.string.shortcut_undo_desc), () -> {
            Editable text = mEditShortcutField.getText();
            if (text.length() > 0) {
                text.delete(text.length() - 1, text.length());
            }
        }));
        list.add(new ShortcutItem(getString(R.string.shortcut_copy_all), getString(R.string.shortcut_copy_all_desc),
                () -> copyTextToClipboard(getShortcutFieldText())));
        list.add(new ShortcutItem("⤒", getString(R.string.shortcut_move_start_desc),
                () -> mEditShortcutField.setSelection(0)));
        list.add(new ShortcutItem("⤓", getString(R.string.shortcut_move_end_desc),
                () -> mEditShortcutField.setSelection(mEditShortcutField.length())));
        list.add(new ShortcutItem("◧", getString(R.string.shortcut_select_word_desc), () -> {
            int pos = mEditShortcutField.getSelectionStart();
            String text = mEditShortcutField.getText().toString();
            if (pos >= 0 && pos <= text.length()) {
                int start = pos;
                while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
                    start--;
                }
                int end = pos;
                while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                    end++;
                }
                mEditShortcutField.setSelection(start, end);
            }
        }));

        // Alt letters
        for (char c = 'A'; c <= 'Z'; c++) {
            final String letter = String.valueOf(c);
            list.add(new ShortcutItem("Alt+" + letter, "Alt shortcut " + letter, () -> {
                int cursor = Math.max(0, mEditShortcutField.getSelectionStart());
                mEditShortcutField.getText().insert(cursor, letter);
                Toast.makeText(QuickToolsActivity.this, "Alt+" + letter + " sent", Toast.LENGTH_SHORT).show();
            }));
        }

        mGridShortcuts.setAdapter(new ShortcutGridAdapter(this, list));
    }

    private String getShortcutFieldText() {
        return mEditShortcutField.getText() != null ? mEditShortcutField.getText().toString() : "";
    }

    // ---------------------------------------------------------------- TAB 2: Clipboard

    private void initClipboardTab() {
        mEditClipboardSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterClipboardList(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        findViewById(R.id.btn_clipboard_clear_all).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.clipboard_clear)
                .setMessage(R.string.setting_reset_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clipboard_clear, (dialog, which) -> {
                    mClipboardHistory.clear();
                    refreshClipboardList();
                    Toast.makeText(this, R.string.setting_reset_done, Toast.LENGTH_SHORT).show();
                })
                .show());
    }

    private void refreshClipboardList() {
        filterClipboardList(mEditClipboardSearch.getText() != null
                ? mEditClipboardSearch.getText().toString() : "");
    }

    private void filterClipboardList(String filter) {
        mListClipboardItems.removeAllViews();
        List<ClipboardHistoryStore.ClipPreview> all = mClipboardHistory.getRecentPreviews();
        if (all == null || all.isEmpty()) {
            mTextClipboardEmpty.setVisibility(View.VISIBLE);
            mTextClipboardEmpty.setText(R.string.clipboard_empty);
            return;
        }

        int shown = 0;
        String query = filter.trim().toLowerCase();
        for (ClipboardHistoryStore.ClipPreview preview : all) {
            if (!query.isEmpty() && !preview.text.toLowerCase().contains(query)) {
                continue;
            }
            shown++;
            addClipboardItemView(preview);
        }

        if (shown == 0) {
            mTextClipboardEmpty.setVisibility(View.VISIBLE);
            mTextClipboardEmpty.setText(R.string.clipboard_history_empty_search);
        } else {
            mTextClipboardEmpty.setVisibility(View.GONE);
        }
    }

    private void addClipboardItemView(ClipboardHistoryStore.ClipPreview preview) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.bg_clipboard_item);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(10);
        row.setPadding(p, p, p, p);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        // Content
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(colParams);

        TextView clipText = new TextView(this);
        clipText.setText(preview.text);
        clipText.setTextColor(getResources().getColor(R.color.settings_text_primary));
        clipText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        clipText.setMaxLines(3);
        clipText.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(clipText);

        TextView charCount = new TextView(this);
        charCount.setText(getString(R.string.clipboard_long_text, preview.characterCount));
        charCount.setTextColor(getResources().getColor(R.color.key_hint));
        charCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        charCount.setPadding(0, dp(3), 0, 0);
        textCol.addView(charCount);

        row.addView(textCol);

        // Copy on tap
        row.setOnClickListener(v -> {
            String full = mClipboardHistory.getClipText(preview.id);
            if (full != null) {
                copyTextToClipboard(full);
                Toast.makeText(QuickToolsActivity.this, R.string.clipboard_copied, Toast.LENGTH_SHORT).show();
            }
        });

        // Delete button
        ImageButton del = new ImageButton(this);
        del.setImageResource(R.drawable.ic_delete);
        del.setBackgroundResource(android.R.color.transparent);
        del.setColorFilter(getResources().getColor(R.color.key_hint));
        del.setContentDescription(getString(R.string.shortcut_cut_desc));
        int btnP = dp(8);
        del.setPadding(btnP, btnP, btnP, btnP);
        del.setOnClickListener(v -> {
            mClipboardHistory.deleteClip(preview.id);
            refreshClipboardList();
            Toast.makeText(QuickToolsActivity.this, R.string.clipboard_deleted, Toast.LENGTH_SHORT).show();
        });
        row.addView(del);

        mListClipboardItems.addView(row);
    }

    // ---------------------------------------------------------------- TAB 3: Quick Type

    private void initQuickTypeTab() {
        findViewById(R.id.btn_type_copy_and_exit).setOnClickListener(v -> {
            String text = mEditQuickTypeField.getText() != null
                    ? mEditQuickTypeField.getText().toString() : "";
            if (!text.isEmpty()) {
                copyTextToClipboard(text);
                mClipboardHistory.addClip(text);
                Toast.makeText(this, R.string.quick_type_copy_done, Toast.LENGTH_SHORT).show();
            }
            finish();
        });

        findViewById(R.id.btn_type_copy).setOnClickListener(v -> {
            String text = mEditQuickTypeField.getText() != null
                    ? mEditQuickTypeField.getText().toString() : "";
            copyTextToClipboard(text);
            if (!text.isEmpty()) {
                mClipboardHistory.addClip(text);
                Toast.makeText(this, R.string.quick_type_copy_done, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_type_paste).setOnClickListener(v -> pasteIntoField(mEditQuickTypeField));
        findViewById(R.id.btn_type_clear).setOnClickListener(v -> mEditQuickTypeField.setText(""));
    }

    // ---------------------------------------------------------------- Clipboard utilities

    private void copyTextToClipboard(String text) {
        if (text == null) {
            text = "";
        }
        ClipData clip = ClipData.newPlainText("AMS Keyboard", text);
        if (mClipboardManager != null) {
            mClipboardManager.setPrimaryClip(clip);
            Toast.makeText(this, R.string.clipboard_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteIntoField(EditText field) {
        if (mClipboardManager != null && mClipboardManager.hasPrimaryClip()) {
            ClipData clip = mClipboardManager.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(this);
                if (text != null) {
                    int cursor = Math.max(0, field.getSelectionStart());
                    field.getText().insert(cursor, text);
                }
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- Shortcuts Adapter

    static final class ShortcutItem {
        final String cap;
        final String desc;
        final Runnable action;

        ShortcutItem(String cap, String desc, Runnable action) {
            this.cap = cap;
            this.desc = desc;
            this.action = action;
        }
    }

    private static class ShortcutGridAdapter extends BaseAdapter {
        private final Context mContext;
        private final List<ShortcutItem> mItems;

        ShortcutGridAdapter(Context context, List<ShortcutItem> items) {
            mContext = context;
            mItems = items;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public ShortcutItem getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ShortcutItem item = getItem(position);
            LinearLayout tile = new LinearLayout(mContext);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setBackgroundResource(R.drawable.bg_shortcut_key);
            int p = Math.round(5 * mContext.getResources().getDisplayMetrics().density);
            tile.setPadding(p, p, p, p);
            tile.setContentDescription(item.cap + ", " + item.desc);

            TextView cap = new TextView(mContext);
            cap.setText(item.cap);
            cap.setTextColor(mContext.getResources().getColor(R.color.key_text));
            cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            cap.setGravity(Gravity.CENTER);
            cap.setSingleLine(true);
            tile.addView(cap);

            TextView desc = new TextView(mContext);
            desc.setText(item.desc);
            desc.setTextColor(mContext.getResources().getColor(R.color.key_hint));
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            desc.setGravity(Gravity.CENTER);
            desc.setSingleLine(true);
            tile.addView(desc);

            tile.setOnClickListener(v -> item.action.run());
            return tile;
        }
    }
}
