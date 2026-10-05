package com.example.adbtoolbox.common

enum class ADBDestination {
    Home,
    Apps,
    Terminal,
    Settings,
    DeviceInfo,
    ADBPanel,
    Permissions,
    RootManager,
    ShellExecutor,
    AppDetail,
    ADBModule,
    Plugins,
    PluginDetail,
    GlassPlayground,
    RootTool,
    TempRoot,
    /** v2.8 品牌自适应一键性能加速 */
    PerformanceBoost,
    /** v2.8 手机体检（指令可用性检查员） */
    PhoneInspector,
    /** v2.8 华为深度优化（HarmonyOS / EMUI 专属） */
    HuaweiBoost,
    /** v2.8 已安装 Root 模块管理（Magisk / KernelSU：列表、启停、卸载、执行 action.sh） */
    RootModules,
    /** v2.8 机型分类优化（每个品牌独立分类入口，不与其它品牌混在一起） */
    BrandPerf,
    /** v2.8 游戏帧率（全机型：非华为用应用权限直写，华为/荣耀走 ADB 或 Root） */
    GameFrameRate
}
