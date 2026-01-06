package com.wuliang.lib

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SimpleApkInstaller : XapkInstaller() {

    override fun installXapk(uri: Uri, context: Context) {
        val apkPath = when (uri.scheme) {
            "file" -> {
                val path = uri.path
                Log.d(INSTALL_OPEN_APK_TAG, "检测到file:// URI: $path")

                val file = File(path)
                if (!file.exists()) {
                    throw IllegalStateException("APK文件不存在: $path")
                }
                if (!file.canRead()) {
                    throw IllegalStateException("无法读取APK文件: $path")
                }

                Log.d(INSTALL_OPEN_APK_TAG, "APK文件存在且可读，大小: ${file.length()} bytes")
                path
            }
            "content" -> {
                Log.d(INSTALL_OPEN_APK_TAG, "检测到content:// URI，需要复制到缓存目录")
                val tempFile = File(context.cacheDir, "install_${System.currentTimeMillis()}.apk")
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        FileInputStream(pfd.fileDescriptor).use { fis ->
                            FileOutputStream(tempFile).use { fos ->
                                fis.copyTo(fos)
                            }
                        }
                    } ?: throw IllegalStateException("无法为URI打开ParcelFileDescriptor: $uri")

                    Log.d(INSTALL_OPEN_APK_TAG, "APK文件已复制到: ${tempFile.absolutePath}")
                    Log.d(INSTALL_OPEN_APK_TAG, "复制后文件大小: ${tempFile.length()} bytes")
                    tempFile.absolutePath
                } catch (e: Exception) {
                    Log.e(INSTALL_OPEN_APK_TAG, "从URI安装APK失败: $uri", e)
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    throw e
                }
            }
            else -> {
                throw IllegalStateException("不支持的URI scheme: ${uri.scheme}")
            }
        }

        if (apkPath != null) {
            Log.d(INSTALL_OPEN_APK_TAG, "准备安装APK: $apkPath")
            enterInstallActivity(arrayListOf(apkPath), context)
        }
    }

    private fun enterInstallActivity(apkFilePaths: ArrayList<String>, context: Context) {
        val intent = Intent(context, InstallActivity::class.java)
        intent.putStringArrayListExtra(InstallActivity.KEY_APK_PATHS, apkFilePaths)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
