package com.github.uiautomator.compat;

import android.graphics.Point;
import android.os.RemoteException;
import android.view.Display;
import android.view.IRotationWatcher;

import com.github.uiautomator.util.InternalApi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class WindowManagerWrapper {
    private final Object windowManager;
    private final Method freezeRotationInjector;
    private final Method thawRotationInjector;

    public static interface RotationWatcher {
        public void onRotationChanged(int rotation);
    }

    public WindowManagerWrapper() {
        windowManager = getWindowManager();

        try {
            freezeRotationInjector = windowManager.getClass()
                    .getMethod("freezeRotation", int.class);
            thawRotationInjector = windowManager.getClass()
                    .getMethod("thawRotation");
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("freezeRotation/thawRotation is not supported");
        }
    }

    public void freezeRotation(int rotation) {
        try {
            freezeRotationInjector.invoke(windowManager, rotation);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void thawRotation() {
        try {
            thawRotationInjector.invoke(windowManager);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public Point getDisplaySize() {
        Object displayManager = InternalApi.getSingleton("android.hardware.display.DisplayManagerGlobal");
        try {
            Object displayInfo = displayManager.getClass().getMethod("getDisplayInfo", int.class)
                    .invoke(displayManager, Display.DEFAULT_DISPLAY);
            if (displayInfo != null) {
                Class<?> cls = displayInfo.getClass();
                int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
                int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
                int rotation = cls.getDeclaredField("rotation").getInt(displayInfo);
                if (rotation % 2 == 1) {
                    width ^= height;
                    height ^= width;
                    width ^= height;
                }
                return new Point(width, height);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getRotation() {
        try {
            Method getter = windowManager.getClass().getMethod("getDefaultDisplayRotation");
            return (Integer) getter.invoke(windowManager);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public Object watchRotation(final RotationWatcher watcher) {
        IRotationWatcher realWatcher = new IRotationWatcher.Stub() {
            @Override
            public void onRotationChanged(int rotation) throws RemoteException {
                watcher.onRotationChanged(rotation);
            }
        };

        try {
            Method getter = windowManager.getClass().getMethod("watchRotation", IRotationWatcher.class, int.class);
            getter.invoke(windowManager, realWatcher, 0);
            return realWatcher;
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("watchRotation is not supported: " + e.getMessage());
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException("watchRotation is not supported: " + e.getMessage());
        } catch (InvocationTargetException e) {
            throw new UnsupportedOperationException("watchRotation is not supported: " + e.getMessage());
        }
    }

    public static Object getWindowManager() {
        return InternalApi.getServiceAsInterface("window", "android.view.IWindowManager$Stub");
    }
}
