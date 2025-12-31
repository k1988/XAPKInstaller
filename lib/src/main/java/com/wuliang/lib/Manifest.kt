package com.wuliang.lib

import com.google.gson.annotations.SerializedName

data class Manifest(
    @SerializedName("package_name")
    val package_name: String,

    @SerializedName("split_apks")
    val split_apks: List<ApkInfo>,

    @SerializedName("expansions")
    val expansions: List<ExpansionInfo>
)

data class ApkInfo(
    @SerializedName("file")
    val file: String
)

data class ExpansionInfo(
    @SerializedName("file")
    val file: String
)