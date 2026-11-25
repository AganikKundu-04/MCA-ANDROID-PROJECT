package com.example;

import android.app.Application;
import android.content.ComponentCallbacks2;

import androidx.annotation.NonNull;

import com.example.ui.common.ThemePreferences;

/**
 * Application entry point owning the AppContainer, appearance theme, and Mirage native memory cleanup.
 */
public class SpendingApp extends Application {

    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePreferences.applySavedMode(this);
        appContainer = new AppContainer(this);
    }

    @NonNull
    public AppContainer getAppContainer() {
        if (appContainer == null) {
            appContainer = new AppContainer(this);
        }
        return appContainer;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (appContainer != null && level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            appContainer.onLowMemoryOrTrim();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (appContainer != null) {
            appContainer.onLowMemoryOrTrim();
        }
    }
}
