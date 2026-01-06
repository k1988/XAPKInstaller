package com.wuliang.lib;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.wuliang.lib.FileUtilsKt.getFileName;

/**
 * @author Jason Ran
 * @date 2019/9/26
 */
public class InstallActivity extends AppCompatActivity {

    private static final String TAG = "InstallActivity";

    private static final String PACKAGE_INSTALLED_ACTION =
            "com.wuliang.common.SESSION_API_PACKAGE_INSTALLED";

    public static final String KEY_APK_PATHS = "apk_path";

    private List<String> apkPaths;
    private ExecutorService installXapkExectuor;

    private PackageInstaller.Session mSession;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_install);

        initData();
        installXapk();
    }

    public void initData() {
        apkPaths = getIntent().getStringArrayListExtra(KEY_APK_PATHS);
    }

    private void installXapk() {
        if (Build.VERSION.SDK_INT < 21) {
            Toast.makeText(this, "暂时不支持安装,请更新到Android 5.0及以上版本", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (apkPaths == null || apkPaths.isEmpty()) {
            Toast.makeText(this, "解析apk出错或已取消", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (RomUtils.isMeizu() || RomUtils.isVivo()) {
            Toast.makeText(this,"魅族或VIVO系统用户如遇安装被中止或者安装失败的情况，请尝试联系手机平台客服，或者更换系统内置包安装器再重试",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        installXapkExectuor = Executors.newSingleThreadExecutor();
        installXapkExectuor.execute(() -> {
            try {
                if (apkPaths == null || apkPaths.isEmpty()) {
                    Toast.makeText(this, "解析apk出错或已取消", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                mSession = initSession();

                for (String apkPath : apkPaths) {
                    addApkToInstallSession(apkPath, mSession);
                }

                commitSession(mSession);
            } catch (IOException e) {
                e.printStackTrace();
                abandonSession();
            }
        });
    }

    @TargetApi(21)
    private PackageInstaller.Session initSession() throws IOException {
        PackageInstaller.Session session;
        PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        int sessionId;
        sessionId = packageInstaller.createSession(params);
        if (sessionId == -1) {
            throw new IOException("Failed to create package installer session");
        }
        session = packageInstaller.openSession(sessionId);
        if (session == null) {
            throw new IOException("Failed to open package installer session");
        }

        return session;
    }

    @TargetApi(21)
    private void addApkToInstallSession(String filePath, PackageInstaller.Session session)
            throws IOException {
        // It's recommended to pass the file size to openWrite(). Otherwise installation may fail
        // if the disk is almost full.
        try (OutputStream packageInSession = session.openWrite(getFileName(filePath), 0, new File(filePath).length());
             InputStream is = new BufferedInputStream(new FileInputStream(filePath))) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = is.read(buffer)) >= 0) {
                packageInSession.write(buffer, 0, n);
            }
        }
    }

    @TargetApi(21)
    private void commitSession(PackageInstaller.Session session) {
        // Create an install status receiver.
        Intent intent = new Intent(this, InstallActivity.class);
        intent.setAction(PACKAGE_INSTALLED_ACTION);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
            PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
        IntentSender statusReceiver = pendingIntent.getIntentSender();

        // Commit the session (this will start the installation workflow).
        session.commit(statusReceiver);
    }

    @TargetApi(21)
    private void abandonSession() {
        if (mSession != null) {
            mSession.abandon();
            mSession.close();
        }
    }

    @TargetApi(21)
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent called, action: " + intent.getAction());
        Log.d(TAG, "intent data: " + intent.getData());
        Log.d(TAG, "intent categories: " + intent.getCategories());

        Bundle extras = intent.getExtras();
        if (extras != null) {
            Log.d(TAG, "extras keys: " + extras.keySet());
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, "extra[" + key + "] = " + value + " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
            }
        } else {
            Log.d(TAG, "extras: null");
        }

        if (PACKAGE_INSTALLED_ACTION.equals(intent.getAction())) {
            int status = -100;
            String message = "";

            if (extras != null) {
                status = extras.getInt(PackageInstaller.EXTRA_STATUS, -100);
                message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.d(TAG, "PackageInstaller status: " + status + ", message: " + message);
            }

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Log.d(TAG, "STATUS_PENDING_USER_ACTION");
                    Intent confirmIntent = (Intent) extras.get(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        startActivity(confirmIntent);
                    } else {
                        Log.e(TAG, "Confirm intent is null");
                        Toast.makeText(this, "安装确认界面启动失败", Toast.LENGTH_LONG).show();
                        finish();
                    }
                    break;
                case PackageInstaller.STATUS_SUCCESS:
                    Log.d(TAG, "STATUS_SUCCESS");
                    Toast.makeText(this, "安装成功!", Toast.LENGTH_SHORT).show();
                    finish();
                    break;
                case PackageInstaller.STATUS_FAILURE:
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                case PackageInstaller.STATUS_FAILURE_CONFLICT:
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                case PackageInstaller.STATUS_FAILURE_INVALID:
                case PackageInstaller.STATUS_FAILURE_STORAGE:
                    Log.d(TAG, "STATUS_FAILURE: " + status + ", message: " + message);
                    Toast.makeText(this, "安装失败: " + (message != null ? message : "未知错误"),
                            Toast.LENGTH_LONG).show();
                    finish();
                    break;
                default:
                    Log.e(TAG, "Unrecognized status received from installer: " + status);
                    Toast.makeText(this, "安装失败，状态码: " + status,
                            Toast.LENGTH_LONG).show();
                    finish();
            }
        } else {
            Log.d(TAG, "Intent action does not match PACKAGE_INSTALLED_ACTION");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (installXapkExectuor != null && !installXapkExectuor.isShutdown()) {
            installXapkExectuor.shutdown();
        }
        abandonSession();
    }
}
