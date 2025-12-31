package com.wuliang.lib

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import java.io.InputStreamReader

class SingleApkXapkInstaller : XapkInstaller() {

    override fun installXapk(uri: Uri, context: Context) {
        val unzipDir = File(context.cacheDir, "xapk_install_${System.currentTimeMillis()}")
        if (!unzipDir.exists()) {
            unzipDir.mkdirs()
        }

        try {
            context.contentResolver.openInputStream(uri)?.use {
                ZipUtil.unpack(it, unzipDir)
            } ?: throw IllegalStateException("无法为URI打开输入流: $uri")

            val manifestFile = File(unzipDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw IllegalStateException("在XAPK中未找到manifest.json")
            }

            val manifest = Gson().fromJson(InputStreamReader(manifestFile.inputStream()), Manifest::class.java)
            val apkFile = File(unzipDir, manifest.split_apks[0].file)
            val obbFile = File(unzipDir, manifest.expansions[0].file)

            if (obbFile.exists()) {
                val obbTargetDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Android/obb/${manifest.package_name}"
                )
                if (!obbTargetDir.exists()) {
                    obbTargetDir.mkdirs()
                }
                val obbTargetFile = File(obbTargetDir, obbFile.name)
                obbFile.renameTo(obbTargetFile)
            }

            if (apkFile.exists()) {
                enterInstallActivity(arrayListOf(apkFile.absolutePath), context)
            } else {
                Log.e(INSTALL_OPEN_APK_TAG, "在XAPK中未找到APK文件.")
            }
        } catch (e: Exception) {
            Log.e(INSTALL_OPEN_APK_TAG, "从URI安装XAPK失败: $uri", e)
        } finally {
            // 清理缓存目录
            unzipDir.deleteRecursively()
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