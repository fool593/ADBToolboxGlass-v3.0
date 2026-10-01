package com.example.adbtoolbox.common

// Root模块数据模型，对应 Magisk/KernelSU 模块
data class RootModuleData(
    val id: String,
    val name: String,
    val version: String = "1.0",
    val versionCode: Int = 1,
    val author: String = "Unknown",
    val description: String = "",
    val isEnabled: Boolean = true,
    val isInstalled: Boolean = true,
    val moduleDir: String = "",
    val hasAction: Boolean = false,
    val updateTime: Long = 0L,
    val size: String = ""
) {
    companion object {
        /** 模块目录。界面展示与复制都用它，避免各处硬编码字符串。 */
        const val MODULES_DIR: String = "/data/adb/modules"

        /**
         * 上次列表读取失败的原因（success=false 时才会被填入）。
         *
         * 为什么放在数据类里：`getInstalledModules()` 的签名只返回 `List<RootModuleData>`，
         * 无法区分「确实一个模块都没有」和「读取失败（无权限/命令不可用）」——
         * 界面如果把失败当成空列表，用户就会看到"暂无模块"这种假象。
         */
        var lastError: RootModuleError = RootModuleError.None
            private set

        /** 读取失败原因码。界面据此显示不同文案；[detail] 用于展示原始错误。 */
        var lastErrorDetail: String = ""
            private set

        /** 由 actual 实现（androidMain）在每次 `getInstalledModules()` 后调用。 */
        fun setLastError(error: RootModuleError, detail: String = "") {
            lastError = error
            lastErrorDetail = detail
        }
    }
}

/** 模块列表读取结果分类。 */
enum class RootModuleError {
    /** 没有失败（可能是真的没有模块）。 */
    None,

    /** shell 命令本身失败：没有 su / Shizuku 未授权 / 设备未 Root。 */
    CommandFailed,

    /** 命令可用但访问被拒绝（/data/adb 权限不足，未真正拿到 Root）。 */
    PermissionDenied
}

// Root模块安装结果
data class RootModuleInstallResult(
    val success: Boolean,
    val message: String
)
