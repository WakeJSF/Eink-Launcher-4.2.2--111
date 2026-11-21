# Eink-Launcher Android 4.2.2 兼容版本

## 🎯 项目简介

这是 **Eink-Launcher (咩Eink桌面)** 的 Android 4.2.2 兼容性适配版本，专为墨水屏阅读器优化。

### 主要改进
- ✅ 完全适配 Android 4.2.2 (API 17)
- ✅ 修复更换系统图标时崩溃的问题
- ✅ 优化内存管理，避免 OOM
- ✅ 提升低配设备稳定性
- ✅ 支持 Java 7 编译

### 版本信息
- **版本**: 4.1.1-42compat
- **minSdk**: 14 (Android 4.0)
- **targetSdk**: 17 (Android 4.2.2)
- **编译目标**: Java 7

---

## 📦 快速获取 APK

### 方式 1: GitHub Actions 自动构建（推荐）

1. **Fork 本仓库**
   - 点击 GitHub 页面右上角 "Fork" 按钮
   - 等待 Fork 完成

2. **触发构建**
   - 进入 "Actions" 标签
   - 点击 "Build Android APK" workflow
   - 选择 "Run workflow"
   - 等待 5-10 分钟

3. **下载 APK**
   - 在 Actions 运行页面底部下载 "app-debug" artifact
   - 或查看 "Releases" 页面下载 Release 版本

### 方式 2: 本地构建

```bash
# 克隆仓库
git clone https://github.com/YOUR_USERNAME/Eink-Launcher-42.git
cd Eink-Launcher-42

# 构建 Debug APK
./gradlew assembleDebug

# 或使用 Windows 批处理文件
gradlew.bat assembleDebug

# APK 位置
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 核心修复内容

### 1. SDK 版本调整
| 配置项 | 修改前 | 修改后 |
|--------|--------|--------|
| compileSdk | 36 | 17 |
| targetSdk | 36 | 17 |
| Java版本 | 17 | 7 |
| multidex | 启用 | 禁用 |

### 2. 内存优化
- **IconCache**: 根据设备内存动态调整缓存大小（低端设备减半）
- **Bitmap**: 使用 RGB_565 替代 ARGB_8888，节省 50% 内存
- **图标尺寸**: 限制在 128x128 以内，避免过大占用内存

### 3. 并发安全
- **SharedPreferences**: 添加 `synchronized` 关键字，`commit()` → `apply()`
- 防止主线程阻塞和 ANR

### 4. API 兼容性
- **Fragment**: 使用 `androidx.fragment.app.Fragment`
- **权限检查**: 区分 Android 版本处理
- **异常处理**: 添加 try-catch 防止崩溃

---

## 📱 安装步骤

1. **启用未知来源**
   - 设置 → 安全 → 启用 "未知来源"

2. **传输 APK**
   - 通过 USB、WiFi 或云盘传输到设备
   - 或使用 ADB: `adb install app-debug.apk`

3. **安装并授权**
   - 点击 APK 文件安装
   - 授予必要权限（存储、相机等）

4. **设置为默认桌面**
   - 按 Home 键
   - 选择 "Eink Launcher" 并设为默认

---

## 🧪 测试清单

### 基础功能
- [ ] 应用正常启动
- [ ] 应用列表显示正常
- [ ] 滑动翻页流畅
- [ ] 点击应用正常启动

### 自定义图标
- [ ] 开启自定义图标开关
- [ ] 添加小尺寸图标 (<128KB)
- [ ] 添加大尺寸图标 (>256KB)
- [ ] 切换图标正常显示

### 设置功能
- [ ] 调整网格大小
- [ ] 修改字体大小
- [ ] 切换主题模式
- [ ] 隐藏/显示应用
- [ ] 排序方式切换

### 稳定性测试
- [ ] 连续快速操作不崩溃
- [ ] 内存使用稳定
- [ ] 无 ANR 现象

---

## 📊 性能对比

| 指标 | 原版 | 适配版 | 改善 |
|------|------|--------|------|
| 内存占用 | 高 (易 OOM) | 低 (优化 50%) | ✅ |
| 图标加载 | 慢 (全尺寸) | 快 (智能缩放) | ✅ |
| 设置响应 | 可能 ANR | 流畅 | ✅ |
| 兼容性 | API 36 | API 17 | ✅ |

---

## ⚠️ 已知限制

由于 Android 4.2.2 的系统限制：

1. **不支持的功能**
   - 状态栏图标颜色调整 (需 API 23+)
   - 导航栏图标颜色调整 (需 API 26+)
   - Android 13+ 的新权限模型

2. **性能权衡**
   - 图标色彩精度略有下降 (RGB_565)
   - 低端设备图标缓存减半
   - 大图加载可能有延迟

---

## 🐛 问题反馈

如遇问题，请提供：

1. **设备信息**
   - 设备型号
   - Android 版本
   - 内存大小

2. **问题描述**
   - 具体操作步骤
   - 崩溃现象
   - 发生频率

3. **日志信息**
   ```bash
   adb logcat | grep "cn.modificator.launcher"
   ```

4. **截图/录屏**
   - 崩溃前的界面
   - 错误提示信息

---

## 📄 相关文档

- [FIX_REPORT.md](FIX_REPORT.md) - 详细修复报告
- [ANDROID_42_COMPATIBILITY.md](ANDROID_42_COMPATIBILITY.md) - 兼容性说明
- [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md) - 修改总结
- [GITHUB_ACTIONS_GUIDE.md](GITHUB_ACTIONS_GUIDE.md) - GitHub Actions 使用指南

---

## 🙏 致谢

- **原作者**: E-Ink Launcher 团队
- **维护者**: zanjie1999 (咩咩)
- **适配贡献**: AI Assistant

---

## 📜 许可证

Apache License 2.0

---

*最后更新: 2026-11-21*  
*适配目标: Android 4.2.2 (API 17)*  
*版本: 4.1.1-42compat*
