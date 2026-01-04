package com.wuliang.lib

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.gson.Gson
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Locale

class SingleApkXapkInstaller : XapkInstaller() {

    override fun installXapk(uri: Uri, context: Context) {
        val unzipDir = File(context.cacheDir, "xapk_install_${System.currentTimeMillis()}")
        if (!unzipDir.exists()) {
            unzipDir.mkdirs()
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    ZipFile(channel).use { zip ->
                        val entries = zip.entries
                        var manifestEntry: ZipArchiveEntry? = null
                        val apkEntries = mutableListOf<ZipArchiveEntry>()
                        val obbEntries = mutableListOf<ZipArchiveEntry>()

                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue

                            val name = entry.name.lowercase(Locale.US)

                            if (name == "manifest.json") {
                                manifestEntry = entry
                            } else if (name.endsWith(".apk")) {
                                apkEntries.add(entry)
                            } else if (name.startsWith("android/obb/")) {
                                obbEntries.add(entry)
                            }
                        }

                        if (manifestEntry == null) {
                            throw IllegalStateException("在XAPK中未找到manifest.json")
                        }

                        val manifestFile = File(unzipDir, "manifest.json")
                        zip.getInputStream(manifestEntry).use { zis ->
                            FileOutputStream(manifestFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }

                        val manifest = Gson().fromJson(InputStreamReader(manifestFile.inputStream()), Manifest::class.java)

                        val apkEntry = apkEntries.find { it.name == manifest.split_apks[0].file }
                        if (apkEntry == null) {
                            throw IllegalStateException("在XAPK中未找到APK文件: ${manifest.split_apks[0].file}")
                        }

                        val apkFile = File(unzipDir, manifest.split_apks[0].file)
                        zip.getInputStream(apkEntry).use { zis ->
                            FileOutputStream(apkFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }

                        if (manifest.expansions.isNotEmpty()) {
                            val obbEntry = obbEntries.find { it.name.endsWith(manifest.expansions[0].file) }
                            if (obbEntry != null) {
                                copyObbFile(context, zip, obbEntry, manifest)
                            }
                        }

                        enterInstallActivity(arrayListOf(apkFile.absolutePath), context)
                    }
                }
            } ?: throw IllegalStateException("无法为URI打开ParcelFileDescriptor: $uri")
        } catch (e: Exception) {
            Log.e(INSTALL_OPEN_APK_TAG, "从URI安装XAPK失败: $uri", e)
            throw e
        }
    }

    private fun copyObbFile(context: Context, zip: ZipFile, obbEntry: ZipArchiveEntry, manifest: Manifest) {
        val obbFileName = File(manifest.expansions[0].file).name
        val externalStorageDir = Environment.getExternalStorageDirectory()

        Log.d(INSTALL_OPEN_APK_TAG, "外部存储目录: ${externalStorageDir.absolutePath}")
        Log.d(INSTALL_OPEN_APK_TAG, "外部存储目录是否存在: ${externalStorageDir.exists()}")
        Log.d(INSTALL_OPEN_APK_TAG, "外部存储目录是否可读: ${externalStorageDir.canRead()}")
        Log.d(INSTALL_OPEN_APK_TAG, "外部存储目录是否可写: ${externalStorageDir.canWrite()}")

        val obbTargetDir = File(
            externalStorageDir,
            "Android/obb/${manifest.package_name}"
        )

        Log.d(INSTALL_OPEN_APK_TAG, "OBB目标目录: ${obbTargetDir.absolutePath}")
        Log.d(INSTALL_OPEN_APK_TAG, "OBB目标目录是否存在: ${obbTargetDir.exists()}")
        Log.d(INSTALL_OPEN_APK_TAG, "OBB目标目录父目录: ${obbTargetDir.parent}")
        Log.d(INSTALL_OPEN_APK_TAG, "OBB目标目录父目录是否存在: ${obbTargetDir.parentFile?.exists()}")

        if (!obbTargetDir.exists()) {
            Log.d(INSTALL_OPEN_APK_TAG, "开始创建OBB目录...")
            val parentDir = obbTargetDir.parentFile
            if (parentDir != null && !parentDir.exists()) {
                Log.d(INSTALL_OPEN_APK_TAG, "父目录不存在，尝试创建父目录: ${parentDir.absolutePath}")
                val parentCreated = parentDir.mkdirs()
                Log.d(INSTALL_OPEN_APK_TAG, "父目录创建结果: $parentCreated")
                Log.d(INSTALL_OPEN_APK_TAG, "父目录创建后是否存在: ${parentDir.exists()}")
            }

            val dirCreated = obbTargetDir.mkdirs()
            Log.d(INSTALL_OPEN_APK_TAG, "OBB目录创建结果: $dirCreated")
            Log.d(INSTALL_OPEN_APK_TAG, "OBB目录创建后是否存在: ${obbTargetDir.exists()}")

            if (!obbTargetDir.exists()) {
                Log.e(INSTALL_OPEN_APK_TAG, "无法创建OBB目录: ${obbTargetDir.absolutePath}")
                Log.e(INSTALL_OPEN_APK_TAG, "当前SDK版本: ${Build.VERSION.SDK_INT}")
                Log.e(INSTALL_OPEN_APK_TAG, "是否为外部存储管理器: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else "N/A"}")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    throw IllegalStateException(
                        "无法创建OBB目录。\n" +
                        "目标路径: ${obbTargetDir.absolutePath}\n" +
                        "外部存储路径: ${externalStorageDir.absolutePath}\n" +
                        "外部存储可写: ${externalStorageDir.canWrite()}\n" +
                        "Android 10+ 需要特殊权限。\n" +
                        "请确保应用有存储权限，或在设置中授予'管理所有文件'权限。"
                    )
                } else {
                    throw IllegalStateException(
                        "无法创建OBB目录。\n" +
                        "目标路径: ${obbTargetDir.absolutePath}\n" +
                        "外部存储可写: ${externalStorageDir.canWrite()}\n" +
                        "请检查存储权限"
                    )
                }
            }
        }

        val obbTargetFile = File(obbTargetDir, obbFileName)
        Log.d(INSTALL_OPEN_APK_TAG, "OBB目标文件: ${obbTargetFile.absolutePath}")
        Log.d(INSTALL_OPEN_APK_TAG, "OBB文件大小: ${obbEntry.size} bytes")

        try {
            zip.getInputStream(obbEntry).use { zis ->
                FileOutputStream(obbTargetFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (zis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        if (totalBytes % (10 * 1024 * 1024) == 0L) {
                            Log.d(INSTALL_OPEN_APK_TAG, "已复制: ${totalBytes / (1024 * 1024)} MB / ${obbEntry.size / (1024 * 1024)} MB")
                        }
                    }
                    fos.flush()
                }
            }
            Log.d(INSTALL_OPEN_APK_TAG, "OBB文件已复制到: ${obbTargetFile.absolutePath}")
            Log.d(INSTALL_OPEN_APK_TAG, "OBB文件大小: ${obbTargetFile.length()} bytes")
        } catch (e: Exception) {
            Log.e(INSTALL_OPEN_APK_TAG, "复制OBB文件失败: ${obbTargetFile.absolutePath}", e)
            throw IllegalStateException("复制OBB文件失败: ${e.message}")
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