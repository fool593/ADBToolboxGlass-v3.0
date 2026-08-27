# ADBToolboxGlass

一款基于 Kotlin Multiplatform + Compose Multiplatform 开发的 Android ADB 工具箱，采用液态玻璃（Liquid Glass）UI 设计。

## 功能特性

### 核心功能
- **ADB 命令执行** - 支持 Shizuku / Root / 普通 Shell 三级权限执行
- **应用管理** - 查看已安装应用、冻结/解冻、清除缓存、卸载
- **权限管理** - 查看和管理应用权限
- **设备信息** - 查看设备硬件和系统信息
- **ADB 快捷面板** - 截图、重启、清缓存等常用操作
- **终端模拟器** - 交互式 Shell 终端
- **Root 管理器** - Root 权限下的高级操作

### 插件系统
- **ADB 插件** - 支持安装和管理 ADB 模块（zip 格式）
- **Root 模块** - 支持 Magisk/KernelSU 模块管理
- 模块运行、禁用、启用、WebUI 支持

### 高级功能
- **一键 Root** - 支持 Magisk / KernelSU / 临时 Root（GhostLock、TempRoot）
- **Dhizuku 激活** - 设备所有者（Device Owner）激活
- **Shizuku 支持** - 免 Root ADB 权限
- **多语言** - 中文、英文、印地语

### UI 特性
- **液态玻璃效果** - 全局可调节的毛玻璃、折射、光域效果
- **深色/浅色模式** - 自动跟随系统或手动切换
- **自定义壁纸** - 支持设置应用背景壁纸
- **导航栏样式** - 胶囊/圆形滑块切换
- **字体颜色** - 10 种预设颜色可选
- **全局效果调节** - 导航栏、卡片、按钮独立调节

## 技术栈

- **Kotlin Multiplatform** - 跨平台共享代码
- **Compose Multiplatform** - 声明式 UI
- **Backdrop** - 液态玻璃效果库
- **Shizuku** - 免 Root ADB 权限
- **Dhizuku** - 设备所有者权限

## 项目结构

```
ADBToolboxGlass/
├── androidApp/          # Android 应用入口
├── app/                 # 共享代码模块
│   ├── src/commonMain/  # 通用代码（UI、业务逻辑）
│   └── src/androidMain/ # Android 特定实现
├── backdrop/            # 液态玻璃效果库
├── build.gradle.kts     # 根构建配置
└── gradle.properties    # Gradle 属性
```

## 构建方法

### 环境要求
- Android SDK
- JDK 17+
- Gradle 9.5+

### 构建命令

```bash
# 设置 Android SDK 路径
export ANDROID_HOME=/path/to/android/sdk
export ANDROID_SDK_ROOT=/path/to/android/sdk

# 构建 Debug APK
./gradlew :androidApp:assembleDebug

# 构建 Release APK
./gradlew :androidApp:assembleRelease
```

### 输出位置
- Debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

## 权限说明

应用需要以下权限才能正常工作：
- **Shizuku 权限** - 免 Root 执行 ADB 命令
- **Root 权限** - 执行需要 Root 的操作（可选）
- **设备所有者** - Dhizuku 激活（可选）
- **存储权限** - 读取模块文件和壁纸

## 注意事项

1. Shizuku 需要通过 ADB 或 Root 启动服务
2. Dhizuku 激活前需确保设备上没有其他账户（包括双开空间）
3. 临时 Root 功能仅支持特定机型，存在一定风险
4. 冻结系统应用可能导致系统不稳定，请谨慎操作

## 许可证

MIT License
