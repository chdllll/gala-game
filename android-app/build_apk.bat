@echo off
chcp 65001 >nul
echo ============================================================
echo Gala Game Android APK 打包脚本
echo ============================================================
echo.

setlocal

set JAVA_HOME=C:\Program Files\Java\jdk-17
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

echo [1/4] 检查Java环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ Java未安装或JAVA_HOME未正确设置
    echo 请安装JDK 17并设置JAVA_HOME环境变量
    pause
    exit /b 1
)
echo ✅ Java环境正常

echo.
echo [2/4] 检查Android SDK...
if not exist "%ANDROID_HOME%" (
    echo ❌ Android SDK未找到
    echo 请安装Android Studio或Android SDK
    pause
    exit /b 1
)
echo ✅ Android SDK正常

echo.
echo [3/4] 检查签名密钥...
if not exist "app\release.keystore" (
    echo 正在创建签名密钥...
    keytool -genkeypair -v -keystore app\release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000 -storepass gala2024 -keypass gala2024 -dname "CN=Gala Game, OU=Development, O=Gala, L=Beijing, ST=Beijing, C=CN"
)
echo ✅ 签名密钥就绪

echo.
echo [4/4] 开始构建APK...
call gradlew.bat assembleRelease

if errorlevel 1 (
    echo ❌ 构建失败
    pause
    exit /b 1
)

echo.
echo ============================================================
echo ✅ 构建成功！
echo ============================================================
echo.
echo APK文件位置:
echo   %CD%\app\build\outputs\apk\release\app-release.apk
echo.
echo 请将此APK文件传输到手机安装
echo.
pause
