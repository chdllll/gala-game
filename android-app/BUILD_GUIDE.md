# Gala Game Android APK 打包指南

## 使用 GitHub Actions 自动构建（推荐）

### 步骤 1: 创建 GitHub 仓库

1. 登录 GitHub (https://github.com)
2. 点击右上角 "+" → "New repository"
3. 填写仓库名称，如 `gala-game`
4. 选择 "Private" 或 "Public"
5. 点击 "Create repository"

### 步骤 2: 上传代码到 GitHub

在项目目录打开命令行：

```bash
cd c:\Users\19566\Desktop\ltrj-main

git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/你的用户名/gala-game.git
git push -u origin main
```

### 步骤 3: 触发构建

**方式一：手动触发**
1. 进入 GitHub 仓库页面
2. 点击 "Actions" 标签
3. 选择 "Build Android APK" workflow
4. 点击 "Run workflow" → "Run workflow"

**方式二：推送代码自动触发**
每次推送代码到 main 或 master 分支，会自动触发构建

### 步骤 4: 下载 APK

1. 等待构建完成（约 10-15 分钟）
2. 点击完成的 workflow run
3. 在 "Artifacts" 区域下载 `gala-game-apk.zip`
4. 解压得到 APK 文件

---

## 本地打包（需要配置环境）

### 前置要求

#### 1. 安装 JDK 17

**下载地址**: https://adoptium.net/temurin/releases/?version=17

选择 Windows x64 版本下载并安装。

设置环境变量：
- `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot`
- 在 `PATH` 中添加 `%JAVA_HOME%\bin`

#### 2. 安装 Android SDK

**方式一：安装 Android Studio（推荐）**
- 下载地址: https://developer.android.com/studio

**方式二：仅安装命令行工具**
- 下载地址: https://developer.android.com/studio#command-tools

设置环境变量：
- `ANDROID_HOME` = `C:\Users\你的用户名\AppData\Local\Android\Sdk`

#### 3. 安装 Python 3.10

**下载地址**: https://www.python.org/downloads/release/python-31011/

安装时勾选 "Add Python to PATH"

### 打包步骤

```bash
cd c:\Users\19566\Desktop\ltrj-main\android-app
build_apk.bat
```

或手动执行：

```bash
cd c:\Users\19566\Desktop\ltrj-main\android-app
gradlew.bat assembleRelease
```

### APK 文件位置

```
android-app\app\build\outputs\apk\release\app-release.apk
```

---

## 安装到手机

### 方式一：USB 数据线

1. 连接手机到电脑
2. 复制 APK 到手机存储
3. 在手机上打开 APK 安装

### 方式二：无线传输

1. 通过微信/QQ/邮件发送 APK 到手机
2. 在手机上打开安装

### 方式三：ADB 安装

```bash
adb install app-release.apk
```

---

## 常见问题

### Q: GitHub Actions 构建失败
A: 检查 Actions 页面的错误日志，通常是代码问题或依赖问题

### Q: 手机安装提示"未信任的应用"
A: 在手机设置中启用「允许安装未知来源应用」

### Q: 应用闪退
A: 检查是否授予了必要权限，查看 logcat 日志

---

## 签名信息

- 密钥别名: `release`
- 密钥密码: `gala2024`
- 存储密码: `gala2024`

**重要**: 更新应用时需要使用相同的签名！
