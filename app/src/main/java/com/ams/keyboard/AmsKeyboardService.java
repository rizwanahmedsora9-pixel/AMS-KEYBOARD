package com.ams.keyboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * AMS Keyboard input method.
 *
 * <p>Highlights:
 * <ul>
 *   <li>Sticky Shift / Ctrl / Alt modifiers that behave like a PC keyboard (Ctrl+A, Ctrl+C,
 *       Ctrl+V, Ctrl+X, Ctrl+Z and Alt+letter are all sent as real {@link KeyEvent}s).</li>
 *   <li>"Copy all" reads the whole editor through {@link #getAllEditorText()} instead of relying
 *       on a selection, so unlimited text can be copied even when nothing is highlighted.</li>
 *   <li>Auto-correct plus a suggestion strip driven by {@link WordDictionary}.</li>
 *   <li>Every option is read from {@link KeyboardPreferences}, so the settings screen controls the
 *       live keyboard.</li>
 * </ul>
 */
public class AmsKeyboardService extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    // Custom key codes used by the XML layouts.
    private static final int KEYCODE_CTRL = 1001;
    private static final int KEYCODE_ALT = 1002;
    private static final int CUSTOM_SYMBOLS_CODE = -100;
    private static final int CUSTOM_EMOJI_CODE = -1001;

    // Shortcut identifiers shared with ShortcutPadView.
    static final int SHORTCUT_SELECT_ALL = 1;
    static final int SHORTCUT_COPY = 2;
    static final int SHORTCUT_CUT = 3;
    static final int SHORTCUT_PASTE = 4;
    static final int SHORTCUT_UNDO = 5;
    static final int SHORTCUT_REDO = 6;
    static final int SHORTCUT_COPY_ALL = 7;
    static final int SHORTCUT_MOVE_START = 8;
    static final int SHORTCUT_MOVE_END = 9;
    static final int SHORTCUT_SELECT_WORD = 10;
    static final int SHORTCUT_ALT_LETTER = 11;

    private static final int MAX_COMMIT_CHUNK_CHARACTERS = 8192;
    private static final int SUGGESTION_COUNT = 3;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ClipboardManager.OnPrimaryClipChangedListener mClipboardListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    capturePrimaryClipboard(false);
                }
            };

    // Views
    private LinearLayout mRootView;
    private View mKeyboardArea;
    private KeyboardView mKeyboardView;
    private LinearLayout mSuggestionStrip;
    private LinearLayout mSuggestionRow;
    private TextView mSuggestionEmpty;
    private LinearLayout mQuickShortcutRow;
    private View mToolbar;
    private LinearLayout mToolsPanel;
    private LinearLayout mShortcutPanel;
    private LinearLayout mEmojiPanel;
    private LinearLayout mClipboardPanel;
    private GridLayout mToolsGrid;
    private GridLayout mEmojiGrid;
    private GridView mShortcutGrid;
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
    private ImageButton mShortcutsButton;
    private ImageButton mSettingsButton;
    private ImageButton mToolbarSettingsButton;

    // Keyboards
    private AmsKeyboard mQwertyKeyboard;
    private AmsKeyboard mSymbolsKeyboard;
    private AmsKeyboard mCurrentKeyboard;
    private boolean mCaps;
    private boolean mShiftLatched;
    private boolean mCtrlLatched;
    private boolean mAltLatched;

    // Settings snapshot, refreshed whenever the keyboard is shown.
    private boolean mAutoCorrectEnabled;
    private boolean mSuggestionsEnabled;
    private boolean mAutoCapitalizeEnabled;
    private boolean mDoubleSpaceEnabled;
    private boolean mSoundEnabled;
    private boolean mVibrateEnabled;
    private boolean mPreviewEnabled;
    private boolean mLongPressEnabled;
    private boolean mAltToolbarEnabled;
    private int mKeyboardHeightPercent = KeyboardPreferences.DEFAULT_KEYBOARD_HEIGHT;
    private int mRowGapDp = KeyboardPreferences.DEFAULT_ROW_GAP;

    // Typing state
    private WordDictionary mDictionary;
    private final StringBuilder mCurrentWord = new StringBuilder();
    private long mLastShiftTapAt;
    private long mLastSpaceAt;
    private boolean mDoubleSpaceArmed;
    private boolean mComposingSuggestion;
    private boolean mShiftWasAuto;

    // Clipboard
    private ClipboardManager mClipboardManager;
    private ClipboardHistoryStore mClipboardHistory;
    private ExecutorService mHistoryWorker;
    private boolean mHistoryEnabled = true;
    private boolean mClipboardListenerRegistered;
    private boolean mInputViewVisible;
    private boolean mDestroying;
    private String mLastObservedClipboardText;
    private int mClipboardLimit = KeyboardPreferences.DEFAULT_CLIPBOARD_LIMIT;

    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener =
            (sharedPreferences, key) -> mMainHandler.post(this::onPreferenceChanged);

    private Vibrator mVibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        mClipboardHistory = new ClipboardHistoryStore(this);
        mHistoryWorker = Executors.newSingleThreadExecutor();
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        readPreferences();
        KeyboardPreferences.get(this).registerOnSharedPreferenceChangeListener(mPreferenceListener);
    }

    @Override
    public View onCreateInputView() {
        mRootView = (LinearLayout) getLayoutInflater()
                .inflate(R.layout.keyboard_view, null);

        mKeyboardArea = mRootView.findViewById(R.id.keyboard_area);
        mKeyboardView = mRootView.findViewById(R.id.keyboard);
        mSuggestionStrip = mRootView.findViewById(R.id.suggestion_strip);
        mSuggestionRow = mRootView.findViewById(R.id.suggestion_row);
        mSuggestionEmpty = mRootView.findViewById(R.id.suggestion_empty);
        mQuickShortcutRow = mRootView.findViewById(R.id.quick_shortcut_row);
        mSettingsButton = mRootView.findViewById(R.id.suggestion_settings);
        mToolbar = mRootView.findViewById(R.id.keyboard_toolbar);
        mToolsPanel = mRootView.findViewById(R.id.tools_panel);
        mShortcutPanel = mRootView.findViewById(R.id.shortcut_panel);
        mEmojiPanel = mRootView.findViewById(R.id.emoji_panel);
        mClipboardPanel = mRootView.findViewById(R.id.clipboard_panel);
        mToolsGrid = mRootView.findViewById(R.id.tools_grid);
        mEmojiGrid = mRootView.findViewById(R.id.emoji_grid);
        mShortcutGrid = mRootView.findViewById(R.id.shortcut_grid);
        mClipboardList = mRootView.findViewById(R.id.clipboard_list);
        mClipboardEmpty = mRootView.findViewById(R.id.clipboard_empty);
        mClipboardCount = mRootView.findViewById(R.id.clipboard_count);
        mClipboardEnabledSwitch = mRootView.findViewById(R.id.clipboard_enabled);
        mToolsButton = mRootView.findViewById(R.id.toolbar_tools);
        mEmojiButton = mRootView.findViewById(R.id.toolbar_emoji);
        mClipboardButton = mRootView.findViewById(R.id.toolbar_clipboard);
        mShortcutsButton = mRootView.findViewById(R.id.toolbar_shortcuts);
        mCopyButton = mRootView.findViewById(R.id.toolbar_copy);
        mPasteButton = mRootView.findViewById(R.id.toolbar_paste);
        mKeyboardButton = mRootView.findViewById(R.id.toolbar_keyboard);
        mToolbarSettingsButton = mRootView.findViewById(R.id.toolbar_settings);

        mDictionary = WordDictionary.getInstance(this);

        buildKeyboards();
        bindToolbar();
        bindClipboardControls();
        populateToolsGrid();
        populateEmojiGrid();
        mShortcutGrid.setAdapter(new ShortcutPadView(this, this));
        applyPreferencesToViews();
        // Panels sit on top of the keyboard inside this frame; keep its height stable.
        if (mKeyboardArea != null) {
            mKeyboardArea.setMinimumHeight(dp(4 * 49 + 3 * 3 + 8));
        }
        showKeyboardPanel();
        return mRootView;
    }

    // ---------------------------------------------------------------- keyboards

    private void buildKeyboards() {
        mQwertyKeyboard = new AmsKeyboard(this, R.xml.keyboard);
        mSymbolsKeyboard = new AmsKeyboard(this, R.xml.keyboard_symbols);
        decorateModifierKeys(mQwertyKeyboard);
        decorateModifierKeys(mSymbolsKeyboard);
        mCurrentKeyboard = mQwertyKeyboard;
        mKeyboardView.setKeyboard(mCurrentKeyboard);
        mKeyboardView.setOnKeyboardActionListener(this);
        mKeyboardView.setEnabled(true);
        mKeyboardView.setPreviewEnabled(mPreviewEnabled);
        applyKeyboardGeometry();
        updateModifierAppearance();
    }

    private void decorateModifierKeys(AmsKeyboard keyboard) {
        for (Keyboard.Key key : keyboard.getKeys()) {
            if (key.codes == null || key.codes.length == 0) {
                continue;
            }
            int code = key.codes[0];
            switch (code) {
                case Keyboard.KEYCODE_SHIFT:
                    key.icon = getDrawable(R.drawable.ic_shift);
                    key.iconPreview = null;
                    break;
                case KEYCODE_CTRL:
                case KEYCODE_ALT:
                    key.sticky = true;
                    break;
                case Keyboard.KEYCODE_DELETE:
                    key.icon = getDrawable(R.drawable.ic_backspace_wide);
                    break;
                case 10:
                    key.icon = getDrawable(R.drawable.ic_enter);
                    break;
                default:
                    break;
            }
        }
    }

    private void readPreferences() {
        mAutoCorrectEnabled = KeyboardPreferences.isAutoCorrectEnabled(this);
        mSuggestionsEnabled = KeyboardPreferences.isSuggestionsEnabled(this);
        mAutoCapitalizeEnabled = KeyboardPreferences.isAutoCapitalizeEnabled(this);
        mDoubleSpaceEnabled = KeyboardPreferences.isDoubleSpacePeriodEnabled(this);
        mSoundEnabled = KeyboardPreferences.isSoundEnabled(this);
        mVibrateEnabled = KeyboardPreferences.isVibrateEnabled(this);
        mPreviewEnabled = KeyboardPreferences.isKeyPreviewEnabled(this);
        mLongPressEnabled = KeyboardPreferences.isLongPressSymbolsEnabled(this);
        mAltToolbarEnabled = KeyboardPreferences.isAltToolbarEnabled(this);
        mKeyboardHeightPercent = KeyboardPreferences.getKeyboardHeightPercent(this);
        mRowGapDp = KeyboardPreferences.getRowGap(this);
        mHistoryEnabled = KeyboardPreferences.isClipboardHistoryEnabled(this);
        mClipboardLimit = KeyboardPreferences.getClipboardLimit(this);
    }

    private void applyPreferencesToViews() {
        if (mKeyboardView != null) {
            mKeyboardView.setPreviewEnabled(mPreviewEnabled);
        }
        if (mQwertyKeyboard != null) {
            mQwertyKeyboard.setLongPressSymbolsEnabled(mLongPressEnabled);
            mSymbolsKeyboard.setLongPressSymbolsEnabled(mLongPressEnabled);
        }
        if (mSuggestionStrip != null) {
            mSuggestionRow.setVisibility(mSuggestionsEnabled ? View.VISIBLE : View.GONE);
            mSuggestionEmpty.setVisibility(mSuggestionsEnabled ? View.GONE : View.VISIBLE);
            if (!mSuggestionsEnabled) {
                mSuggestionEmpty.setText(R.string.suggestions_disabled);
            }
            mQuickShortcutRow.setVisibility(mAltToolbarEnabled ? View.VISIBLE : View.GONE);
            buildQuickShortcutRow();
        }
        if (mQwertyKeyboard != null) {
            applyKeyboardGeometry();
        }
        if (mClipboardEnabledSwitch != null
                && mClipboardEnabledSwitch.isChecked() != mHistoryEnabled) {
            mClipboardEnabledSwitch.setChecked(mHistoryEnabled);
        }
    }

    /** Applies the height percentage and row spacing, then redraws. */
    private void applyKeyboardGeometry() {
        if (mKeyboardView == null || mQwertyKeyboard == null) {
            return;
        }
        int baseKeyHeight = Math.round(
                AmsKeyboard.BASE_KEY_HEIGHT_DP * getResources().getDisplayMetrics().density);
        int targetKeyHeight = Math.round(baseKeyHeight * mKeyboardHeightPercent / 100f);
        int gap = Math.round(mRowGapDp * getResources().getDisplayMetrics().density);
        mQwertyKeyboard.applyRowGap(gap);
        mSymbolsKeyboard.applyRowGap(gap);
        mQwertyKeyboard.applyKeyHeight(targetKeyHeight);
        mSymbolsKeyboard.applyKeyHeight(targetKeyHeight);
        mKeyboardView.requestLayout();
        mKeyboardView.invalidateAllKeys();
    }

    // ---------------------------------------------------------------- panels

    private void showKeyboardPanel() {
        if (mRootView == null) {
            return;
        }
        updateChromeVisibility(true);
        mKeyboardView.setVisibility(View.VISIBLE);
        mToolsPanel.setVisibility(View.GONE);
        mShortcutPanel.setVisibility(View.GONE);
        mEmojiPanel.setVisibility(View.GONE);
        mClipboardPanel.setVisibility(View.GONE);
        setToolbarSelection(mKeyboardButton);
        mRootView.requestLayout();
    }

    private void showToolsPanel() {
        if (mRootView == null) {
            return;
        }
        updateChromeVisibility(true);
        mKeyboardView.setVisibility(View.GONE);
        mToolsPanel.setVisibility(View.VISIBLE);
        mShortcutPanel.setVisibility(View.GONE);
        mEmojiPanel.setVisibility(View.GONE);
        mClipboardPanel.setVisibility(View.GONE);
        setToolbarSelection(mToolsButton);
        mRootView.requestLayout();
    }

    private void showShortcutPanel() {
        if (mRootView == null) {
            return;
        }
        updateChromeVisibility(true);
        mKeyboardView.setVisibility(View.GONE);
        mToolsPanel.setVisibility(View.GONE);
        mShortcutPanel.setVisibility(View.VISIBLE);
        mEmojiPanel.setVisibility(View.GONE);
        mClipboardPanel.setVisibility(View.GONE);
        setToolbarSelection(mShortcutsButton);
        mRootView.requestLayout();
    }

    private void showEmojiPanel() {
        if (mRootView == null) {
            return;
        }
        updateChromeVisibility(true);
        mKeyboardView.setVisibility(View.GONE);
        mToolsPanel.setVisibility(View.GONE);
        mShortcutPanel.setVisibility(View.GONE);
        mEmojiPanel.setVisibility(View.VISIBLE);
        mClipboardPanel.setVisibility(View.GONE);
        setToolbarSelection(mEmojiButton);
        mRootView.requestLayout();
    }

    private void showClipboardPanel() {
        if (mRootView == null) {
            return;
        }
        // The clipboard view has its own header, so the chrome moves out of the way.
        updateChromeVisibility(false);
        mKeyboardView.setVisibility(View.GONE);
        mToolsPanel.setVisibility(View.GONE);
        mShortcutPanel.setVisibility(View.GONE);
        mEmojiPanel.setVisibility(View.GONE);
        mClipboardPanel.setVisibility(View.VISIBLE);
        refreshClipboardHistoryAsync();
        mRootView.requestLayout();
    }

    private void updateChromeVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        mToolbar.setVisibility(visibility);
        boolean stripUseful = mSuggestionsEnabled || mAltToolbarEnabled;
        mSuggestionStrip.setVisibility(visible && stripUseful ? View.VISIBLE : View.GONE);
    }

    private void setToolbarSelection(ImageButton selected) {
        int selectedColor = getResources().getColor(R.color.ime_accent);
        int normalColor = getResources().getColor(R.color.toolbar_icon);
        ImageButton[] buttons = {
                mToolsButton, mEmojiButton, mClipboardButton, mShortcutsButton,
                mCopyButton, mPasteButton, mKeyboardButton, mToolbarSettingsButton
        };
        for (ImageButton button : buttons) {
            if (button != null) {
                button.setColorFilter(button == selected ? selectedColor : normalColor);
            }
        }
    }

    private void bindToolbar() {

        mToolsButton.setOnClickListener(view -> showToolsPanel());
        mEmojiButton.setOnClickListener(view -> showEmojiPanel());
        mClipboardButton.setOnClickListener(view -> showClipboardPanel());
        mShortcutsButton.setOnClickListener(view -> showShortcutPanel());
        mCopyButton.setOnClickListener(view -> runShortcut(SHORTCUT_COPY));
        mPasteButton.setOnClickListener(view -> runShortcut(SHORTCUT_PASTE));
        mKeyboardButton.setOnClickListener(view -> showKeyboardPanel());
        mToolbarSettingsButton.setOnClickListener(view -> openSettings());
        bindHeightDrag(mRootView.findViewById(R.id.resize_handle));
        mSettingsButton.setOnClickListener(view -> openSettings());
        mRootView.findViewById(R.id.clipboard_back)
                .setOnClickListener(view -> showKeyboardPanel());
        mRootView.findViewById(R.id.clipboard_clear)
                .setOnClickListener(view -> clearClipboardHistory());
    }

    /**
     * The pill above the suggestions is the keyboard's resize handle: drag it up or down and the
     * keyboard grows or shrinks while you type. The value is persisted on release and the same
     * preference drives the slider in the settings screen.
     */
    private void bindHeightDrag(View handle) {
        final float[] startY = new float[1];
        final int[] startPercent = new int[1];
        final boolean[] dragging = new boolean[1];

        handle.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY[0] = event.getRawY();
                    startPercent[0] = mKeyboardHeightPercent;
                    dragging[0] = true;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        return false;
                    }
                    float delta = startY[0] - event.getRawY();
                    // The four key rows are the resizable part, so 1% is 1% of their height.
                    int rowsHeight = Math.round(AmsKeyboard.BASE_KEY_HEIGHT_DP
                            * getResources().getDisplayMetrics().density) * 4;
                    int percent = startPercent[0]
                            + Math.round(delta * 100f / Math.max(1, rowsHeight));
                    percent = KeyboardPreferences.clamp(percent,
                            KeyboardPreferences.MIN_KEYBOARD_HEIGHT,
                            KeyboardPreferences.MAX_KEYBOARD_HEIGHT);
                    if (percent != mKeyboardHeightPercent) {
                        mKeyboardHeightPercent = percent;
                        applyKeyboardGeometry();
                        showHeightToast(percent);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        KeyboardPreferences.get(this).edit()
                                .putInt(KeyboardPreferences.KEY_KEYBOARD_HEIGHT,
                                        mKeyboardHeightPercent)
                                .apply();
                        dragging[0] = false;
                    }
                    return true;
                default:
                    return false;
            }
        });
        handle.setOnLongClickListener(view -> {
            // Long-press snaps back to the default height.
            mKeyboardHeightPercent = KeyboardPreferences.DEFAULT_KEYBOARD_HEIGHT;
            KeyboardPreferences.get(this).edit()
                    .putInt(KeyboardPreferences.KEY_KEYBOARD_HEIGHT,
                            KeyboardPreferences.DEFAULT_KEYBOARD_HEIGHT)
                    .apply();
            applyKeyboardGeometry();
            showHeightToast(KeyboardPreferences.DEFAULT_KEYBOARD_HEIGHT);
            return true;
        });
    }

    private android.widget.Toast mHeightToast;

    private void showHeightToast(int percent) {
        if (mHeightToast != null) {
            mHeightToast.cancel();
        }
        mHeightToast = android.widget.Toast.makeText(this,
                getString(R.string.setting_keyboard_height_value, percent),
                android.widget.Toast.LENGTH_SHORT);
        mHeightToast.show();
    }

    private void bindClipboardControls() {
        mClipboardEnabledSwitch.setChecked(mHistoryEnabled);
        mClipboardEnabledSwitch.setContentDescription(mHistoryEnabled
                ? getString(R.string.clipboard_history_on)
                : getString(R.string.clipboard_history_off));
        mClipboardEnabledSwitch.setOnCheckedChangeListener(
                (button, isChecked) -> setClipboardHistoryEnabled(isChecked));
    }

    private void openSettings() {
        try {
            android.content.Intent intent =
                    new android.content.Intent(this, MainActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // Some restricted profiles block launching activities from an IME.
        }
    }

    private void buildQuickShortcutRow() {
        mQuickShortcutRow.removeAllViews();
        if (!mAltToolbarEnabled) {
            return;
        }
        addStripButton("Ctrl+A", () -> runShortcut(SHORTCUT_SELECT_ALL));
        addStripButton("Ctrl+C", () -> runShortcut(SHORTCUT_COPY));
        addStripButton("Ctrl+X", () -> runShortcut(SHORTCUT_CUT));
        addStripButton("Ctrl+V", () -> runShortcut(SHORTCUT_PASTE));
        addStripButton("↶", () -> runShortcut(SHORTCUT_UNDO));
        addStripButton("↷", () -> runShortcut(SHORTCUT_REDO));
    }

    private void addStripButton(String label, Runnable action) {
        TextView button = ShortcutPadView.createStripButton(this, label, action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(2), 0, dp(2), 0);
        mQuickShortcutRow.addView(button, params);
    }

    // ---------------------------------------------------------------- tools & emoji

    private void populateToolsGrid() {
        if (mToolsGrid == null || mToolsGrid.getChildCount() > 0) {
            return;
        }
        addToolTile("▣", getString(R.string.tool_clipboard), this::showClipboardPanel);
        addToolTile("⌘", getString(R.string.tool_alt_shortcuts), this::showShortcutPanel);
        addToolTile("☷", getString(R.string.tool_select_all),
                () -> runShortcut(SHORTCUT_SELECT_ALL));
        addToolTile("▢", getString(R.string.tool_copy), () -> runShortcut(SHORTCUT_COPY));
        addToolTile("✂", getString(R.string.tool_cut), () -> runShortcut(SHORTCUT_CUT));
        addToolTile("↧", getString(R.string.tool_paste), () -> runShortcut(SHORTCUT_PASTE));
        addToolTile("⧉", getString(R.string.tool_copy_all),
                () -> runShortcut(SHORTCUT_COPY_ALL));
        addToolTile("↶", getString(R.string.tool_undo), () -> runShortcut(SHORTCUT_UNDO));
        addToolTile("↷", getString(R.string.tool_redo), () -> runShortcut(SHORTCUT_REDO));
        addToolTile("⇤", getString(R.string.tool_move_start),
                () -> runShortcut(SHORTCUT_MOVE_START));
        addToolTile("⇥", getString(R.string.tool_move_end),
                () -> runShortcut(SHORTCUT_MOVE_END));
        addToolTile("⚙", getString(R.string.tool_settings), this::openSettings);
    }

    private void addToolTile(String icon, String label, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(label);
        tile.setBackgroundResource(R.drawable.bg_shortcut_key);
        tile.setOnClickListener(view -> action.run());

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextColor(getResources().getColor(R.color.toolbar_icon));
        iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        iconView.setGravity(Gravity.CENTER);
        tile.addView(iconView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(getResources().getColor(R.color.toolbar_icon));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(1);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int index = mToolsGrid.getChildCount();
        int columns = 4;
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(index / columns, 1, 1f),
                GridLayout.spec(index % columns, 1, 1f));
        params.width = 0;
        params.height = 0;
        params.setGravity(Gravity.FILL);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
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
            cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            cell.setGravity(Gravity.CENTER);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setContentDescription(emoji);
            cell.setOnClickListener(view -> {
                InputConnection connection = getCurrentInputConnection();
                if (connection != null) {
                    connection.commitText(emoji, 1);
                    resetComposingWord();
                }
                showKeyboardPanel();
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / 8, 1, 1f), GridLayout.spec(index % 8, 1, 1f));
            params.width = 0;
            params.height = dp(38);
            params.setGravity(Gravity.FILL);
            mEmojiGrid.addView(cell, params);
        }
    }

    // ---------------------------------------------------------------- shortcuts

    /** Runs one of the PC-style shortcuts against the current editor. */
    void runShortcut(int shortcut) {
        runShortcut(shortcut, (char) 0);
    }

    void runShortcut(int shortcut, char altLetter) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            showKeyboardPanel();
            return;
        }
        switch (shortcut) {
            case SHORTCUT_SELECT_ALL:
                sendControlKey(connection, mCtrlLatched, KeyEvent.KEYCODE_A);
                mCtrlLatched = false;
                break;
            case SHORTCUT_COPY:
                connection.performContextMenuAction(android.R.id.copy);
                break;
            case SHORTCUT_CUT:
                connection.performContextMenuAction(android.R.id.cut);
                break;
            case SHORTCUT_PASTE:
                pasteClipboard(connection);
                break;
            case SHORTCUT_UNDO:
                sendControlKey(connection, true, KeyEvent.KEYCODE_Z);
                break;
            case SHORTCUT_REDO:
                sendControlKey(connection, true, KeyEvent.KEYCODE_Y);
                break;
            case SHORTCUT_COPY_ALL:
                copyAllText(connection);
                break;
            case SHORTCUT_MOVE_START:
                sendControlKey(connection, true, KeyEvent.KEYCODE_MOVE_HOME);
                break;
            case SHORTCUT_MOVE_END:
                sendControlKey(connection, true, KeyEvent.KEYCODE_MOVE_END);
                break;
            case SHORTCUT_SELECT_WORD:
                selectWordAtCursor(connection);
                break;
            case SHORTCUT_ALT_LETTER:
                sendAltKey(connection, altLetter);
                break;
            default:
                break;
        }
        updateModifierAppearance();
        showKeyboardPanel();
    }

    private void sendControlKey(InputConnection connection, boolean withControl, int keyCode) {
        int meta = withControl ? KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON : 0;
        connection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        connection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, meta));
    }

    private void sendAltKey(InputConnection connection, char letter) {
        int keyCode = KeyEvent.keyCodeFromString("KEYCODE_" + Character.toUpperCase(letter));
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return;
        }
        int meta = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        connection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        connection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, meta));
    }

    /**
     * Copies the entire field.
     *
     * <p>Reading through {@link #getAllEditorText()} does not need a selection, so this works even
     * when the app never highlighted anything. The full string is put on the clipboard, which is
     * the "unlimited copy" behaviour a real PC has with Ctrl+A then Ctrl+C.
     */
    private void copyAllText(InputConnection connection) {
        String all = getAllEditorText(connection);
        if (TextUtils.isEmpty(all)) {
            toast(getString(R.string.tool_copy_all_empty));
            return;
        }
        if (mClipboardManager == null) {
            toast(getString(R.string.copy_failed));
            return;
        }
        try {
            mClipboardManager.setPrimaryClip(ClipData.newPlainText("AMS Keyboard", all));
            // The clipboard listener stores it in history as well, so it can be pasted later.
            toast(getString(R.string.tool_copy_all_done, all.length()));
        } catch (RuntimeException ignored) {
            toast(getString(R.string.copy_failed));
        }
    }

    private String getAllEditorText(InputConnection connection) {
        ExtractedText extracted = connection.getExtractedText(
                new ExtractedTextRequest(), 0);
        if (extracted != null && extracted.text != null && extracted.text.length() > 0) {
            return extracted.text.toString();
        }
        android.view.inputmethod.SurroundingText surrounding = connection.getSurroundingText(
                Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, 0);
        return surrounding == null ? null : surrounding.getText().toString();
    }

    private void selectWordAtCursor(InputConnection connection) {
        CharSequence before = connection.getTextBeforeCursor(48, 0);
        CharSequence after = connection.getTextAfterCursor(48, 0);
        String left = before == null ? "" : before.toString();
        String right = after == null ? "" : after.toString();
        int start = left.length();
        while (start > 0 && !isWordSeparator(left.charAt(start - 1))) {
            start--;
        }
        int end = 0;
        while (end < right.length() && !isWordSeparator(right.charAt(end))) {
            end++;
        }
        int selectionStart = start - left.length();
        int selectionEnd = end;
        connection.setSelection(selectionStart, selectionEnd);
    }

    private static boolean isWordSeparator(char value) {
        return Character.isWhitespace(value) || ".,;:!?()[]{}<>\"'`".indexOf(value) >= 0;
    }

    // ---------------------------------------------------------------- clipboard

    private void pasteClipboard(InputConnection connection) {
        String text = getPrimaryClipboardText();
        if (TextUtils.isEmpty(text)) {
            // Nothing readable (or a restricted clipboard): let the host app try instead.
            connection.performContextMenuAction(android.R.id.paste);
            return;
        }
        commitTextInChunks(connection, text);
    }

    private void setClipboardHistoryEnabled(boolean enabled) {
        mHistoryEnabled = enabled;
        KeyboardPreferences.get(this).edit()
                .putBoolean(KeyboardPreferences.KEY_CLIPBOARD_HISTORY, enabled).apply();
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
        String text = readPrimaryClipboardText(true);
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
                android.os.PersistableBundle extras = clip.getDescription().getExtras();
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
        mClipboardCount.setText(getString(R.string.clipboard_count, count, mClipboardLimit));
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
            background.setCornerRadius(dp(8));
            card.setBackground(background);
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription(getString(R.string.clipboard_item_description));
            card.setOnClickListener(view -> pasteHistoryClip(clip.id));
            card.setOnTouchListener(createSwipeToDeleteListener(clip.id));
            card.setOnLongClickListener(view -> {
                togglePin(clip.id, !clip.pinned);
                return true;
            });

            TextView preview = new TextView(this);
            preview.setText(clip.pinned ? "📌 " + clip.text : clip.text);
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

    private void togglePin(long clipId, boolean pinned) {
        executeHistoryTask(() -> {
            mClipboardHistory.setPinned(clipId, pinned);
            refreshClipboardHistoryAsync();
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

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        readPreferences();
        applyPreferencesToViews();
        mCurrentKeyboard = mQwertyKeyboard;
        mCaps = false;
        mShiftLatched = false;
        mCtrlLatched = false;
        mAltLatched = false;
        mKeyboardView.setKeyboard(mCurrentKeyboard);
        mKeyboardView.setShifted(false);
        updateLetterKeyLabels();
        applyKeyboardGeometry();
        resetComposingWord();
        clearSuggestions();
        showKeyboardPanel();

        mInputViewVisible = true;
        if (mHistoryEnabled) {
            registerClipboardListener();
            // Android only lets an active IME inspect the current clipboard. Save its current
            // plain text too, but don't create another item if it is already the newest one.
            capturePrimaryClipboard(true);
        }
        if (mAutoCapitalizeEnabled && isCaretAtFieldStart()) {
            applyAutoShift(true);
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        mInputViewVisible = false;
        if (mHeightToast != null) {
            mHeightToast.cancel();
            mHeightToast = null;
        }
        unregisterClipboardListener();
        commitPendingCorrection();
        resetComposingWord();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onDestroy() {
        mDestroying = true;
        mInputViewVisible = false;
        unregisterClipboardListener();
        try {
            KeyboardPreferences.get(this)
                    .unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
        } catch (RuntimeException ignored) {
            // Already detached.
        }
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

    private void onPreferenceChanged() {
        if (mDestroying) {
            return;
        }
        readPreferences();
        applyPreferencesToViews();
    }

    // ---------------------------------------------------------------- key handling

    @Override
    public void onPress(int primaryCode) {
        if (mVibrateEnabled && mVibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mVibrator.vibrate(VibrationEffect.createOneShot(14,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                mVibrator.vibrate(14);
            }
        }
        if (mSoundEnabled) {
            mKeyboardView.playSoundEffect(android.view.SoundEffectConstants.CLICK);
        }
    }

    @Override
    public void onRelease(int primaryCode) {
        // Feedback is handled in onPress.
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        switch (primaryCode) {
            case Keyboard.KEYCODE_SHIFT:
                handleShiftKey();
                return;
            case Keyboard.KEYCODE_MODE_CHANGE:
                showSymbolsKeyboard();
                return;
            case CUSTOM_SYMBOLS_CODE: // "ABC" on the symbols layout
                showLettersKeyboard();
                return;
            case CUSTOM_EMOJI_CODE:
                showEmojiPanel();
                return;
            case KEYCODE_CTRL:
                toggleModifier(true);
                return;
            case KEYCODE_ALT:
                toggleModifier(false);
                return;
            case 0:
                return;
            default:
                break;
        }

        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }

        // Ctrl / Alt latch: send a real modifier event and clear the latch afterwards.
        if (mCtrlLatched || mAltLatched) {
            sendLatchedModifierKey(connection, primaryCode);
            return;
        }

        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace(connection);
        } else if (primaryCode == 10) {
            commitPendingCorrection();
            handleEnter(connection);
            resetComposingWord();
            mShiftLatched = false;
            mShiftWasAuto = false;
            updateModifierAppearance();
            if (mAutoCapitalizeEnabled) {
                applyAutoShift(true);
            }
        } else {
            handleCharacter(connection, primaryCode);
        }
    }

    @Override
    public void onText(CharSequence text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null && text != null) {
            commitTextInChunks(connection, text);
            resetComposingWord();
        }
    }

    // ---------------------------------------------------------------- modifiers

    private void handleShiftKey() {
        long now = SystemClock.uptimeMillis();
        boolean doubleTap = now - mLastShiftTapAt < 320 && mShiftLatched;
        mLastShiftTapAt = now;
        if (doubleTap) {
            // Double tap locks caps, like a PC keyboard.
            mCaps = true;
            mShiftLatched = true;
        } else if (mShiftLatched) {
            // An automatic (sentence-start) shift is cancelled by the first tap without locking.
            mShiftLatched = false;
            mCaps = false;
        } else {
            mShiftLatched = true;
        }
        mShiftWasAuto = false;
        updateModifierAppearance();
    }

    private void toggleModifier(boolean control) {
        if (control) {
            mCtrlLatched = !mCtrlLatched;
            if (mCtrlLatched) {
                mAltLatched = false;
            }
        } else {
            mAltLatched = !mAltLatched;
            if (mAltLatched) {
                mCtrlLatched = false;
            }
        }
        updateModifierAppearance();
    }

    private void clearLatchedModifiers() {
        mCtrlLatched = false;
        mAltLatched = false;
    }

    /** Sends the key the user tapped with the latched Ctrl/Alt meta state applied. */
    private void sendLatchedModifierKey(InputConnection connection, int primaryCode) {
        int keyCode;
        if (primaryCode >= 'a' && primaryCode <= 'z') {
            keyCode = KeyEvent.KEYCODE_A + (primaryCode - 'a');
        } else if (primaryCode >= '0' && primaryCode <= '9') {
            keyCode = KeyEvent.KEYCODE_0 + (primaryCode - '0');
        } else if (primaryCode >= 'A' && primaryCode <= 'Z') {
            keyCode = KeyEvent.KEYCODE_A + (primaryCode - 'A');
        } else {
            // Not a key we can map: drop the latch and treat this as ordinary typing.
            clearLatchedModifiers();
            updateModifierAppearance();
            handleCharacter(connection, primaryCode);
            return;
        }

        int meta = 0;
        if (mCtrlLatched) {
            meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        }
        if (mAltLatched) {
            meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        }
        connection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        connection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, meta));
        clearLatchedModifiers();
        updateModifierAppearance();
        resetComposingWord();
    }

    /** Paints the latched modifiers using the checked state of the key background selector. */
    private void updateModifierAppearance() {
        if (mQwertyKeyboard == null || mSymbolsKeyboard == null || mKeyboardView == null) {
            return;
        }
        boolean shiftOn = mShiftLatched || mCaps;
        for (AmsKeyboard keyboard : new AmsKeyboard[]{mQwertyKeyboard, mSymbolsKeyboard}) {
            for (Keyboard.Key key : keyboard.getKeys()) {
                if (key.codes == null || key.codes.length == 0) {
                    continue;
                }
                switch (key.codes[0]) {
                    case Keyboard.KEYCODE_SHIFT:
                        key.on = shiftOn;
                        key.sticky = true;
                        key.icon = getDrawable(mCaps
                                ? R.drawable.ic_caps_lock
                                : R.drawable.ic_shift);
                        break;
                    case KEYCODE_CTRL:
                        key.on = mCtrlLatched;
                        break;
                    case KEYCODE_ALT:
                        key.on = mAltLatched;
                        break;
                    default:
                        break;
                }
            }
        }
        mKeyboardView.setShifted(shiftOn);
        mKeyboardView.invalidateAllKeys();
    }

    // ---------------------------------------------------------------- typing

    private void handleCharacter(InputConnection connection, int primaryCode) {
        char typed = (char) primaryCode;
        if (Character.isLetter(typed)) {
            handleLetter(connection, typed);
            return;
        }
        commitPendingCorrection();
        connection.commitText(String.valueOf(typed), 1);
        resetComposingWord();
        mDoubleSpaceArmed = false;
        if (typed == ' ') {
            handleSpace(connection);
        } else if (".!?".indexOf(typed) >= 0 && mAutoCapitalizeEnabled) {
            // Typing punctuation at the end of a sentence arms the next capital letter.
            applyAutoShift(true);
        }
        updateSuggestions();
    }

    private void handleLetter(InputConnection connection, char typed) {
        boolean uppercase = mCaps || mShiftLatched;
        char output = uppercase ? Character.toUpperCase(typed) : Character.toLowerCase(typed);

        // Typing a letter replaces an active suggestion, like every other keyboard. The
        // replacement is a composing region, so the host editor merges it into one undo step.
        if (mComposingSuggestion) {
            connection.setComposingText(String.valueOf(output), 1);
            connection.finishComposingText();
            mComposingSuggestion = false;
            mCurrentWord.setLength(0);
        } else {
            connection.commitText(String.valueOf(output), 1);
        }
        mCurrentWord.append(Character.toLowerCase(output));
        mDoubleSpaceArmed = false;

        if (mShiftLatched && !mCaps) {
            // A one-shot shift is consumed by the letter it produced.
            mShiftLatched = false;
            mShiftWasAuto = false;
            updateModifierAppearance();
        }
        updateSuggestions();
    }

    private void handleSpace(InputConnection connection) {
        long now = SystemClock.uptimeMillis();
        boolean doubleSpace = mDoubleSpaceEnabled && mDoubleSpaceArmed
                && now - mLastSpaceAt < 700;
        mLastSpaceAt = now;
        mDoubleSpaceArmed = !doubleSpace;
        if (doubleSpace) {
            // The first space is already in the editor; turn " " into ". ".
            CharSequence before = connection.getTextBeforeCursor(1, 0);
            if (before != null && before.length() == 1 && before.charAt(0) == ' ') {
                connection.deleteSurroundingText(1, 0);
                connection.commitText(". ", 1);
            }
        }
        if (mAutoCapitalizeEnabled) {
            applyAutoShift(true);
        }
    }

    private void handleBackspace(InputConnection connection) {
        CharSequence selection = connection.getSelectedText(0);
        if (selection != null && selection.length() > 0) {
            // Deleting a selection works the same way on every Android version.
            connection.commitText("", 1);
            resetComposingWord();
            mDoubleSpaceArmed = false;
            updateSuggestions();
            return;
        }
        CharSequence before = connection.getTextBeforeCursor(1, 0);
        connection.deleteSurroundingText(1, 0);
        if (mCurrentWord.length() > 0) {
            mCurrentWord.setLength(mCurrentWord.length() - 1);
        }
        if (before != null && before.length() > 0 && ".!?".indexOf(before.charAt(0)) >= 0
                && mAutoCapitalizeEnabled) {
            applyAutoShift(true);
        }
        mDoubleSpaceArmed = false;
        updateSuggestions();
    }

    /**
     * True when nothing precedes the caret. Editors that refuse to report surrounding text return
     * null or an empty string; in that case starting a sentence with a capital is the friendly
     * default.
     */
    private boolean isCaretAtFieldStart() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || isPasswordField()) {
            return false;
        }
        CharSequence before = connection.getTextBeforeCursor(1, 0);
        return before == null || before.length() == 0;
    }

    private void clearSuggestions() {
        mSuggestionRow.removeAllViews();
        if (mSuggestionsEnabled) {
            mSuggestionEmpty.setVisibility(View.VISIBLE);
            mSuggestionEmpty.setText(R.string.suggestion_idle);
        }
    }

    // ---------------------------------------------------------------- auto-correct

    /** Replaces the word in progress when space, enter or punctuation is typed. */
    private void commitPendingCorrection() {
        if (!mAutoCorrectEnabled || mCurrentWord.length() == 0) {
            mCurrentWord.setLength(0);
            return;
        }
        String typed = mCurrentWord.toString();
        mCurrentWord.setLength(0);
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || typed.length() < 2 || mDictionary == null) {
            return;
        }
        String correction = mDictionary.bestCorrection(typed, null);
        if (correction == null || correction.equals(typed)) {
            return;
        }
        CharSequence before = connection.getTextBeforeCursor(typed.length(), 0);
        if (before == null || !typed.equalsIgnoreCase(before.toString())) {
            return;
        }
        if (Character.isUpperCase(typed.charAt(0))) {
            correction = Character.toUpperCase(correction.charAt(0)) + correction.substring(1);
        }
        connection.deleteSurroundingText(typed.length(), 0);
        connection.commitText(correction, 1);
    }

    private void updateSuggestions() {
        if (mSuggestionRow == null || !mSuggestionsEnabled) {
            return;
        }
        mSuggestionRow.removeAllViews();
        if (mDictionary == null) {
            return;
        }
        String word = mCurrentWord.toString();
        if (word.length() == 0) {
            mSuggestionEmpty.setVisibility(View.VISIBLE);
            mSuggestionEmpty.setText(R.string.suggestion_idle);
            return;
        }
        mSuggestionEmpty.setVisibility(View.GONE);

        int added = 0;
        if (mAutoCorrectEnabled && !mDictionary.isKnown(word)) {
            String correction = mDictionary.bestCorrection(word, null);
            if (correction != null && !correction.equals(word)) {
                addSuggestion(displayCase(word, correction), true);
                added++;
            }
        }
        for (String prediction : mDictionary.predictionsFor(word, SUGGESTION_COUNT)) {
            if (added >= SUGGESTION_COUNT) {
                break;
            }
            if (prediction.equalsIgnoreCase(word)) {
                continue;
            }
            addSuggestion(displayCase(word, prediction), false);
            added++;
        }
    }

    private String displayCase(String typed, String replacement) {
        if (mCaps || (typed.length() > 0 && Character.isUpperCase(typed.charAt(0)))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement;
    }

    private void addSuggestion(String text, boolean correction) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        chip.setTextColor(getResources().getColor(
                correction ? R.color.ime_accent : R.color.key_text));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setBackgroundResource(R.drawable.bg_suggestion);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription(text);
        chip.setOnClickListener(view -> applySuggestion(text));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        mSuggestionRow.addView(chip, params);
    }

    private void applySuggestion(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        String typed = mCurrentWord.toString();
        if (typed.length() > 0) {
            CharSequence before = connection.getTextBeforeCursor(typed.length(), 0);
            if (before != null && typed.equalsIgnoreCase(before.toString())) {
                connection.deleteSurroundingText(typed.length(), 0);
            }
        }
        connection.commitText(text + " ", 1);
        mCurrentWord.setLength(0);
        mComposingSuggestion = true;
        updateSuggestions();
        if (mAutoCapitalizeEnabled) {
            applyAutoShift(true);
        }
    }

    /** Turns Shift on so the next letter starts a sentence, unless the user beat us to it. */
    private void applyAutoShift(boolean enable) {
        if (!mAutoCapitalizeEnabled) {
            return;
        }
        if (enable) {
            if (!mShiftLatched && !mCaps) {
                mShiftLatched = true;
                mShiftWasAuto = true;
            }
        } else if (mShiftWasAuto) {
            mShiftLatched = false;
            mShiftWasAuto = false;
        }
        updateModifierAppearance();
    }

    private void resetComposingWord() {
        mCurrentWord.setLength(0);
        mComposingSuggestion = false;
    }

    /** Password / PIN fields never get automatic capitals or suggestions. */
    private boolean isPasswordField() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) {
            return false;
        }
        int variation = info.inputType & android.text.InputType.TYPE_MASK_VARIATION;
        int inputClass = info.inputType & android.text.InputType.TYPE_MASK_CLASS;
        return inputClass == android.text.InputType.TYPE_CLASS_TEXT
                && (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || inputClass == android.text.InputType.TYPE_CLASS_NUMBER
                && variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;
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

    private void showSymbolsKeyboard() {
        commitPendingCorrection();
        mCurrentKeyboard = mSymbolsKeyboard;
        mKeyboardView.setKeyboard(mCurrentKeyboard);
        applyKeyboardGeometry();
        updateModifierAppearance();
    }

    private void showLettersKeyboard() {
        mCurrentKeyboard = mQwertyKeyboard;
        mCaps = false;
        mShiftLatched = false;
        updateLetterKeyLabels();
        mKeyboardView.setKeyboard(mCurrentKeyboard);
        applyKeyboardGeometry();
        updateModifierAppearance();
    }

    private void updateLetterKeyLabels() {
        if (mQwertyKeyboard == null) {
            return;
        }
        for (Keyboard.Key key : mQwertyKeyboard.getKeys()) {
            if (key.label == null || key.label.length() != 1) {
                continue;
            }
            char character = key.label.charAt(0);
            if (!Character.isLetter(character)) {
                continue;
            }
            key.label = String.valueOf(mCaps
                    ? Character.toUpperCase(character)
                    : Character.toLowerCase(character));
        }
        if (mKeyboardView != null) {
            mKeyboardView.invalidateAllKeys();
        }
    }

    // ---------------------------------------------------------------- helpers

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyKeyboardGeometry();
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
