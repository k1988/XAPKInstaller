package com.wuliang.lib

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.Locale

const val INSTALL_OPEN_APK_TAG = "install_open_apk_tag"

fun createXapkInstaller(uri: Uri, context: Context): XapkInstaller? {
    val resolver = context.contentResolver

    val fileName = getFileName(uri, context)
    val fileExtension = fileName?.substringAfterLast('.', "")?.lowercase()

    Log.d(INSTALL_OPEN_APK_TAG, "文件名: $fileName, 扩展名: $fileExtension")

    when (fileExtension) {
        "apk" -> {
            Log.d(INSTALL_OPEN_APK_TAG, "检测到普通APK文件，使用SimpleApkInstaller")
            return SimpleApkInstaller()
        }
        "aab" -> {
            Log.d(INSTALL_OPEN_APK_TAG, "检测到AAB文件，暂不支持")
            throw IllegalStateException("暂不支持AAB格式，请使用APK或XAPK格式")
        }
        "xapk", "apks", "apkm", "zip" -> {
            Log.d(INSTALL_OPEN_APK_TAG, "检测到压缩包格式，开始解析内容...")
        }
        else -> {
            Log.d(INSTALL_OPEN_APK_TAG, "未知文件格式: $fileExtension，尝试作为压缩包解析...")
        }
    }

    var apkCount = 0
    var hasSplitApk = false
    var hasManifest = false

    try {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                // 尝试一次 seek，用于快速失败
                try {
                    val pos = channel.position()
                    channel.position(pos)
                } catch (e: Exception) {
                    Log.e(
                        INSTALL_OPEN_APK_TAG,
                        "FileDescriptor does not support seek: $uri",
                        e
                    )
                    return null
                }

                ZipFile(channel).use { zip ->
                    val entries = zip.entries
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue

                        val name = entry.name.lowercase(Locale.US)

                        // manifest.json（允许在任意目录）
                        if (!hasManifest &&
                            (name == "manifest.json" || name.endsWith("/manifest.json"))
                        ) {
                            hasManifest = true
                        }

                        // APK 判断
                        if (name.endsWith(".apk")) {
                            apkCount++
                            if (name.contains("split_") || name.contains("split_config")) {
                                hasSplitApk = true
                            }
                        }
                    }
                }
            }
        } ?: run {
            Log.e(INSTALL_OPEN_APK_TAG, "Unable to open ParcelFileDescriptor: $uri")
            return null
        }
    } catch (e: Exception) {
        Log.e(
            INSTALL_OPEN_APK_TAG,
            "Failed to parse XAPK using ParcelFileDescriptor",
            e
        )
        return null
    }

    if (apkCount == 0) {
        Log.e(INSTALL_OPEN_APK_TAG, "No APK found in archive: $uri")
        throw IllegalStateException("在文件中未找到APK文件")
    }

    return when {
        hasSplitApk || apkCount > 1 -> {
            Log.d(INSTALL_OPEN_APK_TAG, "检测到多个APK或Split APK，使用MultiApkXapkInstaller")
            MultiApkXapkInstaller()
        }
        else -> {
            Log.d(INSTALL_OPEN_APK_TAG, "检测到单个APK，使用SingleApkXapkInstaller")
            SingleApkXapkInstaller()
        }
    }
}

private fun getFileName(uri: Uri, context: Context): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null, null)
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
