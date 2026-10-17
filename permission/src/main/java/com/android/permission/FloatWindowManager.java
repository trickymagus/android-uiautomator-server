/*
 * Copyright (C) 2016 Facishare Technology Co., Ltd. All Rights Reserved.
 */
package com.android.permission;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.android.permission.rom.MeizuUtils;
import com.android.permission.rom.RomUtils;
import com.android.permission.rom.VivoUtils;

/**
 * Description:
 *
 * @author zhaozp
 * @since 2016-10-17
 */

public class FloatWindowManager {
    private static final String TAG = "FloatWindowManager";

    private static volatile FloatWindowManager instance;
    private Dialog dialog;

    public static FloatWindowManager getInstance() {
        if (instance == null) {
            synchronized (FloatWindowManager.class) {
                if (instance == null) {
                    instance = new FloatWindowManager();
                }
            }
        }
        return instance;
    }

    public boolean checkFloatPermission(Context context) {
        Log.i(TAG, "Start check permission");
        if (checkPermission(context)) {
            return true;
        } else {
            applyPermission(context);
            return false;
        }
    }

    public boolean checkPermission(Context context) {
        return commonROMPermissionCheck(context);
    }

    private boolean meizuPermissionCheck(Context context) {
        return MeizuUtils.checkFloatWindowPermission(context);
    }

    private boolean vivoPermissionCheck(Context context) {
        return VivoUtils.checkFloatWindowPermission(context);
    }

    private boolean commonROMPermissionCheck(Context context) {
        //最新发现魅族6.0的系统这种方式不好用，天杀的，只有你是奇葩，没办法，单独适配一下
        if (RomUtils.checkIsMeizuRom()) {
            return meizuPermissionCheck(context);
            // VIVO的i管家比较厉害
        } else if (RomUtils.isVivoSystem()) {
            return vivoPermissionCheck(context);
        } else {
            try {
                return Settings.canDrawOverlays(context);
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            return true;
        }
    }

    public void applyPermission(Context context) {
        commonROMPermissionApply(context);
    }

    /**
     * 直接去申请权限
     *
     * @param context
     */
    public void applyPermissionDirect(Context context) {
        if (RomUtils.checkIsMeizuRom()) {
            MeizuUtils.applyPermission(context);
        } else if (RomUtils.isVivoSystem()) {
            VivoUtils.applyPermission(context);
        } else {
            try {
                commonROMPermissionApplyInternal(context);
            } catch (Exception e) {
                Log.e(TAG, "Throw exception " + e.getMessage(), e);
            }
        }
    }

    private void meizuROMPermissionApply(final Context context) {
        Log.i(TAG, "Request Flyme permission");
        showConfirmDialog(context, new OnConfirmResult() {
            @Override
            public void confirmResult(boolean confirm) {
                if (confirm) {
                    MeizuUtils.applyPermission(context);
                } else {
                    Log.e(TAG, "ROM:meizu, user manually refuse OVERLAY_PERMISSION");
                }
            }
        });
    }

    /**
     * 通用 rom 权限申请
     */
    private void commonROMPermissionApply(final Context context) {
        //这里也一样，魅族系统需要单独适配
        if (RomUtils.checkIsMeizuRom()) {
            meizuROMPermissionApply(context);
        } else {
            showConfirmDialog(context, new OnConfirmResult() {
                @Override
                public void confirmResult(boolean confirm) {
                    if (confirm) {
                        try {
                            commonROMPermissionApplyInternal(context);
                        } catch (Exception e) {
                            Log.e(TAG, Log.getStackTraceString(e));
                        }
                    } else {
                        Log.d(TAG, "user manually refuse OVERLAY_PERMISSION");
                        //需要做统计效果
                    }
                }
            });
        }
    }

    public static void commonROMPermissionApplyInternal(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        context.startActivity(intent);
    }

    private void showConfirmDialog(Context context, OnConfirmResult result) {
        showConfirmDialog(context, "您的手机没有授予悬浮窗权限，请开启后再试", result);
    }

    private void showConfirmDialog(Context context, String message, final OnConfirmResult result) {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }

        dialog = new AlertDialog.Builder(context).setCancelable(true).setTitle("")
                .setMessage(message)
                .setPositiveButton("现在去开启",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                result.confirmResult(true);
                                dialog.dismiss();
                            }
                        }).setNegativeButton("暂不开启",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                result.confirmResult(false);
                                dialog.dismiss();
                            }
                        }).create();
        dialog.show();
    }

    private interface OnConfirmResult {
        void confirmResult(boolean confirm);
    }
}
