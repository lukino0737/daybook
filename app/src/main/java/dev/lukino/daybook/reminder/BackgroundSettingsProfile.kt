package dev.lukino.daybook.reminder

import java.util.Locale

/** Optional OEM shortcuts; always fall back to Android's application details settings. */
internal data class BackgroundSettingsProfile(val instruction: String, val components: List<Pair<String, String>> = emptyList()) {
    companion object {
        fun forDevice(manufacturer: String, brand: String): BackgroundSettingsProfile {
            val names = setOf(manufacturer.lowercase(Locale.ROOT), brand.lowercase(Locale.ROOT))
            return when {
                names.any { it in setOf("xiaomi", "redmi", "poco") } -> BackgroundSettingsProfile(
                    "在系统设置中搜索“自启动”或“后台自启动”，找到 Daybook 并开启。",
                    listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                "honor" in names -> BackgroundSettingsProfile(
                    "在系统设置中搜索“应用启动管理”，找到 Daybook；如需手动管理，请允许自启动、关联启动和后台活动。",
                    listOf("com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
                "huawei" in names -> BackgroundSettingsProfile(
                    "在系统设置中搜索“应用启动管理”，找到 Daybook；如需手动管理，请允许自启动、关联启动和后台活动。",
                    listOf("com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
                names.any { it in setOf("oppo", "oneplus", "realme") } -> BackgroundSettingsProfile(
                    "在 Daybook 的耗电管理或应用电池管理中，允许自启动和后台活动；也可在系统设置中搜索“自启动”。",
                    listOf("com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
                        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity"))
                names.any { it in setOf("vivo", "iqoo") } -> BackgroundSettingsProfile(
                    "在系统设置或 i 管家的权限管理中允许 Daybook 自启动；若仍漏提醒，再检查后台耗电管理。",
                    listOf("com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
                "samsung" in names -> BackgroundSettingsProfile(
                    "在系统设置中搜索“后台使用限制”，确认 Daybook 未被列入休眠或深度休眠应用；需要时加入从不休眠应用。")
                else -> BackgroundSettingsProfile(
                    "在 Daybook 的系统应用设置中查看电池或后台运行选项；若手机提供自启动或启动管理，请允许 Daybook。")
            }
        }
    }
}
