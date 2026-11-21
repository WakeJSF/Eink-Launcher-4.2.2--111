# GitHub Actions 自动构建指南

## 📋 项目信息

- **源仓库**: https://github.com/zanjie1999/Eink-Launcher
- **适配版本**: 4.1.1-42compat
- **目标系统**: Android 4.2.2 (API 17)
- **最低支持**: Android 4.0 (API 14)

---

## 🚀 快速开始

### 方法 1: Fork 原仓库（推荐）

1. **Fork 原仓库**
   - 访问: https://github.com/zanjie1999/Eink-Launcher
   - 点击右上角 "Fork" 按钮
   - 等待 Fork 完成

2. **获取代码**
   ```bash
   git clone https://github.com/YOUR_USERNAME/Eink-Launcher.git
   cd Eink-Launcher
   ```

3. **合并修改**
   - 将本目录下的修改文件复制过去
   - 或者手动应用补丁

4. **推送代码**
   ```bash
   git add .
   git commit -m "feat: Android 4.2.2 compatibility fixes"
   git push origin v0.x
   ```

5. **触发构建**
   - GitHub Actions 会自动运行
   - 或者手动触发: Settings → Actions → Workflow → Run workflow

---

### 方法 2: 创建新仓库

1. **在 GitHub 创建新仓库**
   - 访问: https://github.com/new
   - 仓库名: `Eink-Launcher-42`
   - 设为公开或私有
   - 不要初始化 README

2. **本地初始化**
   ```bash
   cd eink-launcher-android42-fix
   git remote add origin https://github.com/YOUR_USERNAME/Eink-Launcher-42.git
   git branch -M main
   git push -u origin main
   ```

---

## 📁 文件结构

```
eink-launcher-android42-fix/
├── .github/
│   └── workflows/
│       └── build.yml          # GitHub Actions 配置
├── app/
│   ├── build.gradle.kts       # 修改后的构建配置
│   └── src/main/java/
│       ├── cn/modificator/launcher/
│       │   ├── Config.java    # 修复并发问题
│       │   ├── Launcher.java  # 添加异常处理
│       │   ├── SettingFragment.java  # Fragment API 兼容
│       │   └── Utils.java     # 权限检查优化
│       ├── model/
│       │   └── IconCache.java # 内存优化
│       └── widgets/
│           └── AppItemBinder.java  # Bitmap 优化
├── ANDROID_42_COMPATIBILITY.md  # 兼容性说明
├── FIX_REPORT.md               # 修复报告
└── CHANGES_SUMMARY.md          # 修改总结
```

---

## ⚙️ GitHub Actions 配置

`.github/workflows/build.yml` 已配置好，主要功能：

### 自动触发条件
- Push 到 `v0.x`, `main`, `master` 分支
- Pull Request 到上述分支
- 手动触发 (workflow_dispatch)

### 构建步骤
1. 检出代码
2. 设置 JDK 17
3. 构建 Debug APK
4. 构建 Release APK
5. 上传 artifact
6. 创建 Release (仅手动触发)

---

## 🔧 手动触发构建

1. 进入仓库页面
2. 点击 "Actions" 标签
3. 选择 "Build Android APK" workflow
4. 点击 "Run workflow"
5. 选择分支（建议选 `v0.x`）
6. 点击 "Run workflow" 按钮

---

## 📦 获取 APK

### 方式 1: Actions 页面
1. 进入 "Actions" → 选择最近的运行
2. 在 "Artifacts" 部分下载 APK
   - `app-debug.apk` - Debug 版本
   - `app-release.apk` - Release 版本（已签名）

### 方式 2: Release 页面
如果触发了 Release 创建：
1. 进入 "Releases"
2. 下载最新版本
3. 包含两个 APK 文件

---

## 🧪 测试建议

### 必测场景
- [ ] 应用启动
- [ ] 开启/关闭自定义图标
- [ ] 添加大尺寸图标 (>256KB)
- [ ] 快速切换设置项
- [ ] 滑动翻页
- [ ] 长按菜单

### 内存监控
```bash
adb shell dumpsys meminfo cn.modificator.launcher
```

### 日志收集
```bash
adb logcat | grep "cn.modificator.launcher"
```

---

## ⚠️ 注意事项

1. **GitHub Actions 免费额度**
   - 每月 2000 分钟（个人账户）
   - 构建时间约 5-10 分钟
   - 正常使用足够

2. **构建失败排查**
   - 检查 Actions 页面的错误日志
   - 确保 gradle  Wrapper 存在
   - 检查 Java 版本兼容性

3. **签名 Release APK**
   - 当前配置未签名
   - 如需签名，需配置 signing config
   - 或手动使用 jarsigner 签名

---

## 📞 问题反馈

如遇问题，请：
1. 查看 Actions 运行日志
2. 检查构建错误信息
3. 提供设备型号和 Android 版本
4. 附上崩溃日志

---

*最后更新: 2026-11-21*
