# XAPK Installer 安装流程详解

## 项目概述

XAPK Installer 是一个用于安装 Android 应用安装包的工具，支持 APK、XAPK、APKS 等格式的安装。项目基于 Android 5.0+ (API 21+) 开发，使用 Kotlin 和 Java 混合编写。

## 支持的文件格式


## 现在支持的格式：

| 格式 | 安装器 | 说明 |
|------|--------|------|
| **.apk** | SimpleApkInstaller | 普通APK文件 |
| **.xapk** | SingleApkXapkInstaller | 单个APK + OBB文件 |
| **.apks** | MultiApkXapkInstaller | 多个Split APK |
| **.apkm** | SingleApkXapkInstaller | APKMirror格式 |
| **.zip** | Single/MultiApkXapkInstaller | 包含APK的ZIP文件 |
| **.aab** | 不支持 | Android App Bundle（需要特殊处理） |

## 工作流程：

1. 用户选择文件
2. 工厂类检测文件扩展名
3. 根据格式选择合适的安装器：
   - `.apk` → 直接使用 SimpleApkInstaller
   - `.xapk/.apks/.apkm/.zip` → 解析内容，判断是单个还是多个APK
   - `.aab` → 提示用户暂不支持
4. 使用对应的安装器进行安装

## 项目结构

```
XAPKInstaller/
├── app/                          # 主应用模块
│   └── src/main/java/com/wuliang/xapkinstaller/
│       └── MainActivity.kt         # 主界面，处理权限和安装入口
└── lib/                          # 安装库模块
    └── src/main/java/com/wuliang/lib/
        ├── XapkInstaller.kt               # 安装器抽象基类
        ├── XapkInstallerFactory.kt         # 安装器工厂类
        ├── SingleApkXapkInstaller.kt       # 单 APK 安装器
        ├── MultiApkXapkInstaller.kt       # 多 APK 安装器
        ├── InstallActivity.java             # 多 APK 安装界面
        ├── AppUtils.kt                    # 应用安装工具
        ├── FileUtils.kt                    # 文件操作工具
        ├── RomUtils.java                  # ROM 识别工具
        └── UtilsFileProvider.java         # FileProvider 实现
```

## 安装流程详解

### 1. 主流程入口 (MainActivity)

#### 1.1 权限检查流程

```
用户点击安装按钮
    ↓
检查 Android 版本
    ↓
Android 8.0+ (API 26+) ?
    ├─ 是 → 检查是否有安装权限
    │       ├─ 有权限 → 直接安装
    │       └─ 无权限 → 请求权限
    │                   ├─ 用户同意 → 安装
    │                   └─ 用户拒绝 → 引导到设置页面
    └─ 否 → 直接安装
```

#### 1.2 权限相关

**必需权限：**
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

**权限处理逻辑：**
- `REQUEST_INSTALL_PACKAGES`：Android 8.0+ 需要此权限才能安装 APK
- `WRITE_EXTERNAL_STORAGE`：用于访问和写入外部存储中的安装包

#### 1.3 安装入口方法

```kotlin
private fun install() {
    downloadXapkAndInstall()
}
```

### 2. 安装器创建流程 (XapkInstallerFactory)

#### 2.1 创建安装器

```kotlin
fun createXapkInstaller(xapkFilePath: String?): XapkInstaller?
```

**流程步骤：**

```
传入文件路径
    ↓
验证文件路径是否为空
    ↓
创建解压输出目录
    ↓
解压 APK 文件到临时目录
    ├─ 只提取 .apk 文件
    └─ 忽略其他文件
    ↓
解压 OBB 资源到 Android/obb 目录
    ↓
统计 APK 文件数量
    ↓
根据 APK 数量选择安装器
    ├─ 1 个 APK → SingleApkXapkInstaller
    └─ 多个 APK → MultiApkXapkInstaller
```

#### 2.2 解压逻辑

**APK 解压：**
```kotlin
ZipUtil.unpack(xapkFile, unzipOutputDir, NameMapper { name ->
    when {
        name.endsWith(".apk") -> return@NameMapper name
        else -> return@NameMapper null
    }
})
```

**OBB 资源解压：**
```kotlin
ZipUtil.unpack(xapkFile, unzipOutputDir, NameMapper { name ->
    when {
        name.startsWith(prefix) -> return@NameMapper name.substring(prefix.length)
        else -> return@NameMapper null
    }
})
```

**OBB 目录路径：**
```kotlin
// SD 卡可用时
/sdcard/Android/obb/

// SD 卡不可用时
/data/Android/obb/
```

### 3. 单 APK 安装流程 (SingleApkXapkInstaller)

#### 3.1 安装流程

```
SingleApkXapkInstaller.install()
    ↓
遍历解压目录中的文件
    ↓
查找 .apk 文件
    ↓
调用 installApp() 安装
```

#### 3.2 安装实现 (AppUtils)

```kotlin
fun installApp(file: File?, context: Context)
```

**版本兼容处理：**

| Android 版本 | URI 处理方式 |
|--------------|--------------|
| < 7.0 (API 24) | `Uri.fromFile(file)` |
| ≥ 7.0 (API 24) | `FileProvider.getUriForFile()` |

**安装 Intent 构建：**
```kotlin
val intent = Intent(Intent.ACTION_VIEW)
intent.setDataAndType(data, "application/vnd.android.package-archive")
intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

// Android 7.0+ 需要授予 URI 权限
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}
```

### 4. 多 APK 安装流程 (MultiApkXapkInstaller)

#### 4.1 安装流程

```
MultiApkXapkInstaller.install()
    ↓
收集所有 .apk 文件路径
    ↓
启动 InstallActivity
    ↓
使用 PackageInstaller 批量安装
```

#### 4.2 InstallActivity 安装流程

**初始化阶段：**
```java
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_install);

    initData();        // 获取 APK 路径列表
    installXapk();     // 开始安装
}
```

**ROM 兼容性检查：**
```java
// 检查是否为魅族或 VIVO 系统
if (RomUtils.isMeizu() || RomUtils.isVivo()) {
    Toast.makeText(this, "魅族或VIVO系统用户如遇安装被中止或者安装失败的情况...",
            Toast.LENGTH_SHORT).show();
    finish();
}
```

**安装执行：**
```
创建安装会话 (PackageInstaller.Session)
    ↓
遍历所有 APK 文件
    ↓
将每个 APK 写入会话
    ↓
提交会话 (commit)
    ↓
等待安装结果回调
```

#### 4.3 PackageInstaller API 使用

**创建会话：**
```java
PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
    PackageInstaller.SessionParams.MODE_FULL_INSTALL
);
int sessionId = packageInstaller.createSession(params);
session = packageInstaller.openSession(sessionId);
```

**写入 APK：**
```java
try (OutputStream packageInSession = session.openWrite(fileName, 0, file.length());
     InputStream is = new BufferedInputStream(new FileInputStream(filePath))) {
    byte[] buffer = new byte[16384];
    int n;
    while ((n = is.read(buffer)) >= 0) {
        packageInSession.write(buffer, 0, n);
    }
}
```

**提交会话：**
```java
Intent intent = new Intent(this, InstallActivity.class);
intent.setAction(PACKAGE_INSTALLED_ACTION);

// Android 12+ 需要指定 PendingIntent 的 mutability
int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ? PendingIntent.FLAG_IMMUTABLE
    : 0;
PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

session.commit(pendingIntent.getIntentSender());
```

#### 4.4 安装结果处理

| 状态码 | 说明 | 处理方式 |
|--------|------|----------|
| `STATUS_PENDING_USER_ACTION` | 需要用户确认 | 启动确认 Intent |
| `STATUS_SUCCESS` | 安装成功 | 显示成功提示并关闭 |
| `STATUS_FAILURE` | 安装失败 | 显示失败提示并关闭 |
| `STATUS_FAILURE_ABORTED` | 安装中止 | 显示失败提示并关闭 |
| `STATUS_FAILURE_BLOCKED` | 安装被阻止 | 显示失败提示并关闭 |
| `STATUS_FAILURE_CONFLICT` | 安装冲突 | 显示失败提示并关闭 |
| `STATUS_FAILURE_INCOMPATIBLE` | 不兼容 | 显示失败提示并关闭 |
| `STATUS_FAILURE_INVALID` | 无效 | 显示失败提示并关闭 |
| `STATUS_FAILURE_STORAGE` | 存储空间不足 | 显示失败提示并关闭 |

## 文件格式详解

### APK (Android Package)

**特点：**
- 单个安装包
- 包含应用代码、资源和清单文件
- 可直接通过 Intent.ACTION_VIEW 安装

**安装方式：**
```
SingleApkXapkInstaller → installApp() → 系统安装器
```

### XAPK (Extended APK)

**特点：**
- Google Play 使用的扩展格式
- 包含多个 APK（主包 + 分包）
- 包含 OBB 资源文件
- ZIP 压缩格式

**结构示例：**
```
app.xapk
├── Android/
│   └── obb/
│       └── com.example.app/
│           └── main.123456789.com.example.app.obb
├── base.apk          # 主 APK
├── split_config.armeabi_v7a.apk  # CPU 架构分包
├── split_config.zh.apk            # 语言分包
└── manifest.json     # 清单文件
```

**安装流程：**
```
解压 XAPK
    ↓
提取所有 .apk 文件
    ↓
提取 OBB 资源到 Android/obb/
    ↓
判断 APK 数量
    ├─ 1 个 → SingleApkXapkInstaller
    └─ 多个 → MultiApkXapkInstaller
```

### APKS (Android Package Split)

**特点：**
- Google Play Bundle 下载格式
- 与 XAPK 类似，都是 ZIP 格式
- 包含多个分包 APK

**安装方式：**
```
APKS 文件作为 XAPK 处理
    ↓
解压并提取 APK
    ↓
使用 MultiApkXapkInstaller 安装
```

### AAB (Android App Bundle)

**说明：**
- 开发格式，不能直接安装
- 需要通过 `bundletool` 转换为 APK/APKS
- 本项目不直接支持 AAB 格式

## 关键技术点

### 1. FileProvider (Android 7.0+)

**作用：**
- 解决 Android 7.0+ 的文件访问限制
- 通过 content:// URI 替代 file:// URI

**配置：**
```xml
<provider
    android:name="com.wuliang.lib.UtilsFileProvider"
    android:authorities="${applicationId}.utilcode.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/util_code_provider_paths" />
</provider>
```

### 2. PackageInstaller API (Android 5.0+)

**优势：**
- 支持批量安装多个 APK
- 更好的安装控制
- 可以监听安装进度和结果

**使用场景：**
- 安装 XAPK 包含的多个分包 APK
- 需要精确控制安装流程

### 3. ROM 兼容性处理

**支持的 ROM：**
- 华为 (Huawei)
- 小米 (Xiaomi)
- OPPO
- VIVO
- 三星 (Samsung)
- 魅族 (Meizu)
- 一加 (OnePlus)
- 努比亚 (Nubia)
- 联想 (Lenovo)
- 等其他品牌

**特殊处理：**
```java
// 魅族和 VIVO 系统可能有安装限制
if (RomUtils.isMeizu() || RomUtils.isVivo()) {
    Toast.makeText(this, "魅族或VIVO系统用户如遇安装被中止...",
            Toast.LENGTH_SHORT).show();
}
```

### 4. PendingIntent Mutability (Android 12+)

**要求：**
- Android 12+ 必须指定 PendingIntent 的 mutability 标志
- `FLAG_IMMUTABLE`：不可变 Intent（推荐）
- `FLAG_MUTABLE`：可变 Intent

**实现：**
```java
int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ? PendingIntent.FLAG_IMMUTABLE
    : 0;
PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
```

## 依赖库

| 库名 | 版本 | 用途 |
|------|------|------|
| androidx.appcompat | 1.7.0 | AndroidX 兼容库 |
| org.zeroturnaround:zt-zip | 1.13 | ZIP 文件操作 |

## 系统要求

| 项目 | 要求 |
|------|------|
| minSdk | 21 (Android 5.0) |
| targetSdk | 35 (Android 15) |
| compileSdk | 35 (Android 15) |
| Kotlin | 2.1.0 |
| Gradle | 8.9 |
| AGP | 8.7.3 |

## 已知限制

1. **AAB 格式不支持**：需要先转换为 APK 或 APKS
2. **部分 ROM 兼容性问题**：魅族、VIVO 等系统可能有安装限制
3. **Android 5.0 以下不支持**：需要 Android 5.0 及以上版本
4. **需要用户授权**：Android 8.0+ 需要用户手动授权安装权限

## 使用示例

### 安装单个 APK

```kotlin
val xapkInstaller = createXapkInstaller("/sdcard/Download/app.apk")
xapkInstaller?.installXapk(this)
```

### 安装 XAPK 包

```kotlin
val xapkInstaller = createXapkInstaller("/sdcard/Download/game.xapk")
xapkInstaller?.installXapk(this)
```

### 在 MainActivity 中使用

```kotlin
private fun doInstall(xapkFilePath: String) {
    val xapkInstaller = createXapkInstaller(xapkFilePath)

    if (xapkInstaller == null) {
        Toast.makeText(this, "安装xapk失败！", Toast.LENGTH_SHORT).show()
    } else {
        val installExecutor = Executors.newSingleThreadExecutor()
        installExecutor.execute {
            xapkInstaller.installXapk(this@MainActivity)
        }
    }
}
```

## 总结

XAPK Installer 提供了一个完整的 Android 应用安装解决方案，支持多种安装包格式。通过智能识别文件类型和 APK 数量，自动选择合适的安装策略，确保在不同 Android 版本和 ROM 上都能正常工作。

核心优势：
- ✅ 支持多种安装包格式
- ✅ 自动处理 OBB 资源
- ✅ 兼容 Android 5.0 - 15
- ✅ 处理多 APK 分包安装
- ✅ ROM 兼容性优化
