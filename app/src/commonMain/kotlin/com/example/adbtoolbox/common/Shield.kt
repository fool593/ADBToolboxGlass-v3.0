package com.example.adbtoolbox.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 游龙式安全护盾：root 全盘恶意脚本扫描。
 *
 * 设计原则（安全第一）：
 * 1. 扫描永远只**列出与提示**，绝不自动删除——删除必须由用户逐条二次确认后才执行；
 * 2. 只扫文本型脚本/配置类文件（*.sh *.bat *.cmd *.conf *.ini *.txt *.log *.service
 *    *.timer *.rc *.properties，且 < 512KB），不读二进制与 apk/dex 区；
 * 3. 排除 /proc /sys /dev /data/app /apex /vendor（伪文件系统与代码区），
 *    保留 /data、/sdcard、/system 等真实用户与系统数据区（含每个应用的 cache）；
 * 4. 命中模式是可解释的启发式规则（rm -rf 相册/全机、wipe、dd 清零、格式化、
 *    recovery --wipe、重启进 bootloader/recovery、base64|sh、curl|sh、wget|sh 等），
 *    因此**可能有误报**——这正是"必须询问用户"而不是自动删的原因；
 * 5. 删除时拒绝 /proc /sys /dev 与含换行/分号的路径。
 */
object Shield {

    /** 全盘扫描。返回匹配文件的绝对路径（每行一个，最多 200 条）。 */
    suspend fun scan(): CommandResult = withContext(Dispatchers.Default) {
        val exts = "\\( -name '*.sh' -o -name '*.bak' -o -name '*.conf' -o -name '*.ini' " +
            "-o -name '*.txt' -o -name '*.log' -o -name '*.cmd' -o -name '*.bat' " +
            "-o -name '*.service' -o -name '*.timer' -o -name '*.rc' -o -name '*.properties' \\)"
        val pats = "rm[[:space:]-]*(rf|fr)[[:space:]-]*/(data|sdcard|storage|cache|system)|" +
            "wipe_(data|cache|framework|dalvik|userdata)|format[[:space:]]+[/]system|" +
            "dd[[:space:]]+if=/dev/(zero|urandom)|" +
            "reboot[[:space:]]+(bootloader|recovery)|recovery[[:space:]]+--wipe|" +
            "base64[[:space:]]+(-d|--decode)[^|]*[|][[:space:]]*(sh|bash)|" +
            "curl[[:space:]]+[^|]*[|][[:space:]]*(sh|bash)|" +
            "wget[[:space:]]+[^|]*[|][[:space:]]*(sh|bash)"
        val cmd = "find / -path /proc -prune -o -path /sys -prune -o -path /dev -prune -o " +
            "-path /data/app -prune -o -path /apex -prune -o -path /vendor -prune -o " +
            "-type f " + exts + " -size -512k -print0 2>/dev/null | " +
            "xargs -0 grep -lE '${pats}' 2>/dev/null | head -n 200"
        try {
            ADBTools.execCommand(cmd, timeout = 300)
        } catch (e: Exception) {
            CommandResult("", "${e.javaClass.simpleName}: ${e.message}", -1)
        }
    }

    /**
     * 删除一个**已由用户二次确认**的匹配文件。
     * 拒绝保护路径（/proc、/sys、/dev）与含换行/分号的路径，防止注入。
     */
    suspend fun delete(path: String): CommandResult = withContext(Dispatchers.Default) {
        val p = path.trim()
        if (p.isEmpty() || p.startsWith("/proc") || p.startsWith("/sys") || p.startsWith("/dev") ||
            p.contains("\n") || p.contains(";")
        ) {
            return@withContext CommandResult("", "refused: protected or unsafe path", 1)
        }
        val q = "'" + p.replace("'", "'\\''") + "'"
        try {
            ADBTools.execCommand("rm -f $q", timeout = 30)
        } catch (e: Exception) {
            CommandResult("", "${e.javaClass.simpleName}: ${e.message}", -1)
        }
    }

    /** 打开无障碍设置（用户可在此启用"拦截/识别新装应用"；删除仍需在本应用内确认）。 */
    fun accessibilitySettingsAction(): String = "android.settings.ACCESSIBILITY_SETTINGS"

    /** 无障碍是否已为本应用开启：读系统 enabled_accessibility_services（无需 root，真实结果）。 */
    suspend fun accessibilityEnabled(): Boolean = withContext(Dispatchers.Default) {
        try {
            ADBTools.execCommand("settings get secure enabled_accessibility_services", 20)
                .output.contains("com.example.adbtoolbox/", true)
        } catch (e: Exception) {
            false
        }
    }

    /** Root 是否真可用：su -c id 返回 uid=0（真实探针，不用路径猜测）。 */
    suspend fun rootAvailable(): Boolean = withContext(Dispatchers.Default) {
        try {
            ADBTools.execCommand("su -c id 2>/dev/null || id", 15).output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    /** 紧急逃生动作：强停最近接触/前台的应用（需 Root/Shizuku）；只针对单个包，绝不批量。 */
    suspend fun forceStopLastNewApp(): String = withContext(Dispatchers.Default) {
        val pkg = com.example.adbtoolbox.common.AppCache.lastSeenAccessibilityPackage.value
            ?: com.example.adbtoolbox.common.AppCache.lastForegroundAccessPackage.value
        if (pkg.isNullOrBlank() || pkg == "com.android.systemui") {
            "暂无可用目标：没有记录到最近前台/接触的应用"
        } else {
            val r = ADBTools.execCommand("am force-stop ", 15)
            if (r.exitCode == 0) "已强停："
            else "强停失败：\n"
        }
    }

}
