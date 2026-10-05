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

/**
 * 打开模块自带 WebUI 的准备结果。
 *
 * 为什么需要它：模块的 WebUI 文件通常在 `/data/adb/modules/<id>/webroot/`，普通应用进程**读不到**，
 * 必须先以 Root 身份把 webroot 拷到应用自己的 cacheDir，再交给 WebView 加载。
 * 这个过程有多种失败方式（没有 Root、模块没有 webroot、拷贝失败、异常），
 * 所以不能只返回一个可空字符串——界面需要据此显示**具体原因**。
 */
data class ModuleWebUIResult(
    /** 拷贝到应用私有目录后的入口 html 绝对路径；失败时为 null */
    val localPath: String?,
    /** 失败原因码：E_WEBUI_MISSING / E_WEBUI_ROOT_REQUIRED / E_WEBUI_COPY_FAILED / E_WEBUI_EXCEPTION */
    val errorCode: String? = null,
    /** 原始细节（路径、exit code、异常文本），用于如实展示 */
    val detail: String? = null
) {
    val ok: Boolean get() = localPath != null
}
