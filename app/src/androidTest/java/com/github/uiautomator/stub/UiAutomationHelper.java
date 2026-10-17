package com.github.uiautomator.stub;

import android.app.UiAutomation;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Configurator;

/** Keeps direct UiAutomation access consistent with UiDevice's connection flags. */
final class UiAutomationHelper {
    private UiAutomationHelper() { }

    static UiAutomation getUiAutomation() {
        // Different flags reconnect UiAutomation and can suppress the helper service.
        return InstrumentationRegistry.getInstrumentation().getUiAutomation(
                Configurator.getInstance().getUiAutomationFlags());
    }
}
