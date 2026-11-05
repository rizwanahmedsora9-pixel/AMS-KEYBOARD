package com.ams.keyboard;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Launcher / settings screen for AMS Keyboard.
 *
 * Opens the system input-method settings so the user can enable AMS Keyboard.
 * No ads and no third-party icons are shown here.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
