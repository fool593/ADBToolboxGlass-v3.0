# -*- coding: utf-8 -*-
import sys
sys.stdout.reconfigure(encoding='utf-8')

p = r'app\src\androidMain\kotlin\com\example\adbtoolbox\common\RootToolManager.kt'
t = open(p, encoding='utf-8').read()

# 在 executeRootMethod 的 when 里添加新方法
old = '''                "mtk_generic_old" -> {
                    RootResult(false, "MTK通用老漏洞已在2020年3月安全更新中修复，当前系统大概率不受影响。\\n\\n如果您的设备是2020年前的老款MTK机型且未更新安全补丁，可以尝试从XDA下载对应exploit。", methodId)
                }'''

new = '''                "redmi_note11tpro_lkb_unlock" -> {
                    // 红米 Note 11T Pro LKB 单刷解BL
                    val lkbExists = exec("ls /data/local/tmp/lkb.img 2>/dev/null || echo ''")
                    if (lkbExists.isBlank()) {
                        RootResult(false, "红米Note11T Pro LKB单刷解BL需要电脑配合操作：\\n\\n1. 下载对应机型专属LKB单刷文件（网上搜索\"红米Note11T Pro LKB单刷\"）\\n2. 手机进入fastboot模式（关机后按住音量下+电源）\\n3. 电脑打开MiFlash工具，只勾选LKB项\\n4. 刷入修改版LKB镜像\\n5. 用配套工具完成最终解锁（会清除全部数据，请先备份）\\n6. 解BL后刷入KSU/Magisk获取root\\n\\n注意：此操作会清除全部数据，且有变砖风险，请谨慎操作。", methodId)
                    } else {
                        RootResult(false, "检测到 lkb.img 文件，但LKB单刷需要在fastboot模式下用电脑MiFlash工具刷入，无法在手机端直接执行。请按上述步骤用电脑操作。", methodId)
                    }
                }
                "mtk_generic_old" -> {
                    RootResult(false, "MTK通用老漏洞已在2020年3月安全更新中修复，当前系统大概率不受影响。\\n\\n如果您的设备是2020年前的老款MTK机型且未更新安全补丁，可以尝试从XDA下载对应exploit。", methodId)
                }'''

if old in t:
    t = t.replace(old, new, 1)
    print('Added: executeRootMethod handler for redmi_note11tpro_lkb_unlock')
else:
    print('ERROR: old string not found')

open(p, 'w', encoding='utf-8', newline='\n').write(t)
