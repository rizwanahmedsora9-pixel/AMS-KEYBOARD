package com.ams.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ClipDescription;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** AMS Keyboard input method with a reference-inspired toolbar and persistent clipboard history. */
public class AmsKeyboardService extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final String PREFERENCES_NAME = "ams_keyboard_preferences";
    private static final String PREFERENCE_HISTORY_ENABLED = "clipboard_history_enabled";
    private static final int MAX_COMMIT_CHUNK_CHARACTERS = 8192;
    private static final int CUSTOM_KEYBOARD_CODE = -100;
    private static final int CUSTOM_EMOJI_CODE = -101;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ClipboardManager.OnPrimaryClipChangedListener mClipboardListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    capturePrimaryClipboard(false);
                }
            };

    private KeyboardView mKeyboardView;
    private Keyboard mQwertyKeyboard;
    private Keyboard mSymbolsKeyboard;
    private Keyboard mCurrentKeyboard;
    private boolean mCaps;

    private LinearLayout mRootView;
    private View mToolbar;
    private View mNumberHints;
    private LinearLayout mToolsPanel;
    private LinearLayout mEmojiPanel;
    private LinearLayout mClipboardPanel;
    private GridLayout mToolsGrid;
    private GridLayout mEmojiGrid;
    private LinearLayout mClipboardList;
    private TextView mClipboardEmpty;
    private TextView mClipboardCount;
    private Switch mClipboardEnabledSwitch;
    private ImageButton mToolsButton;
    private ImageButton mEmojiButton;
    private ImageButton mClipboardButton;
    private ImageButton mCopyButton;
    private ImageButton mPasteButton;
    private ImageButton mKeyboardButton;

    private ClipboardManager mClipboardManager;
    private ClipboardHistoryStore mClipboardHistory;
    private ExecutorService mHistoryWorker;
    private boolean mHistoryEnabled = true;
    private boolean mClipboardListenerRegistered;
    private boolean mInputViewVisible;
    private boolean mDestroying;
    private String mLastObservedClipboardText;

    @Override
    public void onCreate() {
        super.onCreate();
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        mClipboardHistory = new ClipboardHistoryStore(this);
        mHistoryWorker = Executors.newSingleThreadExecutor();
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        mHistoryEnabled = preferences.getBoolean(PREFERENCE_HISTORY_ENABLED, true);
    }

    @Override
    public View onCreateInputView() {
        mRootView = (LinearLayout) getLayoutInflater().inflate(R.layout.keyboard_view, null);

        mKeyboardView = mRootView.findViewById(R.id.keyboard);
        mToolbar = mRootView.findViewById(R.id.keyboard_toolbar);
        mNumberHints = mRootView.findViewById(R.id.number_hints);
        mToolsPanel = mRootView.findViewById(R.id.tools_panel);
        mEmojiPanel = mRootView.findViewById(R.id.emoji_panel);
        mClipboardPanel = mRootView.findViewById(R.id.clipboard_panel);
        mToolsGrid = mRootView.findViewById(R.id.tools_grid);
        mEmojiGrid = mRootView.findViewById(R.id.emoji_grid);
        mClipboardList = mRootView.findViewById(R.id.clipboard_list);
        mClipboardEmpty = mRootView.findViewById(R.id.clipboard_empty);
        mClipboardCount = mRootView.findViewById(R.id.clipboard_count);
        mClipboardEnabledSwitch = mRootView.findViewById(R.id.clipboard_enabled);
        mToolsButton = mRootView.findViewById(R.id.toolbar_tools);
        mEmojiButton = mRootView.findViewById(R.id.toolbar_emoji);
        mClipboardButton = mRootView.findViewById(R.id.toolbar_clipboard);
        mCopyButton = mRootView.findViewById(R.id.toolbar_copy);
        mPasteButton = mRootView.findViewById(R.id.toolbar_paste);
        mKeyboardButton = mRootView.findViewById(R.id.toolbar_keyboard);

        mQwertyKeyboard = new Keyboard(this, R.xml.keyboard);
        mSymbolsKeyboard = new Keyboard(this, R.xml.keyboard_symbols);
        mCurrentKeyboard = mQwertyKeyboard;
        mKeyboardView.setKeyboard(mCurrentKeyboard);
        mKeyboardView.setOnKeyboardActionListener(this);
        mKeyboardView.setEnabled(true);
        updateLetterKeyLabels();

        mToolsButton.setOnClickListener(view -> showToolsPanel());
        mEmojiButton.setOnClickListener(view -> showEmojiPanel());
        mClipboardButton.setOnClickListener(view -> showClipboardPanel());
        mCopyButton.setOnClickListener(view -> performEditorContextAction(android.R.id.copy));
        mPasteButton.setOnClickListener(view -> pastePrimaryClipboard());
        mKeyboardButton.setOnClickListener(view -> showKeyboardPanel());
        mRootView.findViewById(R.id.clipboard_back)
                .setOnClickListener(view -> showKeyboardPanel());
        mRootView.findViewById(R.id.clipboard_clear)
                .setOnClickListener(view -> clearClipboardHistory());

        // Set the initial value before attaching the listener to avoid writing a false default.
        mClipboardEnabledSwitch.setChecked(mHistoryEnabled);
        mClipboardEnabledSwitch.setContentDescription(mHistoryEnabled
                ? getString(R.string.clipboard_history_on)
                : getString(R.string.clipboard_history_off));
        mClipboardEnabledSwitch.setOnCheckedChangeListener(
                (button, isChecked) -> setClipboardHistoryEnabled(isChecked));

        populateToolsGrid();
        populateEmojiGrid();
        showKeyboardPanel();
        return mRootView;
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        if (mKeyboardView != null) {
            mCurrentKeyboard = mQwertyKeyboard;
            mCaps = false;
            mKeyboardView.setKeyboard(mCurrentKeyboard);
            mKeyboardView.setShifted(false);
            updateLetterKeyLabels();
            showKeyboardPanel();
        }

        mInputViewVisible = true;
        if (mHistoryEnabled) {
            registerClipboardListener();
            // Android only lets an active IME inspect the current clipboard. Save its current
            // plain text too, but don't create another item if it is already the newest one.
            capturePrimaryClipboard(true);
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        mInputViewVisible = false;
        unregisterClipboardListener();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onDestroy() {
        mDestroying = true;
        mInputViewVisible = false;
        unregisterClipboardListener();
        if (mHistoryWorker != null) {
            // Queue the database close after already-submitted history writes/reads.
            if (mClipboardHistory != null) {
                try {
                    mHistoryWorker.execute(() -> mClipboardHistory.close());
                } catch (RejectedExecutionException ignored) {
                    mClipboardHistory.close();
                }
            }
            mHistoryWorker.shutdown();
        }
        super.onDestroy();
    }

    private void showKeyboardPanel() {
        if (mRootView == null) {
            return;
        }
        mToolbar.setVisibility(View.VISIBLE);
        mNumberHints.setVisibility(mCurrentKeyboard == mSymbolsKeyboard ? View.GONE : View.VISIBLE);
        mKeyboardView.setVisibility(View.VISIBLE);
        mToolsPanel.setVisibility(View.GONE);
        mEmojiPanel.setVisibility(View.GONE);
        mClipboardPanel.setVisibility(View.GONE);
        setToolbarSelection(mKeyboardButton);
        mRootView.requestLayout();
    }

    private void showToolsPanel() {
        if (mRootView == null) {
            return;
        }
        mToolbar.setVisibility(View.VISIBLE);
        mNumberHints.setVisibility(View.GONE);
        mKeyboardView.setVisibility(View.GONE);
        mToolsPanel.setVisibility(View.VISIBLE);
        mEmojiPanel.setVisibility(View.GONE);
        mClipboardPanel.setVisibility(View.GONE);
        setToolbarSelection(mToolsButton);
        mRootView.requestLayout();
    }

    private void showEmojiPanel() {
        if (mRootView == null) {
            return;
        }
        mToolbar.setVisibility(View.VISIBLE);
        mNumberHints.setVisibility(View.GONE);
        mKeyboardView.setVisibility(View.GONE);
        mToolsPanel.setVisibility(View.GONE);
        mEmojiPanel.setVisibility(View.VISIBLE);
        mClipboardPanel.setVisibility(View.GONE);
        setToolbarSelection(mEmojiButton);
        mRootView.requestLayout();
    }

    private void showClipboardPanel() {
        if (mRootView == null) {
            return;
        }
        // The reference clipboard view has its own header, so replace the regular toolbar.
        mToolbar.setVisibility(View.GONE);
        mNumberHints.setVisibility(View.GONE);
        mKeyboardView.setVisibility(View.GONE);
        mToolsPanel.setVisibility(View.GONE);
        mEmojiPanel.setVisibility(View.GONE);
        mClipboardPanel.setVisibility(View.VISIBLE);
        refreshClipboardHistoryAsync();
        mRootView.requestLayout();
    }

    private void setToolbarSelection(ImageButton selected) {
        int selectedColor = getResources().getColor(R.color.ime_accent);
        int normalColor = getResources().getColor(R.color.toolbar_icon);
        ImageButton[] buttons = {
                mToolsButton, mEmojiButton, mClipboardButton, mCopyButton, mPasteButton, mKeyboardButton
        };
        for (ImageButton button : buttons) {
            if (button != null) {
                button.setColorFilter(button == selected ? selectedColor : normalColor);
            }
        }
    }

    private void populateToolsGrid() {
        if (mToolsGrid == null || mToolsGrid.getChildCount() > 0) {
            return;
        }
        addToolTile("▣", getString(R.string.tool_clipboard), this::showClipboardPanel);
        addToolTile("↧", getString(R.string.tool_paste), this::pastePrimaryClipboard);
        addToolTile("☷", getString(R.string.tool_select_all),
                () -> performEditorContextAction(android.R.id.selectAll));
        addToolTile("▢", getString(R.string.tool_copy),
                () -> performEditorContextAction(android.R.id.copy));
        addToolTile("✂", getString(R.string.tool_cut),
                () -> performEditorContextAction(android.R.id.cut));
        addToolTile("⌨", getString(R.string.tool_keyboard), this::showKeyboardPanel);
    }

    private void addToolTile(String icon, String label, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(label);
        tile.setOnClickListener(view -> action.run());

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextColor(getResources().getColor(R.color.toolbar_icon));
        iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        iconView.setGravity(Gravity.CENTER);
        tile.addView(iconView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(getResources().getColor(R.color.toolbar_icon));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(2);
        tile.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int index = mToolsGrid.getChildCount();
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1, 1f), GridLayout.spec(index % 3, 1, 1f));
        params.width = 0;
        params.height = 0;
        params.setGravity(Gravity.FILL);
        mToolsGrid.addView(tile, params);
    }

    private void populateEmojiGrid() {
        if (mEmojiGrid == null || mEmojiGrid.getChildCount() > 0) {
            return;
        }
        String[] emojis = {
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🙂",
                "😉", "😊", "😍", "🥰", "😎", "🤔", "😭", "😡",
                "👍", "👎", "👏", "🙏", "❤️", "💯", "🔥", "🎉",
                "✅", "💡", "🌍", "☕", "🐱", "🐶", "✨", "💙"
        };
        for (int index = 0; index < emojis.length; index++) {
            final String emoji = emojis[index];
            TextView cell = new TextView(this);
            cell.setText(emoji);
            cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
            cell.setGravity(Gravity.CENTER);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setContentDescription(emoji);
            cell.setOnClickListener(view -> {
                InputConnection connection = getCurrentInputConnection();
                if (connection != null) {
                    commitTextInChunks(connection, emoji);
                }
                showKeyboardPanel();
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / 8, 1, 1f), GridLayout.spec(index % 8, 1, 1f));
            params.width = 0;
            params.height = dp(42);
            params.setGravity(Gravity.FILL);
            mEmojiGrid.addView(cell, params);
        }
    }

    private void performEditorContextAction(int action) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.performContextMenuAction(action);
        }
        showKeyboardPanel();
    }

    private void pastePrimaryClipboard() {
        String text = getPrimaryClipboardText();
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            if (text != null) {
                commitTextInChunks(connection, text);
            } else {
                connection.performContextMenuAction(android.R.id.paste);
            }
        }
        showKeyboardPanel();
    }

    private void setClipboardHistoryEnabled(boolean enabled) {
        mHistoryEnabled = enabled;
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                .edit().putBoolean(PREFERENCE_HISTORY_ENABLED, enabled).apply();
        if (mClipboardEnabledSwitch != null) {
            mClipboardEnabledSwitch.setContentDescription(enabled
                    ? getString(R.string.clipboard_history_on)
                    : getString(R.string.clipboard_history_off));
        }

        if (enabled && mInputViewVisible) {
            registerClipboardListener();
            capturePrimaryClipboard(true);
        } else if (!enabled) {
            unregisterClipboardListener();
            mLastObservedClipboardText = null;
        }
        if (mClipboardPanel != null && mClipboardPanel.getVisibility() == View.VISIBLE) {
            refreshClipboardHistoryAsync();
        }
    }

    private void registerClipboardListener() {
        if (mClipboardManager == null || mClipboardListenerRegistered) {
            return;
        }
        try {
            mClipboardManager.addPrimaryClipChangedListener(mClipboardListener);
            mClipboardListenerRegistered = true;
        } catch (SecurityException ignored) {
            // Android restricts clipboard reads to the active IME / focused app.
        }
    }

    private void unregisterClipboardListener() {
        if (mClipboardManager == null || !mClipboardListenerRegistered) {
            return;
        }
        try {
            mClipboardManager.removePrimaryClipChangedListener(mClipboardListener);
        } catch (SecurityException ignored) {
            // The system can revoke clipboard access as the IME is being hidden.
        }
        mClipboardListenerRegistered = false;
    }

    private void capturePrimaryClipboard(boolean startupSnapshot) {
        if (mDestroying || !mInputViewVisible || !mHistoryEnabled || mClipboardManager == null) {
            return;
        }
        String text = getPrimaryClipboardTextForHistory();
        if (TextUtils.isEmpty(text)) {
            mLastObservedClipboardText = null;
            return;
        }
        if (text.equals(mLastObservedClipboardText)) {
            return;
        }
        mLastObservedClipboardText = text;

        executeHistoryTask(() -> {
            if (startupSnapshot) {
                mClipboardHistory.addClipIfDifferentFromNewest(text);
            } else {
                mClipboardHistory.addClip(text);
            }
            mMainHandler.post(() -> {
                if (!mDestroying && mClipboardPanel != null
                        && mClipboardPanel.getVisibility() == View.VISIBLE) {
                    refreshClipboardHistoryAsync();
                }
            });
        });
    }

    private String getPrimaryClipboardText() {
        return readPrimaryClipboardText(false);
    }

    private String getPrimaryClipboardTextForHistory() {
        return readPrimaryClipboardText(true);
    }

    private String readPrimaryClipboardText(boolean ignoreSensitiveClip) {
        if (mClipboardManager == null) {
            return null;
        }
        try {
            ClipData clip = mClipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return null;
            }
            if (ignoreSensitiveClip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.os.Bundle extras = clip.getDescription().getExtras();
                if (extras != null && extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) {
                    return null;
                }
            }

            StringBuilder result = new StringBuilder();
            for (int index = 0; index < clip.getItemCount(); index++) {
                CharSequence itemText = clip.getItemAt(index).getText();
                if (itemText == null) {
                    continue;
                }
                if (result.length() > 0) {
                    result.append('\n');
                }
                result.append(itemText);
            }
            return result.length() == 0 ? null : result.toString();
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private void refreshClipboardHistoryAsync() {
        if (mDestroying || mClipboardHistory == null || mHistoryWorker == null) {
            return;
        }
        executeHistoryTask(() -> {
            List<ClipboardHistoryStore.ClipPreview> clips = mClipboardHistory.getRecentPreviews();
            int count = mClipboardHistory.getCount();
            mMainHandler.post(() -> renderClipboardHistory(clips, count));
        });
    }

    private void renderClipboardHistory(List<ClipboardHistoryStore.ClipPreview> clips, int count) {
        if (mDestroying || mClipboardList == null || mClipboardEmpty == null || mClipboardCount == null) {
            return;
        }
        mClipboardCount.setText(getString(R.string.clipboard_count, count,
                ClipboardHistoryStore.MAX_ITEMS));
        mClipboardList.removeAllViews();
        boolean isEmpty = clips.isEmpty();
        mClipboardEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        mClipboardEmpty.setText(mHistoryEnabled
                ? getString(R.string.clipboard_empty)
                : getString(R.string.clipboard_paused));

        for (ClipboardHistoryStore.ClipPreview clip : clips) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(9), dp(12), dp(9));
            card.setMinimumHeight(dp(54));
            android.graphics.drawable.GradientDrawable background =
                    new android.graphics.drawable.GradientDrawable();
            background.setColor(getResources().getColor(R.color.clipboard_card));
            background.setCornerRadius(dp(3));
            card.setBackground(background);
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription(getString(R.string.clipboard_item_description));
            card.setOnClickListener(view -> pasteHistoryClip(clip.id));
            card.setOnTouchListener(createSwipeToDeleteListener(clip.id));

            TextView preview = new TextView(this);
            preview.setText(clip.text);
            preview.setTextColor(getResources().getColor(R.color.clipboard_text));
            preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            preview.setMaxLines(4);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            preview.setClickable(false);
            card.addView(preview, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView detail = new TextView(this);
            if (clip.characterCount > ClipboardHistoryStore.PREVIEW_CHARACTERS) {
                detail.setText(getString(R.string.clipboard_long_text, clip.characterCount));
            } else {
                detail.setText(R.string.clipboard_tap_to_paste);
            }
            detail.setTextColor(getResources().getColor(R.color.clipboard_secondary_text));
            detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            detail.setPadding(0, dp(4), 0, 0);
            card.addView(detail, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(dp(8), dp(6), dp(8), 0);
            mClipboardList.addView(card, cardParams);
        }
    }

    private View.OnTouchListener createSwipeToDeleteListener(long clipId) {
        final float[] start = new float[2];
        final int swipeDistance = dp(72);
        return (view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                start[0] = event.getX();
                start[1] = event.getY();
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float deltaX = event.getX() - start[0];
                float deltaY = event.getY() - start[1];
                if (deltaX < -swipeDistance && Math.abs(deltaX) > Math.abs(deltaY) * 1.2f) {
                    deleteClipboardClip(clipId);
                    return true;
                }
            }
            return false;
        };
    }

    private void pasteHistoryClip(long clipId) {
        executeHistoryTask(() -> {
            String completeText = mClipboardHistory.getClipText(clipId);
            mMainHandler.post(() -> {
                if (mDestroying) {
                    return;
                }
                InputConnection connection = getCurrentInputConnection();
                if (connection != null && completeText != null) {
                    // Read and paste the full database value; only the on-screen preview is shortened.
                    commitTextInChunks(connection, completeText);
                }
                showKeyboardPanel();
            });
        });
    }

    private void deleteClipboardClip(long clipId) {
        executeHistoryTask(() -> {
            mClipboardHistory.deleteClip(clipId);
            refreshClipboardHistoryAsync();
        });
    }

    private void clearClipboardHistory() {
        executeHistoryTask(() -> {
            mClipboardHistory.clear();
            refreshClipboardHistoryAsync();
        });
    }

    private void executeHistoryTask(Runnable task) {
        if (mHistoryWorker == null) {
            return;
        }
        try {
            mHistoryWorker.execute(task);
        } catch (RejectedExecutionException ignored) {
            // The IME is shutting down; there is no UI left to update.
        }
    }

    private void commitTextInChunks(InputConnection connection, CharSequence text) {
        if (connection == null || text == null || text.length() == 0) {
            return;
        }
        boolean batchStarted = false;
        try {
            batchStarted = connection.beginBatchEdit();
            int length = text.length();
            int start = 0;
            while (start < length) {
                int end = Math.min(start + MAX_COMMIT_CHUNK_CHARACTERS, length);
                // Keep a UTF-16 surrogate pair together if a boundary falls between its halves.
                if (end < length && end > start
                        && Character.isHighSurrogate(text.charAt(end - 1))
                        && Character.isLowSurrogate(text.charAt(end))) {
                    end--;
                }
                connection.commitText(text.subSequence(start, end), 1);
                start = end;
            }
        } catch (RuntimeException ignored) {
            // Some host editors reject very large edits; smaller chunks limit binder transactions.
        } finally {
            if (batchStarted) {
                try {
                    connection.endBatchEdit();
                } catch (RuntimeException ignored) {
                    // The host input connection may have disappeared during the paste.
                }
            }
        }
    }

    private void updateLetterKeyLabels() {
        if (mQwertyKeyboard == null) {
            return;
        }
        for (Keyboard.Key key : mQwertyKeyboard.getKeys()) {
            if (key.label != null && key.label.length() == 1) {
                char character = key.label.charAt(0);
                if (Character.isLetter(character)) {
                    key.label = String.valueOf(mCaps
                            ? Character.toUpperCase(character)
                            : Character.toLowerCase(character));
                }
            }
        }
        if (mKeyboardView != null && mKeyboardView.getKeyboard() == mQwertyKeyboard) {
            mKeyboardView.invalidateAllKeys();
        }
    }

    private void handleEnter(InputConnection connection) {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (editorInfo != null) {
            int action = editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_UNSPECIFIED
                    && action != EditorInfo.IME_ACTION_NONE) {
                connection.performEditorAction(action);
                return;
            }
        }
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onPress(int primaryCode) {
        // Press feedback is drawn by the keyboard key background selector.
    }

    @Override
    public void onRelease(int primaryCode) {
        // No-op.
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        switch (primaryCode) {
            case 0: // Center spacer in the letter rows.
                return;
            case Keyboard.KEYCODE_SHIFT:
                mCaps = !mCaps;
                if (mKeyboardView != null) {
                    mKeyboardView.setShifted(mCaps);
                }
                updateLetterKeyLabels();
                return;
            case Keyboard.KEYCODE_MODE_CHANGE:
                mCurrentKeyboard = mSymbolsKeyboard;
                mKeyboardView.setKeyboard(mCurrentKeyboard);
                mNumberHints.setVisibility(View.GONE);
                return;
            case CUSTOM_KEYBOARD_CODE:
                mCurrentKeyboard = mQwertyKeyboard;
                mCaps = false;
                mKeyboardView.setKeyboard(mCurrentKeyboard);
                mKeyboardView.setShifted(false);
                mNumberHints.setVisibility(View.VISIBLE);
                updateLetterKeyLabels();
                return;
            case CUSTOM_EMOJI_CODE:
                showEmojiPanel();
                return;
            default:
                break;
        }

        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            connection.deleteSurroundingText(1, 0);
        } else if (primaryCode == 10) {
            handleEnter(connection);
        } else {
            char character = (char) primaryCode;
            if (Character.isLetter(character) && mCaps) {
                character = Character.toUpperCase(character);
            }
            connection.commitText(String.valueOf(character), 1);
        }
    }

    @Override
    public void onText(CharSequence text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            commitTextInChunks(connection, text);
        }
    }

    @Override
    public void swipeLeft() {
        // Swipe gestures are reserved for dismissing clipboard cards.
    }

    @Override
    public void swipeRight() {
        // No-op.
    }

    @Override
    public void swipeDown() {
        // No-op.
    }

    @Override
    public void swipeUp() {
        // No-op.
    }
}
