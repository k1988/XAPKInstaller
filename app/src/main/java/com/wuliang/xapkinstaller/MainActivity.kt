package com.wuliang.xapkinstaller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wuliang.lib.createXapkInstaller
import java.util.concurrent.Executors

private const val PERMISSION_REQUEST_CODE = 99
private const val ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE = 120
private const val FILE_PICKER_REQUEST_CODE = 121

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.install_tv).setOnClickListener {
            checkAndInstall()
        }
    }

    private fun checkAndInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val installAllowed = packageManager.canRequestPackageInstalls()
            if (installAllowed) {
                install()
            } else {
                // 无权限，申请权限
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.REQUEST_INSTALL_PACKAGES),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            install()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            install()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                startActivityForResult(intent, ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE)
            } else {
                Toast.makeText(this, "权限不足！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    install()
                } else {
                    Toast.makeText(this, "权限不足！", Toast.LENGTH_SHORT).show()
                }
            }
            FILE_PICKER_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    data?.data?.also { uri ->
                        doInstall(uri)
                    }
                }
            }
        }
    }

    private fun install() {
        selectXapkAndInstall()
    }

    private fun selectXapkAndInstall() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*" // 允所有文件类型，可以更具体地指定为 "application/vnd.android.package-archive" 或自定义mime类型
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(
                Intent.createChooser(intent, "选择一个XAPK文件"),
                FILE_PICKER_REQUEST_CODE
            )
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "请安装一个文件管理器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun doInstall(uri: Uri) {
        val xapkInstaller = createXapkInstaller(uri, this)

        if (xapkInstaller == null) {
            Toast.makeText(this, "创建XAPK安装程序失败！", Toast.LENGTH_SHORT).show()
        } else {
            val installExecutor = Executors.newSingleThreadExecutor()
            installExecutor.execute {
                xapkInstaller.installXapk(uri, this@MainActivity)
            }
        }
    }
}
