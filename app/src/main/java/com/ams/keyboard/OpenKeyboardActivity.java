package com.ams.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

/**
 * Zero-UI transparent trampoline activity launched from the notification tray, Quick Settings
 * tile, or Settings screen to force AMS Keyboard open anywhere (Hacker's Keyboard mode).
 *
 * <p>Why this works reliably across Android 7–16:
 * <ol>
 *   <li>Launching via {@code PendingIntent.getActivity} causes SystemUI to collapse the
 *       notification shade automatically on every Android version and OEM skin.</li>
 *   <li>Waiting until {@link #onWindowFocusChanged(boolean)} ({@code hasFocus == true}, with a
 *       short fallback timer) ensures the notification shade has finished closing before we act.</li>
 *   <li>Finishing this trampoline <em>before</em> requesting the IME window returns window focus
 *       cleanly to the underlying app so Android's {@code InputMethodManagerService} does not
 *       auto-hide the keyboard during an Activity exit transition, and {@link AmsKeyboardService}
 *       then forces its soft-input window open directly on the underlying app.</li>
 * </ol>
 */
public class OpenKeyboardActivity extends Activity {

    public static final String ACTION_FORCE_SHOW_KEYBOARD =
            "com.ams.keyboard.action.FORCE_SHOW_KEYBOARD";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mHandled = false;
    private boolean mNeedPicker = false;
    private boolean mPickerShown = false;

    private final Runnable mFallbackRunnable = this::actAfterShadeCollapse;
    private final Runnable mPickerFinishRunnable = this::finishSilently;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        configureInvisibleWindow();
        beginFlow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        overridePendingTransition(0, 0);
        mHandler.removeCallbacksAndMessages(null);
        mHandled = false;
        mNeedPicker = false;
        mPickerShown = false;
        beginFlow();
    }

    private void configureInvisibleWindow() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = 1;
        lp.height = 1;
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.dimAmount = 0f;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        window.setAttributes(lp);
    }

    private void beginFlow() {
        if (!NotificationHelper.isAmsKeyboardEnabled(this)) {
            Toast.makeText(this, R.string.setup_needs_enable, Toast.LENGTH_SHORT).show();
            Intent setup = new Intent(this, MainActivity.class);
            setup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(setup);
            finishSilently();
            return;
        }

        mNeedPicker = !NotificationHelper.isAmsKeyboardSelected(this);
        if (mNeedPicker) {
            AmsKeyboardService.setPendingForceOpen(true);
        }

        if (hasWindowFocus()) {
            actAfterShadeCollapse();
        } else {
            // Fallback in case a custom OEM SystemUI delays or suppresses window-focus callbacks.
            mHandler.postDelayed(mFallbackRunnable, 240);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            return;
        }
        if (!mHandled) {
            mHandler.removeCallbacks(mFallbackRunnable);
            actAfterShadeCollapse();
        } else if (mPickerShown) {
            // Input-method picker dialog just closed; return focus to the underlying app so
            // AmsKeyboardService (if selected) immediately opens on it.
            finishSilently();
        }
    }

    private void actAfterShadeCollapse() {
        if (mHandled) {
            return;
        }
        mHandled = true;
        mHandler.removeCallbacks(mFallbackRunnable);

        if (mNeedPicker && !NotificationHelper.isAmsKeyboardSelected(this)) {
            mPickerShown = true;
            Toast.makeText(this, R.string.force_show_select_keyboard_hint, Toast.LENGTH_SHORT)
                    .show();
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                try {
                    imm.showInputMethodPicker();
                    mHandler.postDelayed(mPickerFinishRunnable, 4000);
                    return;
                } catch (Exception ignored) {
                }
            }
            finishSilently();
            return;
        }

        final Context appContext = getApplicationContext();
        finishSilently();
        AmsKeyboardService.triggerForceOpenFromTrampoline(appContext);
    }

    private void finishSilently() {
        mHandler.removeCallbacksAndMessages(null);
        if (!isFinishing()) {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
