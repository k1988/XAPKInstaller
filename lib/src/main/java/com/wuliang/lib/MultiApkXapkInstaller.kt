package com.wuliang.lib

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

class MultiApkXapkInstaller : XapkInstaller() {

    override fun installXapk(uri: Uri, context: Context) {
        val unzipDir = File(
            context.cacheDir,
            "xapk_install_${System.currentTimeMillis()}"
        )
        unzipDir.mkdirs()

        val apkFilePaths = ArrayList<String>()

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    ZipFile(channel).use { zip ->
                        val entries = zip.entries
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue

                            val name = entry.name
                            if (!name.lowercase(Locale.US).endsWith(".apk")) {
                                continue
                            }

                            val outFile = File(unzipDir, File(name).name)
                            zip.getInputStream(entry).buffered().use { zis ->
                                FileOutputStream(outFile).buffered().use { fos ->
                                    zis.copyTo(fos)
                                }
                            }

                            apkFilePaths.add(outFile.absolutePath)
                        }
                    }
                }
            } ?: throw IllegalStateException("Unable to open ParcelFileDescriptor: $uri")

            if (apkFilePaths.isEmpty()) {
                Log.e(INSTALL_OPEN_APK_TAG, "No APK files found in XAPK: $uri")
                unzipDir.deleteRecursively()
                return
            }

            enterInstallActivity(uri.toString(), apkFilePaths, context)

        } catch (e: Exception) {
            Log.e(INSTALL_OPEN_APK_TAG, "Install XAPK failed: $uri", e)
            unzipDir.deleteRecursively()
        }
    }

    private fun enterInstallActivity(
        xapkPath: String,
        apkFilePaths: ArrayList<String>,
        context: Context
    ) {
        Log.d(
            INSTALL_OPEN_APK_TAG,
            "multi apk installer, apkFilePaths=$apkFilePaths"
        )

        val intent = Intent(context, InstallActivity::class.java)
        intent.putStringArrayListExtra(
            InstallActivity.KEY_APK_PATHS,
            apkFilePaths
        )
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
