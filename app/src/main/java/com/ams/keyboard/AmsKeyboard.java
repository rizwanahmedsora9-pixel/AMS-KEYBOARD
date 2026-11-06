package com.ams.keyboard;

import android.content.Context;
import android.inputmethodservice.Keyboard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Keyboard} subclass that understands the AMS layouts.
 *
 * <p>{@code android.inputmethodservice.Keyboard} only reflows keys horizontally, so vertical
 * resizing (the "Keyboard height" setting) has to be done here:
 *
 * <ul>
 *   <li>Every key's {@code y} and {@code height} are recomputed, because {@code KeyboardView}
 *       draws from those fields and never recalculates them vertically.</li>
 *   <li>{@link #getHeight()} is overridden so {@code KeyboardView.onMeasure} gives the view the
 *       right height after a resize.</li>
 *   <li>The touch grid cached inside {@code Keyboard} is cleared, so taps keep landing on the key
 *       that is drawn under the finger.</li>
 * </ul>
 */
final class AmsKeyboard extends Keyboard {

    /** Key height declared by the XML layouts; 100% of the height setting means this value. */
    static final int BASE_KEY_HEIGHT_DP = 45;

    private int mAppliedKeyHeight;
    /** Gap between rows in pixels; set from the "Row spacing" setting. */
    private int mRowGapPx = -1;
    /** Original long-press characters, so the "Long-press symbols" switch is reversible. */
    private final List<CharSequence> mOriginalPopupCharacters = new ArrayList<>();

    AmsKeyboard(Context context, int layoutResId) {
        super(context, layoutResId);
        for (Key key : getKeys()) {
            mOriginalPopupCharacters.add(key.popupCharacters);
        }
    }

    /** Turns the number/symbol long-press popups on or off without rebuilding the keyboard. */
    void setLongPressSymbolsEnabled(boolean enabled) {
        List<Key> keys = getKeys();
        for (int index = 0; index < keys.size(); index++) {
            Key key = keys.get(index);
            key.popupCharacters = enabled ? mOriginalPopupCharacters.get(index) : null;
            if (!enabled) {
                key.popupResId = 0;
            }
        }
    }

    /**
     * Applies a key height in pixels to every row and key.
     *
     * @return true when something changed and the view needs to be redrawn.
     */
    boolean applyKeyHeight(int keyHeightPx) {
        if (keyHeightPx <= 0 || keyHeightPx == mAppliedKeyHeight) {
            return false;
        }
        mAppliedKeyHeight = keyHeightPx;
        int y = 0;
        for (List<Key> row : rowsInOrder()) {
            for (Key key : row) {
                key.height = keyHeightPx;
                key.y = y;
            }
            y += keyHeightPx + mRowGapPx;
        }
        invalidateTouchGrid(this);
        return true;
    }

    /** Applies a new gap between rows, in pixels, without touching the key heights. */
    boolean applyRowGap(int gapPx) {
        if (gapPx == mRowGapPx) {
            return false;
        }
        mRowGapPx = gapPx;
        if (mAppliedKeyHeight > 0) {
            int keyHeight = mAppliedKeyHeight;
            // Force the vertical layout pass to run again with the new gaps.
            mAppliedKeyHeight = 0;
            applyKeyHeight(keyHeight);
        }
        return true;
    }

    @Override
    public int getHeight() {
        if (mAppliedKeyHeight <= 0) {
            return super.getHeight();
        }
        int rows = rowsInOrder().size();
        if (rows == 0) {
            return super.getHeight();
        }
        return rows * mAppliedKeyHeight + (rows - 1) * mRowGapPx;
    }

    /**
     * Groups the keys into visual rows. {@code Keyboard.mKeys} is ordered row by row and every key
     * of a row shares the XML {@code y}, so that value is used as the row marker.
     */
    private List<List<Key>> rowsInOrder() {
        List<List<Key>> rows = new ArrayList<>();
        int currentY = Integer.MIN_VALUE;
        List<Key> current = null;
        for (Key key : getKeys()) {
            if (current == null || key.y != currentY) {
                current = new ArrayList<>();
                rows.add(current);
                currentY = key.y;
            }
            current.add(key);
        }
        return rows;
    }

    /**
     * {@code Keyboard} caches a proximity grid (built from the key rectangles) the first time it is
     * touched and never rebuilds it. Clearing the cache fields makes the next touch rebuild them
     * from the freshly resized keys, which is what keeps taps accurate after a size change.
     */
    static void invalidateTouchGrid(Keyboard keyboard) {
        if (keyboard == null) {
            return;
        }
        try {
            Field neighbors = Keyboard.class.getDeclaredField("mGridNeighbors");
            neighbors.setAccessible(true);
            neighbors.set(keyboard, null);

            Field cellHeight = Keyboard.class.getDeclaredField("mCellHeight");
            cellHeight.setAccessible(true);
            cellHeight.setInt(keyboard, 0);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Heavily modified Android builds: a stale grid only costs the first tap after a
            // resize, and the grid is rebuilt when the keyboard is re-attached anyway.
        }
    }
}
