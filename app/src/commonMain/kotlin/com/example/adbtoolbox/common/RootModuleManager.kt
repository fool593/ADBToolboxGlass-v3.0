package com.example.adbtoolbox.common

// Root模块管理器 expect 声明，actual 实现在 androidMain
expect object RootModuleManager {
    fun installModule(zipFilePath: String): RootModuleInstallResult
    fun getInstalledModules(): List<RootModuleData>
    fun enableModule(moduleId: String): Boolean
    fun disableModule(moduleId: String): Boolean
    fun uninstallModule(moduleId: String): Boolean
    fun runAction(moduleId: String): String

    /**
     * 是否真的拿到了可用的 Root（`id` 返回 uid=0）。
     *
     * 与 [ADBTools.isRooted] 的区别：后者靠 su 文件路径/which 猜测，可能在"装了 Magisk 但本应用
     * 未被授权"时返回 true，导致界面把按钮点亮但每个操作都失败。这里直接验证真实提权结果。
     */
    fun canUseRoot(): Boolean

    /**
     * 扫描所有已安装模块，返回**确实自带 WebUI**（webroot / webui 目录里有入口 html）的模块 id 集合。
     *
     * 用途：界面只在模块真的带 UI 时才显示"打开界面"按钮（用户明确要求：没有 UI 就不显示该按钮）。
     * 只认真实存在的文件，不做任何猜测。
     */
    fun getWebUIModuleIds(): Set<String>

    /**
     * 准备打开模块 WebUI：以 Root 身份把模块的 webroot 拷到应用 cacheDir，返回可直接加载的本地入口路径。
     * 失败时返回带原因码的 [ModuleWebUIResult]。
     */
    fun prepareModuleWebUI(moduleId: String): ModuleWebUIResult
}
