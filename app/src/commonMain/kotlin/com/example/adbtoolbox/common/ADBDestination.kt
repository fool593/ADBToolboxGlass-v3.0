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
    /** v2.9 品牌自适应一键性能加速 */
    PerformanceBoost,
    /** v2.9 手机体检（指令可用性检查员） */
    PhoneInspector,
    /** v2.9 华为深度优化（HarmonyOS / EMUI 专属） */
    HuaweiBoost,
    /** v2.9 已安装 Root 模块管理（Magisk / KernelSU：列表、启停、卸载、执行 action.sh） */
    RootModules,
    /** v2.9 机型分类优化（每个品牌独立分类入口，不与其它品牌混在一起） */
    BrandPerf,
    /** v2.9 游戏帧率（全机型：非华为用应用权限直写，华为/荣耀走 ADB 或 Root） */
    GameFrameRate,
    /**
     * v2.9 首次启动的设置向导（欢迎 → 主题 → 权限 → 完成）。
     * 只在首次启动（[com.example.adbtoolbox.common.AppSettings.onboardingDone] 为 false）时作为初始页面。
     */
    Onboarding,
    /**
     * v2.9 还原所有系统默认设置。
     * 性能优化里有整机级改动（后台策略、Doze、被禁用的厂商组件、刷新率、动画缩放），
     * 这个页面提供一条明确、可验证的退路。
     */
    SystemRestore,
    /**
     * v2.9 内核提权：运行**用户自备**的公开内核 exploit（例如 GhostLock / CVE-2026-43499 的机型移植）。
     * 本应用不内置、不下载 exploit；只做内核信息核对、整包推送、执行与真实 root 探测。
     */
    KernelRoot
}
