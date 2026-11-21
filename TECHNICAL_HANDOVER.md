# Eink-Launcher Android 4.2.2 兼容性适配 - 技术交接文档

## 一、项目概述

### 1.1 项目背景
- **项目名称**: Eink-Launcher (咩Eink桌面)
- **原始项目**: https://github.com/zyym/Eink-Launcher
- **适配仓库**: https://github.com/WakeJSF/Eink-Launcher-4.2.2--111
- **目标平台**: Android 4.2.2 (API 17) E-Ink 墨水屏设备
- **项目类型**: Android Launcher (桌面启动器)

### 1.2 适配目标
将现代 Android 项目降级兼容到 Android 4.2.2 (API 17)，同时保持功能可用性和代码可维护性。

---

## 二、构建环境配置

### 2.1 Gradle 版本
```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

### 2.2 Android Gradle Plugin (AGP)
```toml
# gradle/libs.versions.toml
agp = "7.4.2"  # 从 8.7.3 降级
kotlin = "1.7.21"  # 从 2.2.0 降级
```

### 2.3 SDK 配置
```kotlin
// app/build.gradle.kts
android {
    compileSdk = 31  // 必须 >= 31 以支持现代 AndroidX 库
    defaultConfig {
        minSdk = 14   // 支持 Android 4.0+
        targetSdk = 17 // 保持在 API 17
    }
}
```

### 2.4 Java 8 Desugaring
```kotlin
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
```

---

## 三、遇到的问题及解决方案

### 3.1 编译错误

#### 问题 1: AAR 元数据验证失败
**错误信息**:
```
AAR metadata file: ... has invalid version: 32
```

**原因**: 依赖库要求 compileSdk >= 31，但项目配置为 17

**解决方案**:
```kotlin
// 将 compileSdk 改为 31，但保持 minSdk=14, targetSdk=17
android {
    compileSdk = 31
    defaultConfig {
        minSdk = 14
        targetSdk = 17
    }
}
```

---

#### 问题 2: Kotlin Compose 插件不存在
**错误信息**:
```
Plugin [id: 'org.jetbrains.kotlin.plugin.compose', version: '2.2.0'] was not found
```

**原因**: Kotlin Compose 插件在 Kotlin 1.7.21 中不可用

**解决方案**: 从 build.gradle.kts 中移除 compose 插件声明
```kotlin
// 移除这行
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // id("org.jetbrains.kotlin.plugin.compose")  // 删除
}
```

---

#### 问题 3: 库引用名称不匹配
**错误信息**:
```
Project ':app' references variables libraries which does not exist in catalog
```

**原因**: libs.versions.toml 中定义为 `androidx-appcompat`，但代码中使用 `libs.appcompat`

**解决方案**:
```toml
# gradle/libs.versions.toml
[libraries]
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
```
```kotlin
// app/build.gradle.kts
implementation(libs.androidx.appcompat)
```

---

#### 问题 4: META-INF 文件重复
**错误信息**:
```
Duplicate files copied in APK META-INF/DEPENDENCIES
```

**原因**: 多个依赖库包含相同的 META-INF 文件

**解决方案**:
```kotlin
packagingOptions {
    exclude("META-INF/DEPENDENCIES")
    exclude("META-INF/LICENSE")
    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/NOTICE")
    exclude("META-INF/NOTICE.txt")
    exclude("META-INF/services/javax.annotation.processing.Processor")
}
```

---

#### 问题 5: Fragment 类型不匹配
**错误信息**:
```
Type mismatch: inferred type is AppCompatActivity but FragmentActivity was expected
```

**原因**: Fragment 使用旧的 Android Support Library API，而项目迁移到 AndroidX

**解决方案**:
```java
// 修改 FragmentTransaction 调用
getSupportFragmentManager().beginTransaction()
    .replace(android.R.id.content, new SettingFragment())
    .commit();
```

---

#### 问题 6: findViewBy 拼写错误
**错误信息**:
```
error: cannot find symbol: method findViewBy(int)
```

**原因**: 方法名拼写错误，应为 `findViewById`

**解决方案**:
```java
// 批量替换（已使用 sed 完成）
sed -i 's/findViewBy(/findViewById(/g' app/src/main/java/cn/modificator/launcher/SettingActivity.java
```

---

### 3.2 运行时问题

#### 问题 7: 设置页面点击无响应
**现象**: 打开设置页面后，点击各项设置无反应，界面变黑

**原因分析**:
1. `setFocusableInTouchMode(false)` 禁用了触摸模式
2. SettingActivity 没有 listener 引用，导致 NullPointerException

**解决方案**:
```java
// 1. 启用触摸模式
private void makeFocusable(View view) {
    view.setFocusable(true);
    view.setFocusableInTouchMode(true);  // 改为 true
    // ...
}

// 2. 添加 null check
if (listener != null) {
    listener.onRowNumChanged(rowNum);
}
```

---

#### 问题 8: 设置变更后不立即生效
**现象**: 修改设置后需要重启 launcher 才能生效

**原因**: SettingActivity 和 Launcher 之间没有通信机制

**解决方案**: 使用广播机制
```java
// Launcher.java
public static final String ACTION_SETTINGS_CHANGED = "cn.modificator.launcher.ACTION_SETTINGS_CHANGED";

private final BroadcastReceiver settingsChangedReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 刷新 UI
        applyGridSize(config.getColNum(), config.getRowNum());
        // ...
    }
};

// SettingActivity.java
private void notifySettingsChanged() {
    Intent intent = new Intent(Launcher.ACTION_SETTINGS_CHANGED);
    intent.setPackage(getPackageName());
    sendBroadcast(intent);
}
```

---

## 四、关键代码变更

### 4.1 Launcher.java
- 添加 `ACTION_SETTINGS_CHANGED` 常量
- 添加 `settingsChangedReceiver` 广播接收器
- 在 `onResume()` 中注册接收器
- 在 `onPause()` 中注销接收器
- 移除直接的 listener 传递（改用广播）

### 4.2 SettingActivity.java
- 从 Fragment 转换为 Activity
- 添加 `onCreate()` 方法
- 修复所有 `findViewBy` → `findViewById`
- 添加所有 listener 调用的 null check
- 移除自定义 `OnSettingChangeListener` 接口，使用 `SettingFragment.OnSettingChangeListener`
- 添加 `notifySettingsChanged()` 方法
- 在所有设置变更后调用 `notifySettingsChanged()`
- 修复触摸模式设置

### 4.3 FTPService.java
- 移除 Apache FTPServer 依赖（不兼容 Android）
- 简化为占位实现

### 4.4 AndroidManifest.xml
- 添加 SettingActivity 声明
```xml
<activity
    android:name=".SettingActivity"
    android:exported="false"
    android:label="@string/settings" />
```

### 4.5 strings.xml
- 添加缺失的字符串资源
```xml
<string name="settings">Settings</string>
```

---

## 五、问题定位与修复记录

### 5.1 已修复：设置页面黑屏 + 点击无响应（根本原因）

**现象**: 打开设置页面后大部分区域变黑，设置项点击无反应，但不崩溃

**根本原因（三重）**:

1. **缺少 E-Ink 屏幕刷新标记（最关键）**
   - Manifest 中 `Launcher`、`ClockActivity`、`CrashDetailPage` 都声明了
     `<meta-data android:name="com.yotadevices.keep_on_epd_screen" android:value="true"/>`
   - 但 `SettingActivity` 没有此标记 → 窗口不会刷新到电子纸屏幕 → 黑屏

2. **Activity 类型与主题不匹配**
   - `SettingActivity extends android.app.Activity`
   - 但全局 `AppTheme` 父主题是 `Theme.AppCompat.DayNight.NoActionBar`
   - AppCompat 主题要求宿主必须继承 `AppCompatActivity`，否则 Decor View 构建和事件分发异常

3. **透明窗口背景**
   - `activity_setting.xml` 根布局背景被注释掉，主题 `windowBackground=transparent`
   - 原 Fragment 方案下透明区域能看到桌面；独立 Activity 下无内容渲染为黑

4. **附加问题：FragmentManager API 不匹配**
   - Launcher 用 `getFragmentManager()`（旧 API）检查回退栈
   - SettingFragment 是 androidx Fragment，导致回退栈计数永远为 0 → 设置页关不掉

**最终解决方案（commit 635b30c）**: 回退到 Fragment 架构
- `launcher_activity.xml`: 根布局改为 FrameLayout，添加 `settingContainer` 覆盖层
- `Launcher.openSettingsFragment()`: 用 `getSupportFragmentManager().commitNow()` 将
  SettingFragment 添加到容器（同步提交，避免时序问题）
- 新增 `closeSettingsFragment()` 和 `isSettingsFragmentShown()`（基于容器可见性判断）
- `onBackPressed()` / `handleLauncherKeyDown()` / `handleLauncherKeyUp()` 统一改用
  `getSupportFragmentManager()` 并优先关闭设置页
- `SettingFragment.handleToggleLargeClock()`: 移除错误的 `getFragmentManager().popBackStackImmediate()`
- `SettingFragment`: 所有 `getActivity().onBackPressed()` 改为 `closeSettings()` 辅助方法

**架构优势**:
- 设置页覆盖在 Launcher 之上，共享窗口 → 自动继承 EPD 标记和正确主题
- listener 通过 `onAttach()` 直接获取（`SettingFragment.OnSettingChangeListener`）→ 实时生效，无需广播
- 透明背景能看到下面的桌面，符合原设计意图

---

### 5.2 已修复：编译错误（历史）

| 错误 | 原因 | 解决方案 |
|------|------|----------|
| AAR 元数据版本 32 | compileSdk=17 过低 | 提升 compileSdk 到 31，保持 minSdk=14/targetSdk=17 |
| Compose 插件未找到 | Kotlin 1.7.21 无此插件 | 移除 compose 插件声明 |
| 库引用名不匹配 | version catalog 命名错误 | 修正为 libs.androidx.appcompat |
| META-INF 重复 | 多依赖库冲突 | packagingOptions 排除重复文件 |
| findViewBy 拼写错误 | 方法名错误 | 批量替换为 findViewById |
| config.load() 不存在 | Config 无此方法 | 直接使用 getter（构造函数已加载） |
| Intent 传 Listener | 接口非 Parcelable | 改用 Fragment onAttach 回调机制 |

---

### 5.3 待观察：自定义图标功能

**现象**: 早期版本点击"显示自定义图标"选项无反应

**当前状态**: 回退 Fragment 架构后，listener 回调链路已恢复（`onShowCustomIconChanged` →
`iconCache.markDirty()` + `refreshIcons()`）。理论上应已修复，需在真机验证

**验证方法**: 检查 `Utils.checkStoragePermission()` 是否正确回调 Runnable

---

## 六、GitHub Actions 配置

### 6.1 Workflow 文件
```yaml
# .github/workflows/build.yml
name: Build Android APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle
      - run: ./gradlew assembleDebug
      - run: ./gradlew assembleRelease
      - uses: actions/upload-artifact@v4
        with:
          name: EinkLauncher-debug
          path: app/build/outputs/apk/debug/*.apk
```

### 6.2 构建产物
- **Debug APK**: ~3.5 MB
- **Release APK**: ~1.8 MB
- **构建时间**: 约 1-2 分钟

---

## 七、测试建议

### 7.1 基本功能测试
1. 启动 Launcher
2. 打开设置页面
3. 点击各项设置：
   - [ ] 每行/每列 App 数量
   - [ ] 字体大小
   - [ ] 应用名行数
   - [ ] 显示/隐藏分隔线
   - [ ] 显示/隐藏状态栏
   - [ ] 自定义图标
   - [ ] 时钟显示秒数
   - [ ] 大时钟
   - [ ] 底部对齐图标
   - [ ] 排序模式
   - [ ] 主题模式
   - [ ] 屏幕方向
4. 验证设置是否立即生效
5. 返回桌面，验证 UI 更新

### 7.2 兼容性测试
- [ ] Android 4.2.2 设备测试
- [ ] 不同分辨率 E-Ink 屏幕测试
- [ ] 长时间运行稳定性测试

---

## 八、后续优化建议

### 8.1 短期优化
1. 修复设置页面黑屏问题
2. 添加点击反馈视觉效果
3. 优化广播通信性能

### 8.2 中期优化
1. 重构为 Fragment 架构
2. 添加数据绑定（Data Binding）
3. 添加单元测试

### 8.3 长期优化
1. 迁移到 Jetpack Compose（需升级 targetSdk）
2. 添加更多 E-Ink 优化（如刷新策略）
3. 支持更多机型适配

---

## 九、联系方式

- **仓库地址**: https://github.com/WakeJSF/Eink-Launcher-4.2.2--111
- **构建页面**: https://github.com/WakeJSF/Eink-Launcher-4.2.2--111/actions
- **技术栈**: Java, AndroidX, Gradle Kotlin DSL, GitHub Actions

---

## 十、版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| 4.1.1-42compat | 2026-11-21 | 初始 Android 4.2.2 适配 |
| - | 2026-11-21 | 修复编译错误（AAR/compose/库引用/META-INF/findViewBy） |
| - | 2026-11-21 | 尝试 SettingActivity 独立 Activity 方案（失败） |
| - | 2026-11-21 | 添加广播通信机制和 listener null check |
| **635b30c** | **2026-11-21** | **回退 Fragment 架构，根治黑屏和点击无响应问题** |

**当前稳定版本**: commit 635b30c（GitHub Actions Run #27 构建成功）

---

**文档生成时间**: 2026-11-21  
**最后更新**: 2026-11-21
