# 华为专属深度性能方法库（HarmonyOS / EMUI / MagicOS）

对应实现：`app/src/commonMain/kotlin/com/example/adbtoolbox/common/perf/HuaweiPerf.kt`
界面：`app/src/commonMain/kotlin/com/example/adbtoolbox/common/ui/HuaweiBoostScreen.kt`
文案：`huawei_strings.tsv`

## 0. 执行层约定

- 每条命令通过 `ADBTools.execPerfCommand(command, timeout)` 下发，内部按 **Shizuku → Root → 普通 shell** 三级回退；最终由哪一级执行取决于设备实际具备的权限。
- 命令末尾统一追加唯一标记 `HWM_<ID大写>_DONE`（由 `HuaweiPerf.m` 自动追加，界面展示的命令原文含该标记）。
- shell 里用 `;` 串联的多条命令，**退出码只反映最后一条**。因此判定成功的规则是：
  `未出现 permission denied / operation not permitted / not allowed / SecurityException 字样` 且 `出现标记 或 exitCode == 0`。
  真正是否生效以「验证命令」的输出为准，不以退出码为准。
- 需要回读状态的条目在命令里附带 `echo "xxx=$(...)"` 状态回读行，原始 stdout 直接展示在界面上，不加工。
- 批量执行前后各读一次可用内存（`dumpsys meminfo` 的 `Free RAM:`，读不到回退 `/proc/meminfo` 的 `MemAvailable`），差值即「释放内存」，读不到就是 0，不估算。

## 1. 验证语义（界面上的 有效 / 无效 / 需权限 / 不适用）

- **需权限**：`requiresPermission = root` 但本机无 Root，或 `= shizuku` 但 Shizuku / Root / Dhizuku 都不可用。不执行验证命令，直接判定。
- **不适用**：该条为华为专有接口（`huaweiOnly = true`）而本机不是华为 / 荣耀机型。
- **有效**：验证命令输出非空，且包含 `verifyExpect`（若指定）。
- **无效**：验证命令无输出或不含预期片段。

关于「无效」的两种含义，界面上要看验证输出原文区分：

1. 该 ROM 确实没有这个接口（例如 `/sys/module/lowmemorykiller/parameters/minfree` 在 Android 11+ 的 lmkd 机型上不存在）→ 无效就是无效。
2. 接口存在但当前未设置，`getprop` / `settings list` 读不到（如 `debug.hwui.renderer`、`net.tcp.default_init_rwnd`、`hw_power_mode`）→ 首次验证显示「无效」，执行一次后再验证就会显示实际值。

`settings put` 对未预置的键仍然可以写入，但**能否被系统读取并生效取决于 ROM**，所以这类键一律先用验证命令确认真实存在。

## 2. 方法清单（30 条）

### 组 `bg` 后台与自启（4 条）

#### 1. `bg_kill_all` 结束全部后台进程

- 命令：`am kill-all 2>/dev/null; echo "FreeRAM=$(dumpsys meminfo 2>/dev/null | grep -m1 'Free RAM:' | tr -d ' ')"; echo HWM_BG_KILL_ALL_DONE`
- 验证：`am help 2>/dev/null | grep -m1 kill-all`（预期含 `kill-all`）
- 恢复：无。一次性操作，被结束的后台进程会按需重新拉起。
- 权限：Shizuku
- 风险：safe
- 适用 ROM：Android 5.0+ 全系（EMUI 5 及以上、HarmonyOS 2/3/4）
- 预期收益：立即回收 cached 进程占用的内存，减少内存压力导致的应用重载。
- 原理：`am kill-all` 调用 `ActivityManagerService.killAllBackgroundProcesses()`，只结束处于 cached 状态的后台进程；前台应用与本应用不受影响，比逐个 `force-stop` 更干净。
- 已知限制：只影响 cached 进程，杀不掉被前台服务或 `startForeground` 保活的常驻应用。部分 OEM 把 `am` 的 help 文本裁剪过，验证命令可能读不到 `kill-all` 而显示「无效」，此时看命令本身是否返回 `FreeRAM=` 行即可判断真实执行情况。

#### 2. `bg_disable_ota` 停用系统更新服务

- 命令：`pm disable-user --user 0 com.huawei.android.hwouc 2>/dev/null; am force-stop com.huawei.android.hwouc 2>/dev/null; echo "state=$(pm list packages -d 2>/dev/null | grep -m1 hwouc)"; echo HWM_BG_DISABLE_OTA_DONE`
- 验证：`pm list packages 2>/dev/null | grep -m1 com.huawei.android.hwouc`（预期含包名）
- 恢复：`pm enable com.huawei.android.hwouc 2>/dev/null`
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：EMUI 8+ / HarmonyOS 2+，且系统内存在 `com.huawei.android.hwouc`
- 预期收益：去掉 OTA 检查、下载与升级包校验带来的周期性 CPU、网络与存储开销。
- 原理：`com.huawei.android.hwouc` 是华为系统更新服务，常驻后台并周期性联网检查升级、校验已下载的升级包，属于典型的「用户没在用也会耗电」的组件。
- 已知限制：停用后「设置 → 系统更新」入口同时失效，收不到任何系统更新提示；需要时必须先用恢复命令重新启用。个别 ROM 上停用后会在开机时被系统重新启用（系统应用保护），可通过验证命令观察。

#### 3. `bg_disable_stats` 停用华为数据统计上报

- 命令：`pm disable-user --user 0 com.huawei.bd 2>/dev/null; am force-stop com.huawei.bd 2>/dev/null; echo "state=$(pm list packages -d 2>/dev/null | grep -m1 com.huawei.bd)"; echo HWM_BG_DISABLE_STATS_DONE`
- 验证：`pm list packages 2>/dev/null | grep -m1 com.huawei.bd`
- 恢复：`pm enable com.huawei.bd 2>/dev/null`
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：存在 `com.huawei.bd` 的 EMUI / HarmonyOS
- 预期收益：减少后台日志写入与数据上报唤醒，不影响系统功能。
- 原理：`com.huawei.bd` 承担用户体验改进计划的数据采集与回传，随系统常驻并持续写日志、定期上报。
- 已知限制：**包名与职责在不同 ROM 上不完全一致**（部分机型叫 `com.huawei.bd`，部分机型的统计上报合并在 `com.huawei.hicloud` 等组件里）。因此本条的验证命令先确认包是否存在，不存在则显示「无效」，不会误禁用其它组件。禁用的副作用是无法参与用户体验改进计划，对功能无影响。

#### 4. `bg_autostart_page` 打开华为应用启动管理

- 命令：`am start -n com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity 2>/dev/null || am start -n com.huawei.systemmanager/.optimize.process.ProcessManagerActivity 2>/dev/null; echo "launched=$?"; echo HWM_BG_AUTOSTART_PAGE_DONE`
- 验证：`pm list packages 2>/dev/null | grep -m1 com.huawei.systemmanager`
- 恢复：无。仅界面跳转，不修改任何设置。
- 权限：Shizuku
- 风险：safe，默认不勾选
- 适用 ROM：EMUI 8+ / HarmonyOS 2+（手机管家 `com.huawei.systemmanager` 存在）
- 预期收益：在华为机型上手动关闭自启是减少后台唤醒最有效的手段，比任何脚本都直接。
- 原理：自启由手机管家统一接管，**没有公开的 shell 写入接口**（写入会在 AMS 层被拒绝且不同 ROM 的策略不同）。因此本条只做界面跳转，由用户看到真实的自启列表后自行决定。
- 已知限制：两个 Activity 名称是经验值，`StartupNormalAppListActivity` 在部分 ROM 上不存在，命令用 `||` 回退到 `ProcessManagerActivity`；两者都失败时 `launched=` 行会给出非 0 退出码，界面如实显示，不做静默成功。高版本 HarmonyOS 可能把入口收到「应用启动管理」之外的菜单。

### 组 `mem` 内存与缓存（5 条）

#### 5. `mem_trim_caches` 清理全部应用缓存

- 命令：`pm trim-caches 128G 2>/dev/null; echo "data=$(df /data 2>/dev/null | tail -n1 | tr -s ' ')"; echo HWM_MEM_TRIM_CACHES_DONE`
- 验证：`cmd package help 2>/dev/null | grep -m1 trim-caches`（预期含 `trim-caches`）
- 恢复：无。缓存由应用在需要时自行重建，不涉及用户数据。
- 权限：Shizuku
- 风险：safe，默认勾选
- 适用 ROM：Android 7.0+ / EMUI 5.1+
- 预期收益：释放存储空间，减少缓存占用带来的存储带宽与内存压力。
- 原理：`pm trim-caches <size>` 让 PackageManager 通知每个应用把自身缓存裁剪到指定空闲空间；每个应用清理的是自己的 cache 目录，不触碰应用数据。
- 已知限制：只能清应用缓存，Android 的 `/data/system/` 下系统级缓存与 `media` 缩略图缓存不在范围内。部分 ROM 对 `trim-caches` 的实现是异步的，命令返回后空间不会立刻全部释放（用 `df /data` 回读的行对比即可）。

#### 6. `mem_drop_caches` 回收内核页缓存

- 命令：`sync 2>/dev/null; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; echo "cached=$(grep -m1 '^Cached:' /proc/meminfo 2>/dev/null | tr -s ' ')"; echo HWM_MEM_DROP_CACHES_DONE`
- 验证：`ls /proc/sys/vm/drop_caches 2>/dev/null`（预期含 `drop_caches`）
- 恢复：无。缓存会在文件被再次读取时自然重建，不需要也不存在「恢复」动作。
- 权限：Root
- 风险：caution，默认不勾选
- 适用 ROM：全系 Linux 内核（Android 5.0+）
- 预期收益：立刻把被文件缓存占用的内存还给系统，降低内存压力。
- 原理：写 `3` 到 `/proc/sys/vm/drop_caches` 让内核同时丢弃 pagecache、dentries 与 inodes。写入前必须 `sync`，否则脏页会先被写回磁盘造成额外 I/O。
- 已知限制：不改变物理内存上限，只是把「可回收内存」提前回收；紧接着打开应用会因为缓存缺失而变慢一点，属于正常现象。Shizuku（shell，uid 2000）在多数 ROM 上对 `drop_caches` 无写权限，实际需要 Root，权限不足时界面会显示 permission denied 原文。低内存机型收益明显，12GB 以上机型基本无感。

#### 7. `mem_compact_memory` 触发内存碎片整理

- 命令：`sync 2>/dev/null; echo 1 > /proc/sys/vm/compact_memory 2>/dev/null; echo "buddy=$(head -n1 /proc/buddyinfo 2>/dev/null)"; echo HWM_MEM_COMPACT_MEMORY_DONE`
- 验证：`ls /proc/sys/vm/compact_memory 2>/dev/null`
- 恢复：无。一次性整理动作，内核自行维护后续状态。
- 权限：Root
- 风险：safe，默认不勾选
- 适用 ROM：内核开启 `CONFIG_COMPACTION` 的 Android 5.0+（主流机型均开启）
- 预期收益：提高后续大块内存分配的命中率，降低长时间运行后的分配延迟抖动。
- 原理：写 `1` 到 `/proc/sys/vm/compact_memory` 让内核立即执行一次内存规整，把可迁移页合并为连续物理块，减少高阶分配失败后触发直接回收（direct reclaim）的概率。
- 已知限制：耗时与碎片程度相关，重度碎片时可能阻塞数百毫秒（命令超时设为 60 秒，界面显示真实结果）。对未碎片的系统是空操作，无收益也无副作用。整理结果不持久，随运行时间重新碎片化。

#### 8. `mem_lmk_tune` 调整低内存回收阈值

- 命令：`[ -f /data/local/tmp/hw_lmk_backup ] || cat /sys/module/lowmemorykiller/parameters/minfree > /data/local/tmp/hw_lmk_backup 2>/dev/null; echo 1024,2048,3072,4096,6144,8192 > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null; echo "minfree=$(cat /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null)"; echo HWM_MEM_LMK_TUNE_DONE`
- 验证：`ls /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null`
- 恢复：`cat /data/local/tmp/hw_lmk_backup > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null; echo "minfree=$(cat /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null)"`
- 权限：Root
- 风险：risky，默认不勾选
- 适用 ROM：Android 10 及以下且内核仍使用 `lowmemorykiller` 模块的机型
- 预期收益：把阈值调到较低水平，让系统尽量保留后台进程，切回应用时减少冷启动重载。
- 原理：`minfree` 是内存水位阈值（单位 4KB 页），决定 6 档 adj 分别在哪一档剩余内存下开始杀后台。备份在首次执行时写入 `/data/local/tmp/hw_lmk_backup`，重复执行不会覆盖原始值（`[ -f ... ] ||` 保证），恢复命令原样写回。
- 已知限制：**Android 11 起主流机型改用用户态 lmkd，该 sysfs 节点通常不存在，本条会显示「无效」**——这是事实，不是失败。阈值设得过低会让回收抖动加剧（lmkd 频繁杀进程又立刻被拉起），低内存机型反而更卡；12GB 以上机型改动通常无明显收益。写入值本身是经验值，不同内核档位含义一致但默认值差异很大，务必先看备份文件里的原值。

#### 9. `mem_zram_off` 关闭 ZRAM 交换

- 命令：`cat /proc/swaps > /data/local/tmp/hw_swaps_backup 2>/dev/null; swapoff /dev/block/zram0 2>/dev/null; echo "zram=$(grep -c zram /proc/swaps 2>/dev/null)"; echo HWM_MEM_ZRAM_OFF_DONE`
- 验证：`grep -m1 zram /proc/swaps 2>/dev/null`（预期含 `zram`）
- 恢复：`swapon /dev/block/zram0 2>/dev/null; echo "zram=$(grep -c zram /proc/swaps 2>/dev/null)"`
- 权限：Root
- 风险：risky，默认不勾选
- 适用 ROM：全系（ZRAM 由内核 `CONFIG_ZRAM` 提供）
- 预期收益：仅当物理内存充足、卡顿来自交换抖动时才有收益，CPU 不再花时间做页压缩与解压。
- 原理：`swapoff /dev/block/zram0` 停用基于内存的压缩交换分区，原先被压缩存放的匿名页会被换回物理内存。
- 已知限制：**在 6GB / 8GB 机型上关闭 ZRAM 会显著变慢甚至触发应用被杀**，这是最需要克制的一条。部分 ROM 的 swap 设备名不是 `zram0`（可能是 `zram1` 或多设备），此时 `swapon` 无法完全恢复，**实际恢复需要重启**——恢复命令只做到尽力而为，界面把恢复命令原文写清，不做「一定恢复」的承诺。执行结果里的 `zram=` 计数是回读证据：为 0 表示已关闭。

### 组 `gpu` 渲染与 GPU（4 条）

#### 10. `gpu_anim_scale` 动画时长缩到 0.5x

- 命令：`settings put global window_animation_scale 0.5 2>/dev/null; settings put global transition_animation_scale 0.5 2>/dev/null; settings put global animator_duration_scale 0.5 2>/dev/null; echo "scale=$(settings get global window_animation_scale 2>/dev/null)/$(settings get global transition_animation_scale 2>/dev/null)/$(settings get global animator_duration_scale 2>/dev/null)"; echo HWM_GPU_ANIM_SCALE_DONE`
- 验证：`settings list global 2>/dev/null | grep -m1 window_animation_scale`（预期含键名）
- 恢复：`settings put global window_animation_scale 1 2>/dev/null; settings put global transition_animation_scale 1 2>/dev/null; settings put global animator_duration_scale 1 2>/dev/null`
- 权限：Shizuku（写全局设置需要 `WRITE_SECURE_SETTINGS`）
- 风险：safe，默认勾选
- 适用 ROM：Android 5.0+ 全系
- 预期收益：界面切换等待时间减半，主观响应更快；不改变真实帧率。
- 原理：WindowManager 与 Choreographer 在启动窗口动画、Activity 转场与属性动画时都会读取这三个全局缩放系数，按比例缩短动画时长。
- 已知限制：只影响动画时长，不影响渲染性能本身；0.5 而不是 0 是为了保留动画的视觉连续性，设 0 会在部分应用里造成转场突兀。命令执行后新启动的动画立即生效，已经在播放的动画不受影响。

#### 11. `gpu_skiagl` 强制 HWUI 使用 SkiaGL

- 命令：`setprop debug.hwui.renderer skiagl 2>/dev/null; echo "renderer=$(getprop debug.hwui.renderer 2>/dev/null)"; echo HWM_GPU_SKIAGL_DONE`
- 验证：`getprop ro.hardware.egl 2>/dev/null; getprop debug.hwui.renderer 2>/dev/null`（任一非空即视为接口可读）
- 恢复：`setprop debug.hwui.renderer "" 2>/dev/null`
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：**EMUI 10 / 11（Android 10 / 11）默认走 OpenGL 渲染管线时有意义**；Android 12 及 HarmonyOS 3/4 默认已是 SkiaGL
- 预期收益：在默认走 OpenGL 的老版本 EMUI 上减少渲染管线切换开销。
- 原理：`debug.hwui.renderer` 决定 HWUI 使用哪个 GPU 后端（`opengl` / `skiagl` / `skia`）。设为 `skiagl` 让绘制走 Skia 的 GPU 后端。
- 已知限制：**在 Android 12+ 上是无操作**（本身就是默认值），不要把它当成通用「GPU 加速」。属性在重启后失效，需要重新设置。设置后必须重启应用进程才生效（本工具不自动杀进程，避免误伤）。个别老机型在 `skiagl` 下出现文字渲染异常，用恢复命令清空属性并重启即可回退。

#### 12. `gpu_render_scale` 降低渲染分辨率

- 命令（运行时解析，`W`/`H` 为物理分辨率的 85%，按 4 的倍数取整）：
  `wm size <W>x<H> 2>/dev/null; echo "size=$(wm size 2>/dev/null | tail -n1 | tr -d ' ')"; echo HWM_GPU_RENDER_SCALE_DONE`
  读不到物理分辨率时下发：`echo HWM_SKIP_NO_PHYSICAL_SIZE`（界面显示为「跳过」，不假装成功）
- 验证：`wm size 2>/dev/null`（预期含 `Physical size`）
- 恢复：`wm size reset 2>/dev/null; echo "size=$(wm size 2>/dev/null | tail -n1 | tr -d ' ')"`
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：Android 4.4+ 全系（`wm size` 属于 WindowManager shell 接口）
- 预期收益：GPU 每帧填充像素减少约 28%（0.85² ≈ 0.72），明显降低 GPU 负载与功耗，代价是画面变软。
- 原理：`wm size` 设置逻辑分辨率，SurfaceFlinger 负责把逻辑分辨率缩放到物理面板输出；GPU 的填充率与带宽消耗随像素数下降。
- 已知限制：**目标值不写死**——写死 1080x2340 会在 720p 机型上反而放大渲染负担，所以由 `HuaweiPerf.resolveCommand` 按真实 `Physical size` 现算，界面在「实际下发命令」里展示算出来的那条。副作用：部分应用按逻辑分辨率做布局，可能出现错位；截图与录屏尺寸随之变化；`wm size` 在重启后保持，必须用 `wm size reset` 恢复。与「开发者选项 → 最小宽度」等设置叠加时行为不可预期。

#### 13. `gpu_sf_latch` 允许非同步帧提交

- 命令：`setprop debug.sf.latch_unsignaled 1 2>/dev/null; echo "latch=$(getprop debug.sf.latch_unsignaled 2>/dev/null)"; echo HWM_GPU_SF_LATCH_DONE`
- 验证：`dumpsys SurfaceFlinger 2>/dev/null | grep -m1 -i SurfaceFlinger`（预期含 `SurfaceFlinger`）
- 恢复：`setprop debug.sf.latch_unsignaled 0 2>/dev/null`
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：Android 9+（SurfaceFlinger 支持 `latch_unsignaled` 的版本）
- 预期收益：降低触控到显示的延迟（约一帧）。
- 原理：默认状态下 SurfaceFlinger 会等待 frame buffer 的同步信号再锁定图层，`debug.sf.latch_unsignaled=1` 取消这次等待，缩短合成流水线延迟。
- 已知限制：属于调试属性，Google 并未承诺长期保留，新版本可能忽略。副作用是个别应用（尤其是自绘 SurfaceView 的应用）可能出现撕裂。重启后失效。属性未设置前 `getprop` 读不到值，验证显示「无效」属正常，执行一次后再验证即显示 `1`。

### 组 `power` 性能模式与温控（5 条）

#### 14. `power_mode_on` 打开华为性能模式

- 命令：`settings put global hw_power_mode 1 2>/dev/null; settings put secure hw_performance_mode 1 2>/dev/null; echo "global=$(settings get global hw_power_mode 2>/dev/null) secure=$(settings get secure hw_performance_mode 2>/dev/null)"; echo HWM_POWER_MODE_ON_DONE`
- 验证：`settings list global 2>/dev/null | grep -m1 -E "hw_power_mode|hw_performance_mode"`（预期含 `hw_`）
- 恢复：`settings put global hw_power_mode 0 2>/dev/null; settings put secure hw_performance_mode 0 2>/dev/null`
- 权限：Shizuku
- 风险：caution，默认勾选（写入本身无害：键不存在时只是多一个无人读取的键值）
- 适用 ROM：EMUI 8 ~ EMUI 11 / HarmonyOS 2~4，且该 ROM 的 Settings 里预置了对应键
- 预期收益：放开 CPU / GPU 频率与调度限制，重负载下掉帧减少；代价是发热与耗电上升。
- 原理：华为的功耗管理读取 Settings 中的性能模式开关并切换功耗策略，等价于「设置 → 电池 → 性能模式」。
- 已知限制：**键名在不同 EMUI / HarmonyOS 版本上并不统一**（`hw_power_mode` / `hw_performance_mode` / 华为自有 `hw_...` 前缀键在不同版本间有出入），本条同时写两个键并回读两者，界面把回读结果原样显示；验证命令确认该 ROM 是否存在这两个键之一，不存在时显示「无效」而命令本身仍会写入。部分 HarmonyOS 版本的性能模式被收进「游戏助手 / 应用助手」的应用级配置，全局键写入不会改变任何行为——这点必须如实说明。

#### 15. `power_saver_off` 退出省电模式

- 命令：`settings put global low_power 0 2>/dev/null; settings put global low_power_sticky 0 2>/dev/null; echo "low_power=$(settings get global low_power 2>/dev/null)"; echo HWM_POWER_SAVER_OFF_DONE`
- 验证：`settings list global 2>/dev/null | grep -m1 low_power`（预期含 `low_power`）
- 恢复：`settings put global low_power 1 2>/dev/null`
- 权限：Shizuku
- 风险：safe，默认勾选
- 适用 ROM：Android 5.0+ 全系
- 预期收益：解除省电模式对 CPU 频率、后台同步与亮度策略的限制。
- 原理：`PowerManagerService` 观察 `Settings.Global.low_power`，置 0 即退出省电模式；`low_power_sticky` 置 0 避免关机重启后被粘性恢复。
- 已知限制：如果设备**从未手动开过省电模式**，`settings list global` 里可能没有 `low_power` 键，验证会显示「无效」，但 `settings put` 仍会写入并被 PMS 读取——这是验证语义的正常表现（见第 1 节）。本条不改变「低电量自动开启省电」的策略，电量低于阈值时系统仍会自动进入省电模式。

#### 16. `power_fixed_perf` 固定高性能模式

- 命令：`cmd power set-fixed-performance-mode-enabled true 2>&1 | head -n3; echo HWM_POWER_FIXED_PERF_DONE`
- 验证：`cmd power help 2>/dev/null | grep -m1 fixed-performance-mode-enabled`（预期含子命令名）
- 恢复：`cmd power set-fixed-performance-mode-enabled false 2>&1 | head -n3`
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：Android 11+（`PowerManagerShellCommand` 提供该子命令）
- 预期收益：亮屏期间频率稳定，减少降频造成的卡顿；发热与耗电明显增加。
- 原理：`set-fixed-performance-mode-enabled` 让 `PowerManagerService` 进入固定性能档，不再随负载与温度动态调整性能档位。
- 已知限制：Android 10 及以下不存在该子命令，`2>&1 | head -n3` 会把 `Unknown command` 之类的错误原文带进 stdout，界面如实显示。部分 ROM 在屏幕熄灭或温度超限时仍会退出该模式（由厂商策略决定，本命令无法覆盖）。

#### 17. `power_gov_performance` CPU 调频切 performance

- 命令：
  `[ -f /data/local/tmp/hw_gov_backup ] || for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do [ -w "$c" ] && echo "$c $(cat $c)" >> /data/local/tmp/hw_gov_backup; done; for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > "$c" 2>/dev/null; done; echo "gov=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)"; echo HWM_POWER_GOV_PERFORMANCE_DONE`
- 验证：`cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null`（非空即视为接口存在）
- 恢复：`while read -r p g; do [ -w "$p" ] && echo "$g" > "$p" 2>/dev/null; done < /data/local/tmp/hw_gov_backup; echo "gov=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)"`
- 权限：Root
- 风险：risky，默认不勾选
- 适用 ROM：内核暴露 cpufreq 且 governor 列表里有 `performance` 的机型；华为麒麟平台多数使用 `schedutil` / `interactive`
- 预期收益：频率响应最快，满载性能最高；空载也维持高频，耗电与发热显著上升。
- 原理：`scaling_governor` 决定 cpufreq 的调频策略，`performance` 直接锁定该策略允许的最高频，跳过负载采样。原值按「路径 + 原 governor」成对备份到 `/data/local/tmp/hw_gov_backup`，恢复命令逐对写回。
- 已知限制：备份文件只在首次执行时创建（`[ -f ... ] ||`），因此恢复的是**第一次执行前**的原始值，这是刻意的。部分内核把 governor 限制在 `schedutil`（华为自研调度器接管调频），写入 `performance` 会被忽略，此时状态回读行 `gov=` 会显示实际值仍是原值——界面展示该行，可据此判断是否真的生效。长时间锁最高频有真实的过热与电池损耗风险。

#### 18. `power_thermal_off` 关闭内核温控

- 命令：
  `[ -f /data/local/tmp/hw_thermal_backup ] || for z in /sys/class/thermal/thermal_zone*/mode; do [ -w "$z" ] && echo "$z $(cat $z)" >> /data/local/tmp/hw_thermal_backup; done; for z in /sys/class/thermal/thermal_zone*/mode; do echo disabled > "$z" 2>/dev/null; done; echo "mode0=$(cat /sys/class/thermal/thermal_zone0/mode 2>/dev/null)"; echo HWM_POWER_THERMAL_OFF_DONE`
- 验证：`cat /sys/class/thermal/thermal_zone0/mode 2>/dev/null`（非空即视为温控节点存在）
- 恢复：`while read -r p v; do [ -w "$p" ] && echo "$v" > "$p" 2>/dev/null; done < /data/local/tmp/hw_thermal_backup; echo "mode0=$(cat /sys/class/thermal/thermal_zone0/mode 2>/dev/null)"`
- 权限：Root
- 风险：risky，默认不勾选
- 适用 ROM：使用 Linux thermal framework（`/sys/class/thermal/thermal_zone*/mode`）的机型
- 预期收益：高温时不再降频，短时满载性能更高。
- 原理：thermal zone 的 `mode` 写为 `disabled` 后，内核 thermal framework 不再对该 zone 触发被动降温（cpufreq cooling）。原值备份到 `/data/local/tmp/hw_thermal_backup`。
- 已知限制：**存在真实的过热与硬件损伤风险**，只适合明确需要短时满载的场景（跑分、导出、编译），不适合日常开启。华为的功耗/温控大量由自研服务（`com.huawei.powergenie` 等）在用户态实现，可能绕过内核 thermal zone 直接限频，因此本条在部分机型上「写入成功但行为不变」——恢复能力也因此做不到 100% 覆盖厂商策略，稳妥做法是执行后用恢复命令还原并重启。部分机型没有 `thermal_zone0/mode`（只有 `temp`），那种情况下本条显示「无效」，不会误写。

### 组 `storage` 存储与编译（4 条）

#### 19. `storage_bg_dexopt` 立即执行后台 dexopt

- 命令：`cmd package bg-dexopt-job 2>&1 | head -n5; echo HWM_STORAGE_BG_DEXOPT_DONE`
- 验证：`cmd package help 2>/dev/null | grep -m1 bg-dexopt-job`（预期含子命令名）
- 恢复：无。编译产物由系统在空间不足或版本变更时自行回收。
- 权限：Shizuku
- 风险：safe，默认勾选
- 适用 ROM：Android 10+（`bg-dexopt-job` 子命令随 Android 10 引入）
- 预期收益：应用冷启动与首帧更快。
- 原理：`cmd package bg-dexopt-job` 让 PackageManager 立刻跑一次原本只在夜间空闲时调度的后台 dexopt，按 baseline / cloud profile 把应用编译成机器码。
- 已知限制：**命令是同步的，耗时随已安装应用数量增长**（本条超时设为 240 秒），执行期间 CPU 占用高、机身会热。首次执行后系统可能因为 profile 变化在后续夜间再跑一次。Android 10 以下不支持，会输出 `Unknown command` 原文。

#### 20. `storage_compile_all` 全量 speed-profile 编译

- 命令：`cmd package compile -m speed-profile -a 2>&1 | tail -n5; echo HWM_STORAGE_COMPILE_ALL_DONE`
- 验证：`cmd package help 2>/dev/null | grep -m1 compile`（预期含 `compile`）
- 恢复：`cmd package compile --reset -a 2>&1 | tail -n5`（清除编译产物，回到解释执行 + JIT）
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：Android 7.0+（`cmd package compile` 是稳定接口）
- 预期收益：启动速度提升最明显的一条。
- 原理：`-m speed-profile` 用应用已有的 profile 做 AOT 编译，把热点方法编译成 oat 机器码，`-a` 表示对所有已安装应用执行。
- 已知限制：**耗时可能超过十分钟**（超时设为 900 秒），并且额外占用存储（几百 MB 到数 GB，取决于应用数量）。恢复命令会清掉全部编译产物，清完之后短期内应用启动会变慢（等系统重新 JIT / 重新编译）。没有 profile 的应用只能按 baseline 编译，收益有限。存储在 90% 以上占满时建议先清理再执行。

#### 21. `storage_fstrim` 手动 TRIM /data

- 命令：`fstrim -v /data 2>&1 | head -n3; echo HWM_STORAGE_FSTRIM_DONE`
- 验证：`ls /system/bin/fstrim 2>/dev/null`（预期含 `fstrim`）
- 恢复：无。一次性维护动作，效果不可撤销也不需要撤销。
- 权限：Root
- 风险：caution，默认不勾选
- 适用 ROM：Android 4.3+ 全系（system 分区自带 `fstrim`）
- 预期收益：恢复擦除块的写入性能，缓解长期使用后的写入放大。
- 原理：`fstrim` 把文件系统中已删除数据块的回收请求（TRIM）下发给闪存控制器，让主控提前擦除这些块。
- 已知限制：**命令本身较慢**（超时 180 秒），真正整理由主控在后台完成，执行后不会立刻看到读写速度提升。频繁执行没有意义（Android 自身会在空闲维护窗口自动 TRIM），建议数月一次。UFS 3.1 / 4.0 机型收益很小。

#### 22. `storage_clear_logs` 清理崩溃日志与 logcat

- 命令：`rm -rf /data/tombstones/* /data/system/dropbox/* /data/anr/* 2>/dev/null; logcat -c 2>/dev/null; echo "tombstones=$(ls /data/tombstones 2>/dev/null | wc -l)"; echo HWM_STORAGE_CLEAR_LOGS_DONE`
- 验证：`ls -d /data/tombstones 2>/dev/null`（预期含 `tombstones`）
- 恢复：无，**不可逆**。删除的崩溃日志无法找回。
- 权限：Root
- 风险：caution，默认不勾选
- 适用 ROM：Android 5.0+ 全系
- 预期收益：回收存储并减少日志写入 I/O。
- 原理：`/data/tombstones`（native 崩溃）、`/data/system/dropbox`（系统事件）、`/data/anr`（ANR trace）会随着时间累积占用数百 MB 到数 GB；`logcat -c` 清空内核与用户态日志环形缓冲区。
- 已知限制：**代价是丢失崩溃现场**——出了问题时无法回溯原因，出问题的机器不要执行。`dropbox` 目录里的部分文件是判定系统异常（如 `system_server_watchdog`）的依据。Shizuku（shell）通常没有 `/data/anr` 的删除权限，实际需要 Root。

### 组 `net` 网络与待机（4 条）

#### 23. `net_wifi_scan_off` 关闭始终扫描 WLAN

- 命令：`settings put global wifi_scan_always_enabled 0 2>/dev/null; echo "scan=$(settings get global wifi_scan_always_enabled 2>/dev/null)"; echo HWM_NET_WIFI_SCAN_OFF_DONE`
- 验证：`settings list global 2>/dev/null | grep -m1 wifi_scan_always_enabled`（预期含键名）
- 恢复：`settings put global wifi_scan_always_enabled 1 2>/dev/null`
- 权限：Shizuku
- 风险：safe，默认勾选
- 适用 ROM：Android 4.4+ 全系（对应「WLAN 扫描」里的「始终允许扫描」）
- 预期收益：降低待机唤醒次数与耗电。
- 原理：该开关打开时，即使 WLAN 处于关闭状态，系统仍会定期扫描以提供给位置服务，产生射频占用与唤醒。
- 已知限制：依赖 Wi-Fi 扫描的定位精度会下降（室内定位、部分地图的定位速度受影响的场景）。部分 ROM 在「位置信息」页面重置后会把它改回 1。

#### 24. `net_mobile_always_on_off` 关闭始终开启移动数据

- 命令：`settings put global mobile_data_always_on 0 2>/dev/null; echo "mdao=$(settings get global mobile_data_always_on 2>/dev/null)"; echo HWM_NET_MOBILE_ALWAYS_ON_OFF_DONE`
- 验证：`settings list global 2>/dev/null | grep -m1 mobile_data_always_on`（预期含键名）
- 恢复：`settings put global mobile_data_always_on 1 2>/dev/null`
- 权限：Shizuku
- 风险：safe，默认勾选
- 适用 ROM：Android 7.0+，且 ROM 的开发者选项提供了「始终开启移动数据」
- 预期收益：减少移动数据链路维持带来的耗电与信令开销。
- 原理：该开关打开时，连接 WLAN 期间系统仍保持移动数据链路可用，以便 WLAN 质量差时立刻切换。关闭后切换会慢一点，但省掉一条常驻链路的维持成本。
- 已知限制：WLAN 与移动数据切换的衔接变慢（弱信号 WLAN 场景下可能短暂断网）。部分 ROM 未预置该键，验证显示「无效」但写入仍生效（见第 1 节）。

#### 25. `net_wifi_low_latency` 开启 WLAN 低延迟模式

- 命令：`cmd wifi force-low-latency-mode enabled 2>&1 | head -n3; echo HWM_NET_WIFI_LOW_LATENCY_DONE`
- 验证：`cmd wifi 2>/dev/null | grep -m1 force-low-latency-mode`（预期含子命令名）
- 恢复：`cmd wifi force-low-latency-mode disabled 2>&1 | head -n3`
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：Android 11+，且 WLAN 驱动实现了低延迟模式
- 预期收益：降低游戏与实时通信的网络抖动。
- 原理：低延迟模式让 WLAN 固件停止省电轮询（power save），保持收发通路常开。
- 已知限制：**驱动支持程度不确定**：部分 ROM 的 `cmd wifi` 没有这个子命令（输出 `Unknown command`，界面原样显示），部分 ROM 有子命令但驱动忽略设置（命令返回成功但行为不变，本工具无法探测驱动是否真的接受）。耗电略增。属于「不确定接口」，默认不勾选。

#### 26. `net_tcp_rwnd` 提高 TCP 初始接收窗口

- 命令：`setprop net.tcp.default_init_rwnd 60 2>/dev/null; echo "rwnd=$(getprop net.tcp.default_init_rwnd 2>/dev/null)"; echo HWM_NET_TCP_RWND_DONE`
- 验证：`getprop net.tcp.default_init_rwnd 2>/dev/null`
- 恢复：`setprop net.tcp.default_init_rwnd 10 2>/dev/null`
- 权限：Root
- 风险：caution，默认不勾选
- 适用 ROM：Android 全系（属性由 netd / 内核网络栈读取）
- 预期收益：高延迟链路上更快达到可用带宽。
- 原理：`net.tcp.default_init_rwnd` 是系统读取的 TCP 初始接收窗口值（以 MSS 倍数计），系统默认 10；调大后 BDP 较大的链路（高延迟、高带宽）不用等窗口增长就能填满带宽。
- 已知限制：**该属性默认由内核内置值提供，未显式设置时 `getprop` 读到空串**，所以首次验证会显示「无效」，执行一次后再验证即显示 `60`——这是验证语义，不是命令失效。`net.*` 属性只有 root / init 可写，Shizuku 会失败。弱网或短连接（大量小请求）场景收益有限甚至无收益，还可能挤占接收缓冲。重启后失效。

### 组 `sys` 系统级（深度）（4 条）

#### 27. `sys_disable_hwaps` 停用华为应用加速服务

- 命令：`pm disable-user --user 0 com.huawei.android.hwaps 2>/dev/null; am force-stop com.huawei.android.hwaps 2>/dev/null; echo "state=$(pm list packages -d 2>/dev/null | grep -m1 hwaps)"; echo HWM_SYS_DISABLE_HWAPS_DONE`
- 验证：`pm list packages 2>/dev/null | grep -m1 com.huawei.android.hwaps`
- 恢复：`pm enable com.huawei.android.hwaps 2>/dev/null`
- 权限：Shizuku
- 风险：risky，默认不勾选
- 适用 ROM：存在 `com.huawei.android.hwaps` 的 EMUI / HarmonyOS
- 预期收益：减少后台 I/O 与常驻内存。
- 原理：`com.huawei.android.hwaps` 是华为的应用加速 / 预加载服务，会根据使用习惯预测并提前加载应用。
- 已知限制：**行为随 ROM 版本不同，收益方向不确定**：禁用后少了预加载的后台 I/O，但也失去了预加载带来的冷启动优势，部分机型上应用启动反而变慢。这正是不默认勾选的原因。该包在不同 ROM 上的职责有差异（部分版本它同时承担图像加速相关功能），禁用后若发现相机 / 图库异常，用恢复命令还原。

#### 28. `sys_disable_push` 停用华为推送服务

- 命令：`pm disable-user --user 0 com.huawei.android.pushagent 2>/dev/null; am force-stop com.huawei.android.pushagent 2>/dev/null; echo "state=$(pm list packages -d 2>/dev/null | grep -m1 pushagent)"; echo HWM_SYS_DISABLE_PUSH_DONE`
- 验证：`pm list packages 2>/dev/null | grep -m1 com.huawei.android.pushagent`
- 恢复：`pm enable com.huawei.android.pushagent 2>/dev/null`（部分机型需重启或重新登录华为账号才能恢复推送）
- 权限：Shizuku
- 风险：caution，默认不勾选
- 适用 ROM：全系华为 / 荣耀机型（无 GMS 环境下的推送通道）
- 预期收益：减少一条常驻长连接的唤醒、内存与耗电。
- 原理：`com.huawei.android.pushagent` 维持与华为推送服务器的长连接并分发消息。
- 已知限制：**所有依赖华为推送的应用都会收不到离线通知**（微信 / QQ 等有自建通道的应用仍可收到），这是明确的副作用，不是「可能影响」。恢复命令重新启用后，部分应用需要重新注册推送（重启设备或重新登录账号）。

#### 29. `sys_io_scheduler` 块设备调度器切 none

- 命令：
  `[ -f /data/local/tmp/hw_io_backup ] || for q in /sys/block/sd*/queue/scheduler /sys/block/mmcblk*/queue/scheduler; do [ -w "$q" ] || continue; v=$(sed -n 's/.*\[\(.*\)\].*/\1/p' "$q"); echo "$q $v" >> /data/local/tmp/hw_io_backup; done; for q in /sys/block/sd*/queue/scheduler /sys/block/mmcblk*/queue/scheduler; do echo none > "$q" 2>/dev/null; done; echo "sched=$(cat /sys/block/sda/queue/scheduler 2>/dev/null)$(cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null)"; echo HWM_SYS_IO_SCHEDULER_DONE`
- 验证：`ls -d /sys/block/sd* /sys/block/mmcblk* 2>/dev/null`（非空即视为块设备节点存在）
- 恢复：`while read -r q v; do [ -n "$v" ] && [ -w "$q" ] && echo "$v" > "$q" 2>/dev/null; done < /data/local/tmp/hw_io_backup; echo "sched=$(cat /sys/block/sda/queue/scheduler 2>/dev/null)$(cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null)"`
- 权限：Root
- 风险：caution，默认不勾选
- 适用 ROM：内核暴露 `/sys/block/*/queue/scheduler` 的机型（绝大多数 Android）
- 预期收益：降低随机 I/O 延迟。
- 原理：`scheduler` 决定块层的 I/O 调度算法。UFS / NVMe 多队列设备自带硬件队列，`none`（无调度）省掉调度层的重排队开销。备份时用 `sed` 从 `mq-deadline none [cfq]` 这类输出里提取方括号内的当前值，恢复时逐条写回。
- 已知限制：**UFS 机型通常默认就是 `none`，本条多为空操作**；eMMC 老机型从 `cfq`/`bfq` 切到 `none` 在随机写入混合场景下可能反而更慢（失去了读写合并与优先级）。部分内核的调度器列表里没有 `none`，写入被内核忽略，状态回读行会显示原值不变。改块设备调度器对已挂载分区有潜在风险，虽然实践中安全，仍归入 caution。

#### 30. `sys_readahead` 提高块设备预读

- 命令：
  `[ -f /data/local/tmp/hw_ra_backup ] || for q in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do [ -w "$q" ] && echo "$q $(cat $q)" >> /data/local/tmp/hw_ra_backup; done; for q in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do echo 2048 > "$q" 2>/dev/null; done; echo "ra=$(cat /sys/block/sda/queue/read_ahead_kb 2>/dev/null)$(cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null)"; echo HWM_SYS_READAHEAD_DONE`
- 验证：`ls /sys/block/sda/queue/read_ahead_kb /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null`
- 恢复：`while read -r p v; do [ -n "$v" ] && [ -w "$p" ] && echo "$v" > "$p" 2>/dev/null; done < /data/local/tmp/hw_ra_backup; echo "ra=$(cat /sys/block/sda/queue/read_ahead_kb 2>/dev/null)$(cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null)"`
- 权限：Root
- 风险：caution，默认不勾选
- 适用 ROM：内核暴露 `read_ahead_kb` 的机型
- 预期收益：大文件顺序读（拷贝、解压、游戏资源加载）更快。
- 原理：`read_ahead_kb` 是块层一次预读的数据量，调大后顺序读的 I/O 请求数减少、吞吐上升。
- 已知限制：**以随机读为主的负载收益有限甚至为负**（过多预读挤占页缓存、增加无用 I/O），建议只在明确做大文件顺序读时使用。原始值因设备而异（常见 128 ~ 512），恢复命令按备份还原，不要凭记忆手写。

## 3. 不确定接口清单（需要在报告中如实标注）

以下条目的接口名称 / 行为随 ROM 版本变化，本实现全部提供了验证命令来判定本机是否存在，并在界面上显示「有效 / 无效」，**不把「命令返回 0」当作「已经生效」**：

| 方法 | 不确定点 | 判定方式 |
| --- | --- | --- |
| `power_mode_on` | `hw_power_mode` / `hw_performance_mode` 键名在不同 EMUI / HarmonyOS 版本上不统一；HarmonyOS 高版本可能改为应用级性能模式 | `settings list global` 查键是否存在 + 命令内回读两个键的实际值 |
| `net_wifi_low_latency` | `cmd wifi force-low-latency-mode` 子命令与驱动支持程度均不确定 | `cmd wifi` 帮助文本里有该子命令才判有效；执行输出原样展示 |
| `net_tcp_rwnd` | `net.tcp.default_init_rwnd` 默认值由内核内置提供，属性本身读不到 | 首次验证为「无效」，执行后回读属性值 |
| `gpu_skiagl` | Android 12+ 默认已是 SkiaGL，本条在这些机型上是无操作 | 回读 `debug.hwui.renderer`；文档明确说明仅在 EMUI 10/11 有意义 |
| `gpu_sf_latch` | `debug.sf.latch_unsignaled` 是 SurfaceFlinger 调试属性，Google 未承诺长期保留 | `dumpsys SurfaceFlinger` 可读 + 回读属性值 |
| `mem_lmk_tune` | Android 11+ 改用用户态 lmkd，`/sys/module/lowmemorykiller/parameters/minfree` 通常不存在 | `ls` 该节点，不存在即「无效」 |
| `power_thermal_off` | 华为自研温控服务（用户态）可能绕过内核 thermal zone，写入成功但行为不变 | `cat thermal_zone0/mode` 可读 + 回读 `mode0=`；文档标注风险与不确定 |
| `power_gov_performance` | 部分内核只允许 `schedutil`，写入 `performance` 被忽略 | 回读 `gov=` 显示实际生效值 |
| `sys_io_scheduler` | 部分内核调度器列表无 `none`；UFS 机型默认已是 `none` | `ls` 块设备节点 + 回读 `sched=` |
| `sys_readahead` | 原始值因设备而异；随机读场景可能为负收益 | `ls` 该节点 + 回读 `ra=` + 备份文件 |
| `bg_disable_stats` | `com.huawei.bd` 的职责在不同 ROM 上有差异 | `pm list packages` 先确认包存在 |
| `bg_autostart_page` | 两个 Activity 名称是经验值，高版本 HarmonyOS 入口可能迁移 | `pm list packages` 确认手机管家存在 + `launched=` 回读退出码 |
| `bg_kill_all` | 部分 OEM 裁剪 `am help` 文本 | `am help \| grep kill-all`；失败时以命令回的 `FreeRAM=` 行为准 |
| `mem_zram_off` | swap 设备名不一定是 `zram0`，恢复可能不完整（需重启） | `grep zram /proc/swaps` + 回读 `zram=` 计数 |

## 4. 明确排除的接口与原因

这些是「看起来像性能优化、实际无效或代价远大于收益」的接口，本方法库**故意不实现**：

- `settings put global force_gpu_rendering 1`：开发者选项里早已废弃的「强制 GPU 渲染」，现代 Android 的 HWUI 全走 GPU，写入不产生任何渲染行为变化——属于典型的「普通无效 ADB 命令」，故不收录。
- `setenforce 0` / `ro.build.selinux=0`：关闭 SELinux 不带来性能收益，却直接移除系统安全边界，且会让大量依赖 SELinux 上下文的厂商服务异常。不收录。
- 禁用关键包：`com.huawei.hwid`（华为账号）、`com.android.systemui`、华为桌面、输入法、电话 / 短信相关组件，以及**除推送通道本身之外**的任何通讯 / 推送组件，一律不在任何方法中出现。推送通道单独作为 `sys_disable_push`（风险 caution、默认不勾选、文档与界面都写明「所有华为推送通知都会丢失」）出现。
- `service call SurfaceFlinger <code> i32 1`（禁用硬件叠加层）：transaction code 因 Android 版本而异，写错 code 会调用到别的接口，风险不可控，且现代设备上收益为负。不收录。
- 修改 `/proc/sys/vm/swappiness`：Android 的 ZRAM 策略下调整 swappiness 的收益方向不明确（调低减少压缩开销但加剧内存压力），且默认值 100 是 Google 针对 ZRAM 的调优结果，改动多为负收益。不收录。
- `dumpsys deviceidle disable`：关闭 Doze 会显著增加待机耗电，且与「性能」无直接关系。不收录。
- 华为「GPU Turbo」「内存扩展」：没有公开的 shell 接口，无法真实控制；不做假接口。
- 华为自启/后台管理：没有公开的 shell 写入接口（见 `bg_autostart_page`），因此只做界面跳转，不伪造 `settings` 键。

## 5. 权限与回滚汇总

- 全部 30 条都需要提权，**没有一条能在普通应用 shell（untrusted_app）下生效**，这是刻意设计：写入 `Settings`、`am` / `cmd` / `pm`、`sysfs` / `procfs` 在这台设备上都要求 shell 或 root 身份。
- 需要 Shizuku（Shizuku 或 Root 或 Dhizuku 任一可用即可）：写 `Settings`、`am` / `cmd` / `pm` 系列共 19 条。
- 需要 Root：`mem_drop_caches`、`mem_compact_memory`、`mem_lmk_tune`、`mem_zram_off`、`power_gov_performance`、`power_thermal_off`、`storage_fstrim`、`storage_clear_logs`、`net_tcp_rwnd`、`sys_io_scheduler`、`sys_readahead` 共 11 条。
- 两者都不满足时：验证层直接把对应条目标为「需权限」且不执行验证命令，执行时会收到 `permission denied` 原文并显示为失败，不会伪装成功。
- 8 条为**不可逆或一次性操作**，`rollbackCommand = null`，界面显示「不可逆」徽章并在详情里给出说明：
  `bg_kill_all`、`bg_autostart_page`、`mem_trim_caches`、`mem_drop_caches`、`mem_compact_memory`、`storage_bg_dexopt`、`storage_fstrim`、`storage_clear_logs`。
  其中只有 `storage_clear_logs` 会造成数据丢失（崩溃日志），其余要么由系统自然重建，要么本身就是维护动作。
- 默认勾选 8 条（「一键华为深度优化」实际执行的就是这 8 条）：`bg_kill_all`、`mem_trim_caches`、`gpu_anim_scale`、`power_mode_on`、`power_saver_off`、`storage_bg_dexopt`、`net_wifi_scan_off`、`net_mobile_always_on_off`。其余 22 条默认不勾选，其中风险为 risky 的 5 条（`mem_lmk_tune`、`mem_zram_off`、`power_gov_performance`、`power_thermal_off`、`sys_disable_hwaps`）在界面上带红色风险标。
- 二次确认规则：只要待执行集合里出现 `caution` 或 `risky` 的条目，界面就弹出确认对话框（默认的 8 条里含 `power_mode_on`，风险等级为 caution，因此一键按钮也会确认一次）；单条「执行」与「恢复」同样走这套确认。风险为 safe 的 9 条（`bg_kill_all`、`bg_autostart_page`、`mem_trim_caches`、`mem_compact_memory`、`gpu_anim_scale`、`power_saver_off`、`storage_bg_dexopt`、`net_wifi_scan_off`、`net_mobile_always_on_off`）也在同一套判定里，只要集合中含 caution/risky 就会确认。
- 其余 22 条都有可执行的恢复命令；`mem_lmk_tune`、`power_gov_performance`、`power_thermal_off`、`sys_io_scheduler`、`sys_readahead` 会在首次执行时先把原值备份到 `/data/local/tmp/hw_*_backup`，恢复命令按备份逐项还原。
