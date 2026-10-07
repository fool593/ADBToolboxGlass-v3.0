package com.example.adbtoolbox

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 安全护盾的无障碍服务。
 *
 * 用途：用户开启后，系统会把新安装/卸载应用、窗口变化等事件交给这里。
 * 本服务把这些事件里可见的"包名"记录下来（写入 [AppCache.lastSeenAccessibilityPackage]），
 * 护盾页面据此提醒用户留意新装应用；**删除动作永远由用户在护盾页逐条确认后执行**，
 * 本服务不执行任何删除。
 */
class ShieldAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        com.example.adbtoolbox.common.AppCache.accessibilityServiceConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val pkg = event.packageName?.toString().orEmpty()
            if (pkg.isNotEmpty() && pkg != com.example.adbtoolbox.common.AppCache.lastSeenAccessibilityPackage.value) {
                com.example.adbtoolbox.common.AppCache.lastSeenAccessibilityPackage.value = pkg
            }
            // 安装/卸载事件：记录包名，护盾页可以展示"系统刚接触过的包"
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                com.example.adbtoolbox.common.AppCache.lastForegroundAccessPackage.value =
                    event.packageName?.toString().orEmpty()
            }
        } catch (e: Exception) {
            // 事件记录失败不影响服务可用性
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        com.example.adbtoolbox.common.AppCache.accessibilityServiceConnected.value = false
        super.onDestroy()
    }
}