package com.wuliang.lib

import android.content.Context
import android.net.Uri

/**
 * <pre>
 *     author : wuliang
 *     time   : 2019/09/27
 * </pre>
 */
abstract class XapkInstaller {

    abstract fun installXapk(uri: Uri, context: Context)

}