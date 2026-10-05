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

## v2.8 新增

### 主题（设置 → 主题）
- **经典**：程序原有配色。
- **国庆**：中国红 `#C8102E` + 金属金 `#D4AF37`，深红玻璃、金色长按辉光、圆角收紧。
  国庆档期（10 月 1—7 日）全新安装会自动套用；首页会显示一条按国旗标准比例绘制的五星红旗横幅。
- **华为**：华为红 `#CF0A2C` + 石墨黑 `#3A3A3C`。

主题不是换一层贴图，它直接写入全局玻璃配置：玻璃叠加色、长按辉光颜色/强度/光晕、边缘折射量、
卡片圆角、全局渲染强度、导航胶囊指示器颜色，并统一各页面的强调色。

**持久化规则（重要）**：选了主题且**没有**在「液态玻璃调节」里手动改过玻璃参数时，
每次启动都以该主题为准（不会出现"横幅还在、配色却回退"）；一旦手动改过（并点了保存），
就以你的调整为准备，主题不再强行覆盖。两个标记都会落盘：`app_theme_chosen`、`app_theme_user_overrode`。

### 性能加速：按机型独立分类
- 新增**机型分类入口**：小米/红米、vivo/iQOO、OPPO/realme/一加、华为/荣耀、三星、魅族、
  以及全机型通用，各自独立，可单独使用某一机型的优化项。
- 条目按提权通道分成两组、互不混淆：**Root 专属** 与 **ADB / Shizuku 可用**。
- 新增 20 条全机型通用项，并接入原有的一键加速与体检。
- 自动识别机型并给出适用性判断（品牌 / SoC 厂商 / SDK 版本 / 提权通道四项），
  界面显示真实读数摘要，并逐条列出"因缺少提权而暂时跑不了"的项。

### 手机体检 / 华为深度优化
- 手机体检：逐条验证指令在本机是否真的能生效，输出 可用 / 需提权 / 不适用 与综合得分。
- 华为深度优化：30 条 HarmonyOS / EMUI 方法，含原理、命令、验证、恢复与风险等级，
  高风险项默认不勾选并需二次确认。详见 `docs/huawei_methods.md`。

### 游戏帧率（全机型）
把显示刷新率固定到指定档位，游戏与所有应用都按该帧率运行。两条写入通道按机型自动选择：

- **非华为 / 荣耀**：`peak_refresh_rate` / `min_refresh_rate` 位于 system 命名空间，应用声明并拿到
  「修改系统设置」（`WRITE_SETTINGS`）权限后即可**直接写，不需要 ADB**。
- **华为 / 荣耀**：厂商把刷新率键挪到了应用写不到的位置，必须走 **ADB（Shizuku）或 Root**；
  界面会明确告诉你当前走哪条通道、以及是否已具备条件。

写入后真实回读当前刷新率并如实显示（部分机型需熄屏再亮或切后台才切换）。
AOSP 未开放按单个应用改刷新率的接口，因此**不提供"只改某个游戏"的假开关**。

### 插件与模块
- ADB 插件：安装 / 启停 / 卸载 / 执行 `action.sh`，并**可直接打开模块自带的 WebUI**
  （独立整屏宿主，带关闭按钮与返回键拦截）。
- Root 模块：Magisk / KernelSU 模块的列表、启停、卸载、执行 `action.sh`，同样可打开自带 UI。
- **只有模块真的带 UI 时才显示「打开界面」按钮**（真实扫描 webroot / webui 入口文件）；
  没有 UI 就完全不显示该按钮，界面文件已不存在时给出具体原因，而不是点了报错。
- 安装失败会区分并如实回报原因（压缩包结构不对 / 缺安装脚本 / 无 Root / 目录只读 / zip 越界路径等），
  不再是一句笼统的"安装失败"。

### 动效
所有时长与曲线统一收敛到 `theme/AppMotion.kt`：按下 / 回弹 / 光斑跟随用弹簧，
进场 / 退场分别用 decelerate / accelerate 曲线，页面切换为缩放 + 淡入。
各处不再各写一套魔法时长，观感因此一致。

## 文档索引

- `docs/v2.8-国庆主题与华为性能-使用说明.md`：主题、华为优化、以及各轮修复的**根因**与验证状态
- `docs/huawei_methods.md`：30 条华为专属方法逐条说明（命令 / 验证 / 恢复 / 权限 / 风险 / 适用 ROM）

## 已知限制

- 本仓库的验证是「编译 + 静态审计」，**未在真机上验证过全部 shell 行为**。
  厂商 ROM 上部分 `settings` / `cmd` / `sysfs` 接口是否存在因版本而异，
  应用为此提供了「验证本机可用性」与命令执行后的状态回读，请以真机结果为准。
- 非 Android 目标（desktop / iOS / js / wasm）不完整：部分 `expect` 缺少对应 `actual`，
  目前只保证 **Android** 目标可构建。
- 若工程路径包含非 ASCII 字符（如中文目录），AGP 会拒绝构建；
  `gradle.properties` 已加 `android.overridePathCheck=true` 以允许这种路径。

## 免责声明

本项目是系统调试工具。执行系统级命令（修改系统设置、禁用系统组件、清理缓存与数据、
调整内核参数、刷入模块等）可能导致数据丢失、系统异常或设备无法启动。
请自行确认每条命令的含义并做好备份，风险自负。

## 许可证

MIT License
