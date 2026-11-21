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

## 五、当前存在的问题

### 5.1 已知问题

#### 问题 1: 设置页面黑屏
**现象**: 打开设置页面后，大部分区域变黑，但不会崩溃

**可能原因**:
1. E-Ink 屏幕刷新机制与 Activity 生命周期冲突
2. 布局渲染问题（FrameLayout 背景透明）
3. 视图树初始化时序问题

**待解决方向**:
- 检查 `activity_setting.xml` 布局文件的可见性
- 考虑使用 Fragment 替代 Activity
- 添加延迟初始化逻辑

---

#### 问题 2: 点击无响应
**现象**: 设置项点击后没有视觉反馈

**可能原因**:
1. E-Ink 屏幕刷新延迟导致用户感知不到点击
2. Focus 管理问题（D-pad 焦点 vs 触摸焦点）
3. 事件分发被拦截

**待解决方向**:
- 添加点击反馈动画（即使 E-Ink 设备也需要视觉提示）
- 检查事件分发链
- 考虑禁用 focus 管理，改用直接点击

---

#### 问题 3: 自定义图标功能
**现象**: 点击"显示自定义图标"选项无反应

**原因**: 权限检查或图标缓存刷新逻辑可能有问题

**待解决方向**:
- 检查 `Utils.checkStoragePermission()` 实现
- 验证图标缓存刷新逻辑

---

### 5.2 架构问题

#### 问题 4: Activity vs Fragment
**现状**: 将 SettingFragment 转换为 SettingActivity

**问题**:
- Activity 切换有生命周期开销
- 无法直接访问宿主 Activity 的私有成员
- 通信需要通过广播或接口

**建议**:
- 考虑保留 Fragment 架构
- 或使用 ViewModel + LiveData 进行通信

---

#### 问题 5: 广播通信开销
**现状**: 使用广播通知设置变更

**问题**:
- 每次设置变更都会发送广播
- 广播接收器在主线程执行可能阻塞 UI

**建议**:
- 使用 Handler 延迟执行
- 或考虑使用 LocalBroadcastManager

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
| - | 2026-11-21 | 修复编译错误 |
| - | 2026-11-21 | 修复触摸模式问题 |
| - | 2026-11-21 | 添加广播通信机制 |
| - | 2026-11-21 | 添加 listener null check |

---

**文档生成时间**: 2026-11-21  
**最后更新**: 2026-11-21
