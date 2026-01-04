package com.wuliang.lib;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

public class PermissionPageUtil {
    private static final String TAG = "PermissionPageUtil";

    private PermissionPageUtil() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    public static void openPermissionActivity(Activity activity) {
        if (activity == null) {
            return;
        }

        String packageName = activity.getPackageName();
        Log.d(TAG, "Package: " + packageName);
        Log.d(TAG, "ROM: " + RomUtils.getRomInfo().getName());

        try {
            if (isHighAndroidVersion()) {
                goManagerFileAccess(activity);
            } else {
                if (RomUtils.isXiaomi()) {
                    goXiaoMiManager(activity, packageName);
                } else if (RomUtils.isOppo()) {
                    goOppoManager(activity);
                } else if (RomUtils.isVivo()) {
                    goVivoManager(activity);
                } else if (RomUtils.isMeizu()) {
                    goMeizuManager(activity, packageName);
                } else if (RomUtils.isHuawei()) {
                    goHuaWeiManager(activity);
                } else {
                    goIntentSetting(activity);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Open permission activity failed", e);
            goIntentSetting(activity);
        }
    }

    public static boolean isHighAndroidVersion() {
        return Build.VERSION.SDK_INT >= 30;
    }

    private static void goManagerFileAccess(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Go manager file access exception", e);
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception e2) {
                Log.e(TAG, "Go manager all files access exception", e2);
                goIntentSetting(activity);
            }
        }
    }

    private static void goXiaoMiManager(Context context, String packageName) {
        String miuiVersion = getMiuiVersion();
        Log.d(TAG, "MIUI Version: " + miuiVersion);
        Intent intent = new Intent();

        if ("V6".equals(miuiVersion) || "V7".equals(miuiVersion)) {
            intent.setAction("miui.intent.action.APP_PERM_EDITOR");
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
            intent.putExtra("extra_pkgname", packageName);
        } else if ("V8".equals(miuiVersion) || "V9".equals(miuiVersion)) {
            intent.setAction("miui.intent.action.APP_PERM_EDITOR");
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            intent.putExtra("extra_pkgname", packageName);
        } else {
            goIntentSetting(context);
            return;
        }

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Go Xiaomi manager exception", e);
            goIntentSetting(context);
        }
    }

    private static String getMiuiVersion() {
        java.io.BufferedReader reader = null;
        try {
            Process process = Runtime.getRuntime().exec("getprop ro.miui.ui.version.name");
            reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()), 1024);
            String line = reader.readLine();
            return line;
        } catch (Exception e) {
            Log.e(TAG, "Get MIUI version exception", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    Log.e(TAG, "Close reader exception", e);
                }
            }
        }
    }

    private static void goOppoManager(Context context) {
        doStartApplicationWithPackageName("com.coloros.safecenter", context);
    }

    private static void goVivoManager(Context context) {
        doStartApplicationWithPackageName("com.bairenkeji.icaller", context);
    }

    private static void goMeizuManager(Context context, String packageName) {
        try {
            Intent intent = new Intent("com.meizu.safe.security.SHOW_APPSEC");
            intent.addCategory("android.intent.category.DEFAULT");
            intent.putExtra("packageName", packageName);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Go Meizu manager exception", e);
            goIntentSetting(context);
        }
    }

    private static void goHuaWeiManager(Context context) {
        try {
            Intent intent = new Intent(context.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity"));
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Go Huawei manager exception", e);
            goIntentSetting(context);
        }
    }

    private static void doStartApplicationWithPackageName(String pkgName, Context context) {
        try {
            Intent intent = new Intent("android.intent.action.MAIN", (Uri) null);
            intent.addCategory("android.intent.category.LAUNCHER");
            intent.setPackage(pkgName);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Start application with package name exception", e);
            goIntentSetting(context);
        }
    }

    private static void goIntentSetting(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Go intent setting exception", e);
        }
    }

    public static boolean checkStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }
}
