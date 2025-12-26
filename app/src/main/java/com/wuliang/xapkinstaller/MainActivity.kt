package com.wuliang.xapkinstaller

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wuliang.lib.createXapkInstaller
import java.io.File
import java.lang.Exception
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val PERMISSION_REQUEST_CODE = 99
private const val ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE = 120

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.install_tv).setOnClickListener {
            checkAndInstall()
        }
    }

    private fun checkAndInstall() {
        if (Build.VERSION.SDK_INT >= 26) {//8.0
            //来判断应用是否有权限安装apk
            val installAllowed = packageManager.canRequestPackageInstalls()
            //有权限
            if (installAllowed) {
                //安装apk
                install()
            } else {
                //无权限 申请权限
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.REQUEST_INSTALL_PACKAGES,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {//8.0以下
            //安装apk
            install()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE)
            return

        grantResults.forEachIndexed { index, grantResult ->
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (Manifest.permission.REQUEST_INSTALL_PACKAGES == permissions[index]) {
                        //引导用户去手动开启权限
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        startActivityForResult(
                            intent,
                            ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE
                        )
                    }
                }
                Toast.makeText(this, "权限不足！", Toast.LENGTH_SHORT).show();
                return
            }
        }

        install()
    }

    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE) {
            if (packageManager.canRequestPackageInstalls()) {
                install()
            } else {
                Toast.makeText(this, "权限不足！", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private fun install() {
        downloadXapkAndInstall()
    }

    private fun downloadXapkAndInstall() {
        val outputFileDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath +
                    File.separator + "xapk"
        )
        if (!outputFileDirectory.exists())
            outputFileDirectory.mkdir()

        val outputFileName = "LEGO_CITY.xapk"
        val outputFile = File(outputFileDirectory.absolutePath + File.separator + outputFileName)

        if (outputFile.exists()) {
            doInstall(outputFile.absolutePath)
        } else {
            Toast.makeText(this, "请先手动下载XAPK文件到Downloads/xapk目录", Toast.LENGTH_LONG).show()
        }
    }

    private fun doInstall(xapkFilePath: String) {
        val xapkInstaller = createXapkInstaller(xapkFilePath)

        if (xapkInstaller == null) {
            Toast.makeText(this, "安装xapk失败！", Toast.LENGTH_SHORT).show();
        } else {
            val installExecutor = Executors.newSingleThreadExecutor()
            installExecutor.execute {
                xapkInstaller.installXapk(this@MainActivity)
            }
        }
    }

}
