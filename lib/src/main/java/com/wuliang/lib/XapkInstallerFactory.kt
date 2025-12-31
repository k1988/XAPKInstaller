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
        return null
    }

    return when {
        hasSplitApk || apkCount > 1 -> {
            MultiApkXapkInstaller()
        }
        else -> {
            SingleApkXapkInstaller()
        }
    }
}
