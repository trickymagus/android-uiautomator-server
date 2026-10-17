//
// Copied from https://github.com/openstf/STFService.apk/blob/master/app/src/main/java/jp/co/cyberagent/stf/compat/InputManagerWrapper.java

package com.github.uiautomator.compat;

import android.view.InputEvent;
import android.view.KeyEvent;

import com.github.uiautomator.util.InternalApi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InputManagerWrapper {
    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
    private final Object inputManager;
    private final Method injector;

    public InputManagerWrapper() {
        try {
            inputManager = InternalApi.getSingleton("android.hardware.input.InputManager");

            // injectInputEvent() is @hidden
            injector = inputManager.getClass()
                    // public boolean injectInputEvent(InputEvent event, int mode)
                    .getMethod("injectInputEvent", InputEvent.class, int.class);
        }
        catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("injectInputEvent is not supported");
        }
    }

    public boolean injectKeyEvent(KeyEvent event) {
        return injectInputEvent(event);
    }

    public boolean injectInputEvent(InputEvent event) {
        try {
            injector.invoke(inputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC);
            return true;
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
            return false;
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
            return false;
        }
    }
}
