package dev.appharbor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.File

data class PermissionRecord(
    val name: String,
    val granted: Boolean,
    val dangerous: Boolean
)

data class ComponentRecord(
    val type: String,
    val name: String,
    val exported: Boolean,
    val enabled: Boolean
)

data class AppRecord(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val system: Boolean,
    val enabled: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val permissions: List<PermissionRecord>,
    val components: List<ComponentRecord>,
    val sourceApk: String?,
    val splitApks: List<String>,
    val icon: Drawable
)

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    @Suppress("DEPRECATION")
    private val inspectionFlags =
        PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES)

    fun installedApps(): List<AppRecord> {
        val apps = installedApplications()
        return apps.mapNotNull { info ->
            runCatching {
                val pkg = packageInfo(info.packageName, inspectionFlags)
                record(pkg, info)
            }.getOrNull()
        }
    }

    fun get(packageName: String): AppRecord? = runCatching {
        val info = packageInfo(packageName, inspectionFlags)
        val appInfo = info.applicationInfo ?: return@runCatching null
        record(info, appInfo)
    }.getOrNull()

    fun inspectArchive(file: File): AppRecord? = runCatching {
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(file.absolutePath, inspectionFlags) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath
        record(info, appInfo)
    }.getOrNull()

    private fun installedApplications(): List<ApplicationInfo> {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            pm.getInstalledApplications(0)
        }
    }

    private fun packageInfo(packageName: String, flags: Int): PackageInfo {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }
    }

    private fun record(info: PackageInfo, appInfo: ApplicationInfo): AppRecord {
        @Suppress("DEPRECATION")
        val legacyVersionCode = info.versionCode.toLong()
        val requested = info.requestedPermissions.orEmpty().map { permission ->
            val declared = runCatching {
                @Suppress("DEPRECATION")
                pm.getPermissionInfo(permission, 0)
            }.getOrNull()
            val protection = declared?.protectionLevel ?: PermissionInfo.PROTECTION_NORMAL
            PermissionRecord(
                name = permission,
                granted = pm.checkPermission(permission, info.packageName) == PackageManager.PERMISSION_GRANTED,
                dangerous = (protection and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
            )
        }
        val components = buildList {
            info.activities.orEmpty().forEach { add(it.asRecord("Activity")) }
            info.services.orEmpty().forEach { add(it.asRecord("Service")) }
            info.receivers.orEmpty().forEach { add(it.asRecord("Receiver")) }
            info.providers.orEmpty().forEach { add(it.asRecord("Provider")) }
        }
        val installedState = runCatching { pm.getApplicationEnabledSetting(info.packageName) }.getOrDefault(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        )
        val enabled = when (installedState) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> appInfo.enabled
        }
        val label = runCatching { appInfo.loadLabel(pm).toString() }
            .getOrDefault(info.packageName)
        val icon = runCatching { appInfo.loadIcon(pm) }
            .getOrElse { context.getDrawable(android.R.drawable.sym_def_app_icon)!! }
        return AppRecord(
            packageName = info.packageName,
            label = label,
            versionName = info.versionName ?: "Unknown version",
            versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else legacyVersionCode,
            system = appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            enabled = enabled,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            permissions = requested,
            components = components,
            sourceApk = appInfo.sourceDir,
            splitApks = appInfo.splitSourceDirs?.toList().orEmpty(),
            icon = icon
        )
    }

    private fun ComponentInfo.asRecord(kind: String) = ComponentRecord(
        type = kind,
        name = name,
        exported = exported,
        enabled = enabled
    )
}
