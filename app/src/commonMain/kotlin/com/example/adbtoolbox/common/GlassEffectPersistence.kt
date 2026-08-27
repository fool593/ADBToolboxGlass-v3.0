package com.example.adbtoolbox.common

// 液态玻璃效果持久化 expect 声明
expect object GlassEffectPersistence {
    // 保存所有液态玻璃参数到持久化存储
    fun saveAll()

    // 从持久化存储加载所有液态玻璃参数
    fun loadAll()

    // 清除持久化存储（恢复默认）
    fun clear()
}
