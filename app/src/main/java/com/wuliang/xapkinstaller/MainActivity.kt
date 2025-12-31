package com.wuliang.xapkinstaller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wuliang.lib.createXapkInstaller
import java.io.File
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
                    arrayOf(Manifest.permission.REQUEST_INSTALL_PACKAGES),
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

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            install()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //引导用户去手动开启权限
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                startActivityForResult(
                    intent,
                    ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE
                )
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
                if (packageManager.canRequestPackageInstalls()) {
                    install()
                } else {
                    Toast.makeText(this, "权限不足！", Toast.LENGTH_SHORT).show()
                }
            }
            FILE_PICKER_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    data?.data?.also { uri ->
                        val file = getFileFromUri(uri)
                        if (file != null && file.name.endsWith(".xapk", true)) {
                            doInstall(file.absolutePath)
                        } else {
                            Toast.makeText(this, "获取文件失败或文件不是XAPK", Toast.LENGTH_SHORT).show()
                        }
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
            type = "*/*"
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

    @SuppressLint("Range")
    private fun getFileFromUri(uri: Uri): File? {
        var fileName = "temp.xapk"
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }

        val destinationFilename = File(cacheDir, fileName)
        try {
            contentResolver.openInputStream(uri)?.use { ins ->
                destinationFilename.outputStream().use { outs ->
                    ins.copyTo(outs)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }
        return destinationFilename
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
