package dev.appharbor

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.text.DateFormat
import java.io.File
import java.io.FileInputStream
import java.util.Date
import java.util.concurrent.Executors

class DetailsActivity : Activity() {
    private val repository by lazy { AppRepository(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private var app: AppRecord? = null
    private var archivePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.applyAppSystemBars()
        archivePath = intent.getStringExtra(EXTRA_ARCHIVE_PATH)
        app = if (archivePath != null) repository.inspectArchive(File(archivePath!!))
        else intent.getStringExtra(EXTRA_PACKAGE)?.let(repository::get)
        if (app == null) {
            Toast.makeText(this, "Package details are no longer available.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(buildPage(app!!))
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildPage(record: AppRecord): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(HarborPalette.background)
        }
        root.applySystemBarInsets(dp(17f), dp(12f), dp(17f), dp(12f))
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3f), 0, dp(13f))
        }
        header.addView(actionButton(this, "‹  Apps") { finish() })
        header.addView(label(this, if (archivePath == null) "Package details" else "APK inspection",
            16f, HarborPalette.text, true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(actionButton(this, "⋮") { showActions(record) })
        root.addView(header)
        val scroll = ScrollView(this).apply {
            hideScrollbars()
            isFillViewport = true
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2f), 0, dp(18f))
        }
        body.addView(identityCard(record))
        if (archivePath != null) {
            section(body, "INSTALL FROM FILE") {
                addView(label(this@DetailsActivity,
                    "The APK will be passed to Android's package installer. Android may ask you to confirm the install.",
                    12f, HarborPalette.muted))
                addView(actionButton(this@DetailsActivity, "Install APK", active = true) {
                    installArchive()
                }.apply { setPadding(dp(19f), dp(12f), dp(19f), dp(12f)) }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12f) })
            }
        } else {
            section(body, "PACKAGE ACTIONS") {
                val actions = LinearLayout(this@DetailsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                actions.addView(actionButton(this@DetailsActivity, "Open") {
                    openApplication(record.packageName)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(5f)
                })
                actions.addView(actionButton(this@DetailsActivity, "Android info") {
                    openSystemDetails(record.packageName)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(5f)
                })
                addView(actions)
                val rootActions = LinearLayout(this@DetailsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(8f), 0, 0)
                }
                rootActions.addView(actionButton(this@DetailsActivity, "Force stop · root") {
                    rootAction(record, "Force stop", "am force-stop --user 0")
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(5f)
                })
                rootActions.addView(actionButton(this@DetailsActivity,
                    if (record.enabled) "Disable · root" else "Enable · root") {
                    rootAction(record, if (record.enabled) "Disable" else "Enable",
                        if (record.enabled) "pm disable-user --user 0" else "pm enable")
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(5f)
                })
                addView(rootActions)
            }
        }
        section(body, "OVERVIEW") {
            infoRow("Package", record.packageName)
            infoRow("Version", record.versionName + " (" + record.versionCode + ")")
            infoRow("Category", if (record.system) "System app" else "User app")
            infoRow("State", if (record.enabled) "Enabled" else "Disabled")
            infoRow("First installed", dateText(record.firstInstallTime))
            infoRow("Last updated", dateText(record.lastUpdateTime))
            infoRow("Base APK", record.sourceApk ?: "Not available")
            infoRow("Split APKs", record.splitApks.size.toString())
        }
        section(body, "REQUESTED PERMISSIONS · " + record.permissions.size) {
            if (record.permissions.isEmpty()) addView(label(this@DetailsActivity, "No permissions declared.", 12f, HarborPalette.muted))
            record.permissions.forEach { permission ->
                val color = when {
                    permission.dangerous && permission.granted -> HarborPalette.warning
                    permission.granted -> HarborPalette.accent
                    else -> HarborPalette.muted
                }
                infoRow(
                    permission.name.substringAfterLast('.'),
                    (if (permission.granted) "Granted" else "Not granted") +
                        (if (permission.dangerous) " · dangerous" else ""),
                    color
                )
            }
        }
        section(body, "COMPONENTS · " + record.components.size) {
            if (record.components.isEmpty()) addView(label(this@DetailsActivity, "No components returned by Android.", 12f, HarborPalette.muted))
            record.components.forEach { component ->
                val state = if (component.enabled) "Enabled" else "Disabled"
                infoRow(component.type + " · " + component.name.substringAfterLast('.'),
                    state + (if (component.exported) " · exported" else " · private"))
            }
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return root
    }

    private fun identityCard(record: AppRecord): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(HarborPalette.surface, dp(12f).toFloat())
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
        }
        card.addView(ImageView(this).apply {
            setImageDrawable(record.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13f), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        text.addView(label(this, record.label, 18f, HarborPalette.text, true))
        text.addView(label(this, record.packageName, 13f, HarborPalette.muted).apply {
            setPadding(0, dp(5f), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        text.addView(label(this, (if (record.system) "SYSTEM" else "USER") +
            "  ·  " + (if (record.enabled) "ENABLED" else "DISABLED"), 10f, HarborPalette.accent, true).apply {
            setPadding(0, dp(9f), 0, 0)
        })
        card.addView(text)
        return card
    }

    private fun section(parent: LinearLayout, title: String, fill: LinearLayout.() -> Unit) {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1f), dp(8f), dp(1f), 0)
            addView(label(this@DetailsActivity, title, 13f, HarborPalette.accent, true), 0)
            fill()
        }
        parent.addView(section, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12f) })
    }

    private fun LinearLayout.infoRow(title: String, value: String, color: Int = HarborPalette.muted) {
        val row = LinearLayout(this@DetailsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(10f), 0, dp(10f))
        }
        row.addView(label(this@DetailsActivity, title, 13f, HarborPalette.text, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f))
        row.addView(label(this@DetailsActivity, value, 13f, color).apply {
            setTextIsSelectable(true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f))
        addView(row)
        addView(View(this@DetailsActivity).apply { setBackgroundColor(HarborPalette.border) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)))
    }

    private fun showActions(record: AppRecord) {
        val items = if (archivePath == null) arrayOf("Open application", "Android app settings", "Clear app data · root", "Uninstall for user · root")
        else arrayOf("Install this APK")
        AlertDialog.Builder(this)
            .setTitle(record.label)
            .setItems(items) { _, which ->
                if (archivePath != null) {
                    installArchive()
                } else when (which) {
                    0 -> openApplication(record.packageName)
                    1 -> openSystemDetails(record.packageName)
                    2 -> rootAction(record, "Clear app data", "pm clear")
                    3 -> rootAction(record, "Uninstall for user 0", "pm uninstall --user 0", closeAfter = true)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openApplication(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch == null) toast("This package has no launch activity.")
        else runCatching { startActivity(launch) }.onFailure { toast("Could not open this app.") }
    }

    private fun openSystemDetails(packageName: String) {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + packageName)))
        }.onFailure { toast("Android app settings are unavailable.") }
    }

    private fun rootAction(record: AppRecord, title: String, prefix: String, closeAfter: Boolean = false) {
        val safePackage = RootController.safePackages(listOf(record.packageName)).firstOrNull()
        if (safePackage == null) {
            toast("Invalid package name.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(title + " · root")
            .setMessage("This operation requires root access and can affect the app or its data. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                worker.execute {
                    val result = RootController.run(prefix + " " + safePackage)
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle(if (result.success) "Operation finished" else "Operation failed")
                            .setMessage(result.output.take(3000))
                            .setPositiveButton("OK") { _, _ ->
                                if (closeAfter && result.success) finish()
                            }
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun installArchive() {
        val file = archivePath?.let(::File)
        if (file == null || !file.isFile) {
            toast("The selected APK file is no longer available.")
            return
        }
        worker.execute {
            val outcome = runCatching {
                val installer = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setSize(file.length())
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        FileInputStream(file).use { input -> input.copyTo(output) }
                        session.fsync(output)
                    }
                    val callback = Intent(this, InstallStatusReceiver::class.java)
                        .setAction(InstallStatusReceiver.ACTION_INSTALL_STATUS)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(this, sessionId, callback, flags)
                    session.commit(pending.intentSender)
                }
                "Install request sent to Android."
            }.getOrElse { it.message ?: "Unable to start APK installation." }
            runOnUiThread {
                Toast.makeText(this, outcome, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dateText(time: Long): String =
        if (time <= 0L) "Unknown" else DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT
        ).format(Date(time))

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_PACKAGE = "dev.appharbor.extra.PACKAGE"
        const val EXTRA_ARCHIVE_PATH = "dev.appharbor.extra.ARCHIVE_PATH"
    }
}
