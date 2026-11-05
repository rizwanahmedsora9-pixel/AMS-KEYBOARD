package com.ams.keyboard;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid of PC-style shortcut buttons: Ctrl+A/C/X/V, Undo/Redo, Copy all, cursor jumps and the
 * dedicated Alt + letter pad. Every tile forwards its action to
 * {@link AmsKeyboardService#runShortcut(Shortcut)}.
 */
final class ShortcutPadView extends BaseAdapter {

    /** One tile: a short key cap, a caption under it and the action it runs. */
    static final class Shortcut {
        final String cap;
        final String caption;
        final Runnable action;

        Shortcut(String cap, String caption, Runnable action) {
            this.cap = cap;
            this.caption = caption;
            this.action = action;
        }
    }

    private final Context mContext;
    private final AmsKeyboardService mService;
    private final List<Shortcut> mShortcuts = new ArrayList<>();

    ShortcutPadView(Context context, AmsKeyboardService service) {
        mContext = context;
        mService = service;
        rebuild();
    }

    void rebuild() {
        mShortcuts.clear();
        AmsKeyboardService service = mService;
        mShortcuts.add(new Shortcut("Ctrl+A", mContext.getString(R.string.shortcut_select_all_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_SELECT_ALL)));
        mShortcuts.add(new Shortcut("Ctrl+C", mContext.getString(R.string.shortcut_copy_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_COPY)));
        mShortcuts.add(new Shortcut("Ctrl+X", mContext.getString(R.string.shortcut_cut_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_CUT)));
        mShortcuts.add(new Shortcut("Ctrl+V", mContext.getString(R.string.shortcut_paste_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_PASTE)));
        mShortcuts.add(new Shortcut("Ctrl+Z", mContext.getString(R.string.shortcut_undo_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_UNDO)));
        mShortcuts.add(new Shortcut("Ctrl+Y", mContext.getString(R.string.shortcut_redo_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_REDO)));
        mShortcuts.add(new Shortcut(mContext.getString(R.string.shortcut_copy_all),
                mContext.getString(R.string.shortcut_copy_all_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_COPY_ALL)));
        mShortcuts.add(new Shortcut("⤒", mContext.getString(R.string.shortcut_move_start_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_MOVE_START)));
        mShortcuts.add(new Shortcut("⤓", mContext.getString(R.string.shortcut_move_end_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_MOVE_END)));
        mShortcuts.add(new Shortcut("◧",
                mContext.getString(R.string.shortcut_select_word_desc),
                () -> service.runShortcut(AmsKeyboardService.SHORTCUT_SELECT_WORD)));
        for (char letter = 'a'; letter <= 'z'; letter++) {
            final char target = letter;
            mShortcuts.add(new Shortcut("Alt+" + Character.toUpperCase(letter),
                    mContext.getString(R.string.shortcut_alt_label) + " " + letter,
                    () -> service.runShortcut(AmsKeyboardService.SHORTCUT_ALT_LETTER, target)));
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mShortcuts.size();
    }

    @Override
    public Shortcut getItem(int position) {
        return mShortcuts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Shortcut shortcut = getItem(position);
        LinearLayout tile = new LinearLayout(mContext);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackgroundResource(R.drawable.bg_shortcut_key);
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        tile.setContentDescription(shortcut.cap + ", " + shortcut.caption);

        TextView cap = new TextView(mContext);
        cap.setText(shortcut.cap);
        cap.setTextColor(mContext.getResources().getColor(R.color.key_text));
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cap.setGravity(Gravity.CENTER);
        cap.setSingleLine(true);
        tile.addView(cap);

        TextView caption = new TextView(mContext);
        caption.setText(shortcut.caption);
        caption.setTextColor(mContext.getResources().getColor(R.color.key_hint));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setPadding(0, dp(2), 0, 0);
        tile.addView(caption);

        tile.setOnClickListener(view -> shortcut.action.run());
        return tile;
    }

    private int dp(int value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }

    /** Convenience for the strip above the keyboard: a compact, rounded text button. */
    static TextView createStripButton(Context context, String label, Runnable action) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextColor(context.getResources().getColor(R.color.toolbar_icon));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(context.getResources().getColor(R.color.ime_background));
        background.setCornerRadius(dp(context, 8));
        button.setBackground(background);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
