# Android 4.2.2 兼容性适配说明

## 概述
本文件记录了为 Eink-Launcher 项目进行的 Android 4.2.2 (API 17) 兼容性适配修改。

## 修改清单

### 1. build.gradle.kts
**修改内容**:
- `compileSdk`: 36 → 17
- `targetSdk`: 36 → 17
- `versionCode`: 51 → 52
- `versionName`: "4.1" → "4.1.1-42compat"
- 移除 `multiDexEnabled` (Android 4.2.2 不需要)
- 移除 `coreLibraryDesugaring` (Android 4.2.2 不支持)
- 修改 `compileOptions`: Java 17 → Java 7
- 禁用 `isShrinkResources` (避免兼容性问题)
- 降级依赖版本:
  - `androidx.core:core`: 1.12.0 → 1.9.0
  - `androidx.appcompat:appcompat`: 1.6.1 → 1.4.2
  - 移除 `androidx.multidex:multidex`

### 2. Config.java
**修改内容**:
- `addHideApp()`: 添加 `synchronized` 关键字，`commit()` → `apply()`
- `removeHideApp()`: 添加 `synchronized` 关键字，`commit()` → `apply()`
- `setHideApps()`: 添加 `synchronized` 关键字，`commit()` → `apply()`
- `ensureHideAppsLoaded()`: 添加 null 检查，避免 NPE

**原因**: 
- `commit()` 是同步操作，在主线程调用会导致 ANR
- 多线程并发写入 SharedPreferences 可能导致数据损坏
- Android 4.2.2 的 SharedPreferences 实现在有已知 bug

### 3. Utils.java
**修改内容**:
- 添加版本注释说明
- 完善 `checkStoragePermission()` 逻辑

**原因**:
- Android 4.2.2 安装时自动授予所有权限，不需要动态申请
- 代码逻辑需要明确区分不同 Android 版本的权限模型

### 4. IconCache.java
**修改内容**:
- 构造函数添加 `Context` 参数
- 添加 `calculateCacheSize()` 方法根据设备内存动态调整缓存大小
- 低端设备 (内存 ≤ 16MB) 缓存大小从 48 降至 24
- `trimMemory()`: 简化逻辑，统一处理

**原因**:
- Android 4.2.2 设备通常为低端设备，内存限制严格
- 固定 48 个图标的缓存在低内存设备上容易导致 OOM

### 5. AppItemBinder.java
**修改内容**:
- `setCustomIcon()`: 
  - 降低默认 target 尺寸: 256 → 128
  - 添加采样率上限: `if (sample >= 16) break`
  - Bitmap 配置: `ARGB_8888` → `RGB_565` (节省 50% 内存)
  - 添加 `inDither = true` 改善显示效果
  - 添加 OOM 异常捕获和降级处理
- `shutdown()`: 
  - `shutdownNow()` → 优雅关闭
  - 添加 `awaitTermination()` 等待最多 5 秒
  - 添加 InterruptedException 处理

**原因**:
- ARGB_8888 配置下大尺寸自定义图标会占用大量内存
- Android 4.2.2 的 Bitmap 内存管理不如新版本完善
- `shutdownNow()` 可能中断正在进行的图标加载

### 6. Launcher.java
**修改内容**:
- `applySystemBarIconAppearance()`: 添加注释说明 Android 4.2.2 不支持
- `applyLauncherBackground()`: 添加 try-catch 异常处理

**原因**:
- Android 4.2.2 不支持状态栏图标颜色调整 (API 23+)
- 防止因 View 未找到或资源问题导致崩溃

### 7. SettingFragment.java
**修改内容**:
- import: `android.app.Fragment` → `androidx.fragment.app.Fragment`
- 添加 `import androidx.fragment.app.FragmentManager`
- `onAttach()` 方法: 添加新的 API 实现 (接受 Context 参数)
- 保留旧的 `onAttach(Activity)` 方法用于兼容性

**原因**:
- 旧版 `android.app.Fragment` 在现代 AndroidX 环境中不兼容
- 需要同时支持新旧 API 以确保兼容性

## 测试建议

### 基础功能测试
1. 启动应用
2. 切换自定义图标开关
3. 添加/删除隐藏应用
4. 调整网格大小 (列数/行数)
5. 切换主题模式
6. 调整字体大小
7. 进入设置页面

### 压力测试
1. 安装 100+ 个应用
2. 放置大尺寸自定义图标 (>512KB)
3. 快速滑动翻页
4. 连续切换多个设置项
5. 同时开启 FTP 服务和自定义图标

### 内存监控
```bash
# 监控内存使用
adb shell dumpsys meminfo cn.modificator.launcher

# 查看日志
adb logcat | grep "cn.modificator.launcher"

# 检查 ANR
adb shell cat /data/anr/traces.txt
```

## 已知限制

1. **不支持的功能**:
   - 状态栏图标颜色调整 (Android 4.2.2 不支持)
   - 导航栏图标颜色调整 (Android 4.2.2 不支持)
   - Android 13+ 的 RECEIVER_EXPORTED 广播注册

2. **性能考虑**:
   - 自定义图标尺寸限制在 128x128 以内
   - 使用 RGB_565 格式，色彩精度略有下降
   - 图标缓存大小根据设备内存动态调整

## 版本信息
- 原版本: 4.1
- 适配版本: 4.1.1-42compat
- 编译目标: Android 4.2.2 (API 17)
- 最低支持: Android 4.0 (API 14)

## 构建说明

### 使用 Android Studio
1. 打开项目
2. Sync Gradle 文件
3. Build → Clean Project
4. Build → Rebuild Project
5. Build → Build Bundle(s) / APK(s) → Build APK(s)

### 使用命令行
```bash
cd eink-launcher-fix
./gradlew assembleDebug
# 或使用 Windows 批处理文件
gradlew.bat assembleDebug
```

### 输出位置
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## 联系信息
如有问题，请查看 Logcat 日志，筛选标签 `cn.modificator.launcher`

---
*适配完成时间: 2026-11-21*
*适配目标: Android 4.2.2 (API 17)*
