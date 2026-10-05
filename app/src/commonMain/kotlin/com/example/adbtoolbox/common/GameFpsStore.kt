package com.example.adbtoolbox.common

/**
 * 按游戏保存的帧率配置（包名 → 目标帧率）。
 *
 * 为什么单独做一个存储：这份配置是"用户为某个游戏选的帧率"，与全局玻璃/主题配置无关，
 * 也不适合塞进 SharedPreferences 的玻璃配置里混着存。
 *
 * 存储格式刻意做得极简：一行一条 `包名=帧率`，多行拼接成一个字符串。
 * 好处是不依赖任何序列化库、任何 Android 版本都能读写、出问题时肉眼可读。
 */
expect object GameFpsStore {
    /** 读取全部按游戏配置；读不到或解析失败返回空表（不抛异常）。 */
    fun load(): Map<String, Int>

    /** 覆盖保存全部按游戏配置。 */
    fun save(settings: Map<String, Int>)
}
