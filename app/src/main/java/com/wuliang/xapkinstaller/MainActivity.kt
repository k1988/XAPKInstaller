package com.wuliang.xapkinstaller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wuliang.lib.PermissionPageUtil
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                checkInstallPermissionAndInstall()
            } else {
                Toast.makeText(this, "需要管理所有文件的权限", Toast.LENGTH_SHORT).show()
                PermissionPageUtil.openPermissionActivity(this)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

            if (writePermission != PackageManager.PERMISSION_GRANTED || readPermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                checkInstallPermissionAndInstall()
            }
        } else {
            checkInstallPermissionAndInstall()
        }
    }

    private fun checkInstallPermissionAndInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val installAllowed = packageManager.canRequestPackageInstalls()
            if (installAllowed) {
                install()
            } else {
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
            checkInstallPermissionAndInstall()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    // 加了这一行直接跳转到应用对应的设置，面不是应用列表
                    data = Uri.parse("package:$packageName")
                }
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
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadUri = android.provider.DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download"
        )

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        try {
            startActivityForResult(
                Intent.createChooser(intent, "选择XAPK/APK文件"),
                FILE_PICKER_REQUEST_CODE
            )
        } catch (ex: android.content.ActivityNotFoundException) {
            val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.android.package-archive"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            try {
                startActivityForResult(
                    Intent.createChooser(fallbackIntent, "选择XAPK/APK文件"),
                    FILE_PICKER_REQUEST_CODE
                )
            } catch (ex2: android.content.ActivityNotFoundException) {
                val finalFallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    startActivityForResult(
                        Intent.createChooser(finalFallbackIntent, "选择XAPK/APK文件"),
                        FILE_PICKER_REQUEST_CODE
                    )
                } catch (ex3: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, "请安装一个文件管理器", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun doInstall(uri: Uri) {
        val fileName = getFileName(uri)
        val fileExtension = fileName?.substringAfterLast('.', "")

        val validExtensions = listOf("apk", "xapk", "apks", "apkm", "zip", "aab")

        if (fileExtension == null || !validExtensions.contains(fileExtension.lowercase())) {
            Toast.makeText(this, "不支持的文件格式：$fileExtension\n请选择APK、XAPK、APKS、APKM、ZIP或AAB文件", Toast.LENGTH_LONG).show()
            return
        }

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

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null, null)
            cursor?.use {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst()) {
                    result = it.getString(index)
                }
            }
        } else if (uri.scheme == "file") {
            result = uri.lastPathSegment
        }
        return result
    }
}
