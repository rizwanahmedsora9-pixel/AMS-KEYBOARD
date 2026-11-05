package com.ams.keyboard;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * AMS Keyboard — Input Method Service.
 *
 * Visual style (per reference):
 *   - Keyboard background: white (#FFFFFF)
 *   - Key text / labels: black (#000000)
 *   - Layout: matches the reference keyboard layout
 *
 * Features included from the reference (re-implemented, icons/ads ignored):
 *   QWERTY keys, shift/caps, backspace, enter, space, symbols toggle.
 *
 * No ads and no exact icons copied from the reference images.
 */
public class AmsKeyboardService extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView mKeyboardView;
    private Keyboard mQwertyKeyboard;
    private Keyboard mSymbolsKeyboard;
    private boolean mCaps = false;

    @Override
    public View onCreateInputView() {
        mKeyboardView = (KeyboardView)
                getLayoutInflater().inflate(R.layout.keyboard_view, null);

        mQwertyKeyboard = new Keyboard(this, R.xml.keyboard);
        mSymbolsKeyboard = new Keyboard(this, R.xml.keyboard_symbols);

        mKeyboardView.setKeyboard(mQwertyKeyboard);
        mKeyboardView.setOnKeyboardActionListener(this);
        mKeyboardView.setEnabled(true);
        return mKeyboardView;
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Always open on the QWERTY (letter) layout by default.
        mKeyboardView.setKeyboard(mQwertyKeyboard);
        mKeyboardView.setShifted(false);
        mCaps = false;
    }

    @Override
    public void onPress(int primaryCode) {
        // Visual feedback is handled by the key background drawable states.
    }

    @Override
    public void onRelease(int primaryCode) {
        // No-op.
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }

        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                ic.deleteSurroundingText(1, 0);
                break;

            case Keyboard.KEYCODE_SHIFT:
                mCaps = !mCaps;
                mKeyboardView.setShifted(mCaps);
                break;

            case Keyboard.KEYCODE_MODE_CHANGE:
                // Toggle to symbols/numbers layout.
                mKeyboardView.setKeyboard(mSymbolsKeyboard);
                break;

            case -100: // custom code: switch back to letters
                mKeyboardView.setKeyboard(mQwertyKeyboard);
                mKeyboardView.setShifted(false);
                mCaps = false;
                break;

            default:
                char code = (char) primaryCode;
                if (Character.isLetter(code) && mCaps) {
                    code = Character.toUpperCase(code);
                }
                ic.commitText(String.valueOf(code), 1);
                break;
        }
    }

    @Override
    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    @Override
    public void swipeLeft() {
        // No-op: gestures ignored per reference design.
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
