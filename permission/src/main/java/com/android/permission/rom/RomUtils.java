/*
 * Copyright (C) 2016 Facishare Technology Co., Ltd. All Rights Reserved.
 */
package com.android.permission.rom;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Description:
 *
 * @author zhaozp
 * @since 2016-05-23
 */
public class RomUtils {
    private static final String TAG = "RomUtils";

    private static Boolean isSony = null;
    /**
     * 判断是否是sony
     * @return
     */
    public static boolean isSonySystem() {
        if (isSony == null) {
            isSony = Build.MANUFACTURER.toUpperCase().contains("SONY");
        }
        return isSony;
    }

    private static Boolean isSamSung = null;

    /**
     * 判断是否是三星
     *
     * @return
     */
    public static boolean isSamSungSystem() {
        if (isSamSung == null) {
            isSamSung = Build.MANUFACTURER.toUpperCase().contains("SAMSUNG");
        }
        return isSamSung;
    }

    private static Boolean isVivo = null;
    public static boolean isVivoSystem() {
        if (isVivo == null) {
            isVivo = Build.MANUFACTURER.toUpperCase().contains("VIVO");
        }
        return isVivo;
    }

    private static Boolean isGoogle = null;
    public static boolean isGoogleSystem() {
        if (isGoogle == null) {
            isGoogle = Build.MANUFACTURER.toUpperCase().contains("GOOGLE");
        }

        return isGoogle;
    }

    private static Boolean isSmartisan = null;
    public static boolean isSmartisanSystem() {
        if (isSmartisan == null) {
            isSmartisan = Build.MANUFACTURER.toUpperCase().contains("SMARTISAN");
        }
        return isSmartisan;
    }


    public static String getSystemProperty(String propName) {
        String line;
        BufferedReader input = null;
        try {
            Process p = Runtime.getRuntime().exec("getprop " + propName);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            Log.e(TAG, "Unable to read sysprop " + propName, ex);
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.e(TAG, "Exception while closing InputStream", e);
                }
            }
        }
        return line;
    }

    private static Boolean isMeizu = null;
    public static boolean checkIsMeizuRom() {
        //return Build.MANUFACTURER.contains("Meizu");
        if (isMeizu == null) {
            String meizuFlymeOSFlag = getSystemProperty("ro.build.display.id");
            if (TextUtils.isEmpty(meizuFlymeOSFlag)) {
                isMeizu = false;
            } else if (meizuFlymeOSFlag.contains("flyme") || meizuFlymeOSFlag.toLowerCase().contains("flyme")) {
                isMeizu = true;
            } else {
                isMeizu = false;
            }
        }

        return isMeizu;
    }

    public static String getDeviceSerialNO() {
        try {
            return getSystemProperty("ro.serialno");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("SystemUtil", e.getMessage());
        }
        return null;
    }
}
